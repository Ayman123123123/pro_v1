# دليل staging والنسخ الاحتياطي والتراجع

## حالة الدليل

هذا الدليل **خطة تشغيلية جاهزة** وليس أمراً بالنشر. لا يجوز تنفيذ نشر staging أو migration أو استعادة بيانات قبل اجتياز بوابات القبول وفتح change record مخصص. تم التحقق من وجود أدوات النسخ الاحتياطي داخل الحاويات المحلية الحالية: PostgreSQL `pg_dump 16.9`، MongoDB `mongodump 100.13.0`، Redis CLI `7.4.5`، وMinIO Client `mc`.

> يمنع استخدام هذا الدليل لتجاوز أو تعديل DINSTAR أو PSTN أو SMS. لا تتضمن الخطة أي خطوة لإعادة تشغيل أو إعادة تكوين تلك الخدمات.

## بوابات ما قبل staging

| البوابة | الشرط | الوضع الحالي |
|---|---|---|
| تجميع Android الأصلي | `:app:testDebugUnitTest` ناجح في شجرة العمل الأصلية | محجوب بتناقضات مصدر DINSTAR/PSTN/SMS المستبعدة |
| تحقق Android للنطاق المسموح | تجميع Kotlin واختبارات الوحدة في نسخة تحقق معزولة | ناجح: 221 اختباراً، بلا فشل |
| خادم الخلفية | الاختبارات الكاملة | ناجح في الجولة السابقة |
| لوحة الإدارة | `npm run check` | ناجح في الجولة السابقة |
| تكامل مصادق عليه | حسابا اختبار معتمدان وسيناريوهات موثقة | معلق |
| مراجعة الفروق | تصنيف الفروق الكبيرة وعدم دمج PSTN المؤجل | موثق |
| نسخة احتياطية مستعادة تجريبياً | تحقق سلامة النسخة وامتحان restore في بيئة معزولة | معلق قبل staging |

لا يبدأ staging ما دام أي شرط من الشروط الحاجبة غير مجتاز.

## تحضير آمن

يُنفذ المشغل الخطوات التالية من host منفصل عن الإنتاج، مع تعيين مجلد نسخة احتياطية مشفّر وذي مساحة كافية. لا تحفظ ملفات `.env` أو كلمات المرور أو tokens في Git أو في سجل shell.

```powershell
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$BackupRoot = "D:\red-backups\staging\$Stamp"
New-Item -ItemType Directory -Force -Path $BackupRoot, "$BackupRoot\postgres", "$BackupRoot\mongo", "$BackupRoot\redis", "$BackupRoot\minio", "$BackupRoot\metadata" | Out-Null
Set-Content "$BackupRoot\metadata\manifest.txt" "createdAt=$((Get-Date).ToUniversalTime().ToString('o'))"
docker ps --format '{{.Names}}|{{.Image}}|{{.Status}}' | Set-Content "$BackupRoot\metadata\containers.txt"
```

قبل كتابة backup، يوثق المشغل revision المصدر وصور الحاويات الفعالة:

```powershell
git rev-parse HEAD | Set-Content "$BackupRoot\metadata\git-head.txt"
git status --short | Set-Content "$BackupRoot\metadata\git-status.txt"
docker inspect --format '{{.Name}}|{{.Config.Image}}|{{.Image}}' red-backend red-db-sql red-db-nosql red-cache red-storage red-media-sfu red-turn | Set-Content "$BackupRoot\metadata\images.txt"
```

## إجراءات النسخ الاحتياطي

تستخدم الأوامر أدناه أسماء الحاويات المحلية التي تم التحقق منها. في staging الفعلي، يستبدل المشغل أسماء الحاويات فقط بعد مطابقتها مع manifest البيئة المستهدفة.

| الأصل | أسلوب النسخ | شرط السلامة |
|---|---|---|
| PostgreSQL | `pg_dump` بصيغة custom | تحقق `pg_restore --list` قبل migration |
| MongoDB | `mongodump --archive --gzip` | تحقق أرشيف dump وحجمه وMongo restore تجريبي |
| Redis | RDB snapshot | عدم استبدال cache حي إلا في بيئة restore المعزولة |
| MinIO | `mc mirror` إلى مخزن backup مشفر | مقارنة عدد العناصر والأحجام، والحفاظ على retention |
| إعدادات النشر | manifest فقط، دون أسرار | يحوي hashes وإصدارات صور الحاويات وGit SHA |

### PostgreSQL

يُشغّل داخل حاوية قاعدة البيانات حتى يستعمل بيانات اعتماد البيئة الداخلية فقط ولا يطبعها على host:

```powershell
docker exec red-db-sql sh -lc 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges --file=/tmp/red-sovereign.dump'
docker cp red-db-sql:/tmp/red-sovereign.dump "$BackupRoot\postgres\red-sovereign.dump"
docker exec red-db-sql rm -f /tmp/red-sovereign.dump
pg_restore --list "$BackupRoot\postgres\red-sovereign.dump" | Set-Content "$BackupRoot\postgres\restore-list.txt"
```

### MongoDB

يجب أن يعتمد command URI المعرف في بيئة الحاوية، من دون نسخه إلى host أو طباعته. إذا لم توفر الحاوية `MONGO_URI`، يوقف المشغل العملية ويستخدم secret manager المعتمد لتوليد URI مؤقت في جلسة مقيدة.

```powershell
docker exec red-db-nosql sh -lc 'test -n "$MONGO_URI" && mongodump --uri="$MONGO_URI" --db=red_sovereign --archive=/tmp/red-sovereign.archive.gz --gzip'
docker cp red-db-nosql:/tmp/red-sovereign.archive.gz "$BackupRoot\mongo\red-sovereign.archive.gz"
docker exec red-db-nosql rm -f /tmp/red-sovereign.archive.gz
```

### Redis

```powershell
docker exec red-cache sh -lc 'redis-cli --rdb /tmp/red-cache.rdb'
docker cp red-cache:/tmp/red-cache.rdb "$BackupRoot\redis\red-cache.rdb"
docker exec red-cache rm -f /tmp/red-cache.rdb
```

### MinIO

يُعدّ المشغل alias مؤقتاً داخل حاوية `red-storage` باستخدام credentials البيئة الداخلية فقط، ثم يعكس buckets التطبيق إلى وجهة backup معتمدة ومشفرة. لا تستخدم عملية mirror للوصول إلى PSTN أو أي وسيط خارجي غير مخزن الكائنات.

```bash
# مثال مفهومي: استبدل red-backup-target بوجهة النسخ المعتمدة في secret manager.
mc alias set red-local http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mirror --overwrite --preserve red-local/red-media red-backup-target/red-media
mc mirror --overwrite --preserve red-local/red-attachments red-backup-target/red-attachments
```

## تحقق النسخة الاحتياطية

بعد النسخ، يحسب المشغل hashes ويسجل أحجام الملفات، ثم يجري استعادة تجريبية إلى قواعد بيانات تحمل suffix مثل `_restore_check`. لا توجّه أي restore إلى `red_sovereign` الحية.

```powershell
Get-ChildItem "$BackupRoot" -Recurse -File | Get-FileHash -Algorithm SHA256 | Export-Csv "$BackupRoot\metadata\sha256.csv" -NoTypeInformation
Get-ChildItem "$BackupRoot" -Recurse -File | Select-Object FullName,Length,LastWriteTime | Export-Csv "$BackupRoot\metadata\inventory.csv" -NoTypeInformation
```

تعد النسخة صالحة فقط إذا نجح فهرس `pg_restore`، وتحقق `mongorestore --dryRun` أو restore إلى namespace معزول، ومقارنة object count في MinIO، وتوفر hashes للملفات الأساسية.

## نشر staging المقيد

بعد تحقق النسخة وتمرير كل البوابات، ينفذ staging بهذه الترتيب: تجميد التغييرات، حفظ manifest، نشر backend والصور المقفلة بالـ digest، smoke test مصادق عليه، ثم migration صغيرة قابلة للعكس إن وجدت. لا يوجد نشر عام ولا ترقية متجر تطبيقات ضمن هذه الجولة.

| نقطة قرار | يسمح بالمتابعة عندما | يوقف العملية عندما |
|---|---|---|
| قبل الصور | manifest وbackup verification مكتملان | hash مفقود أو صورة غير مقفلة |
| قبل migration | restore check ناجح وrollback SQL جاهز | لا توجد migration عكسية أو backup غير صالح |
| بعد backend | healthcheck وقراءة API المحمية سليمان | healthcheck غير صحي أو 5xx |
| بعد Android | APK من build موثق وخطة اختبار جهاز مكتملة | build أصلي غير ناجح أو أزرار غير مختبرة |

## خطة التراجع

عند إخفاق smoke test أو ارتفاع أخطاء الخدمة، يوقف التغيير ولا يحاول «إصلاحاً مباشراً» على البيانات الحية. يعود المشغل أولاً إلى digest الصورة السابق، ثم يتحقق من health. لا تستعاد قواعد البيانات إلا إذا تضمنت migration تغييراً فعلياً للبيانات أو schema، وبعد تأكيد قائد التغيير.

| نوع الإخفاق | إجراء التراجع | معيار النجاح |
|---|---|---|
| فشل backend بعد النشر | إعادة نشر digest السابق لـ `red-backend` | healthcheck صحي وقراءة API مع استجابة متوقعة |
| فشل SFU أو TURN | إعادة digest الخدمة السابق فقط | healthcheck ووسائط اختبار معتمدة |
| فشل migration قبل commit | تشغيل rollback migration الموثق | checksum/schema المتوقعان |
| فشل migration بعد commit | استعادة PostgreSQL/Mongo إلى بيئة isolated أولاً، ثم قرار استعادة مضبوط | restore check ناجح قبل أي تأثير على staging |
| خلل وسائط MinIO | إعادة mirror من snapshot المحقق | object count وchecksums مطابقان |
| فشل APK | عدم ترقية staging APK؛ إعادة استخدام artifact السابق | تثبيت artifact السابق واختبار launch بسيط |

## سجل التغيير المطلوب

يجب أن يسجل change record: وقت البدء والنهاية، Git SHA، image digests، مسار backup غير القابل للتعديل، نتيجة restore check، منفذ smoke test، قرار go/no-go، وصاحب قرار التراجع. لا يضم السجل أي secret أو token أو بيانات شخصية.
