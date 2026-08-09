# تشغيل RED محليًا — النسخة التجريبية الأولى

هذه نسخة **تجريبية محلية وليست إصدار إنتاج**. بوابة البناء تتحقق من backend وAndroid ولوحة الإدارة وSFU وAsterisk، لكن WebRTC على هاتفين وDINSTAR الفيزيائي ما زالا يحتاجان اختبار أجهزة.

## المتطلبات

- جهاز بذاكرة 16GB مفضلة (8GB حد أدنى، وقد يكون بناء Android بطيئًا).
- مساحة فارغة 25GB تقريبًا لأول تنزيل للصور وAndroid SDK.
- Docker Desktop على Windows، أو Docker Engine + Compose v2 على Linux.
- تخصيص 6GiB على الأقل لـ Docker (8GiB موصى بها للبناء المتوازي وAndroid).
- OpenSSL مطلوب على Linux/macOS؛ على Windows يبحث السكربت في Git for Windows ثم يستخدم حاوية Alpine مؤقتة تلقائيًا إن لم يجده.
- عنوان IPv4 ثابت لجهاز الخادم في الشبكة المحلية، مثل `192.168.1.50`.
- لا يلزم نطاق ولا TLS للتجربة داخل شبكة موثوقة فقط.

## Windows PowerShell

افتح PowerShell داخل مجلد المشروع:

```powershell
cd RED_Ultimate_V1\RED_Ultimate
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\local-first-run.ps1 -ServerIp 192.168.1.50 -BuildAndroid
```

استبدل IP بعنوان جهازك. الخيار `-BuildAndroid` يبني APK مضبوطًا على هذا العنوان. يمكن حذفه لتشغيل الخادم فقط بسرعة أكبر.

## Linux / macOS

```bash
cd RED_Ultimate_V1/RED_Ultimate
chmod +x scripts/local-first-run.sh
BUILD_ANDROID=1 ./scripts/local-first-run.sh 192.168.1.50
```

بدون بناء Android:

```bash
./scripts/local-first-run.sh 192.168.1.50
```

## ما يفعله السكربت

1. يتحقق من Docker وCompose وOpenSSL.
2. ينشئ `.env` بأسرار عشوائية قوية، ولا يستبدله إن كان موجودًا.
3. ينشئ سلطة هوية ECDSA P-256 محلية، ولا يستبدل مفاتيح موجودة.
4. يشغّل `docker compose config --quiet`.
5. يبني ويشغّل PostgreSQL وMongoDB وRedis وMinIO وbackend وSFU وTURN وAsterisk ولوحة الإدارة وNginx.
6. ينتظر `/health` و`/sfu-health` بدل ادعاء نجاح مبكر.
7. عند طلب Android، ينزّل ملفات libsignal الكبيرة إلى cache محلي resumable ويتحقق من SHA-256، ثم يستخدم target خفيفًا يبني Android فقط ويصدر مباشرة:
   - `local-artifacts/red-app-debug.apk`

يمكن تشغيل `scripts/prefetch-android-crypto.ps1` منفصلًا عند الشبكات البطيئة. محتوى `local-maven/` مستثنى من Git، ولا يُقبل أي artifact لا يطابق checksums المثبتة. الخادم يُبنى ويُختبر ضمن Compose/CI، لذلك لا يعاد بناؤه في مرحلة استخراج APK المحلية.

## الوصول

```text
لوحة الإدارة: http://IP-الخادم:8088/
حالة backend: http://IP-الخادم:8088/health
حالة SFU:     http://IP-الخادم:8088/sfu-health
```

بيانات المسؤول موجودة محليًا فقط في:

```text
RED_Ultimate/.env
```

لا ترسل هذا الملف ولا مجلد `secrets/` ولا ترفعهما إلى Git.

## تثبيت Android

بعد استخدام خيار بناء Android:

من جذر المستودع `RED_Ultimate_V1`:

```bash
adb install -r local-artifacts/red-app-debug.apk
```

أو انقل APK إلى الهاتف وثبته يدويًا. يجب أن يكون الهاتف والخادم على الشبكة المحلية نفسها، وأن يسمح جدار الحماية بمنفذ HTTP 80. APK التجريبي يسمح HTTP المحلي؛ إصدار الإنتاج سيمنعه ويستخدم TLS.

## سيناريو التجربة الأولى

1. افتح التطبيق وسجل اسم مستخدم وكلمة مرور واسم عرض، دون هاتف أو بريد أو OTP.
2. افتح لوحة الإدارة وسجل الدخول ببيانات `.env`.
3. وافق على الحساب والجهاز.
4. سجل الدخول في Android.
5. جرّب المنشورات والمجموعات والحالات النصية/بيانات الوسائط والمحادثة المشفرة المتاحة.
6. لا تعتبر أزرار الميزات المعلمة «قيد الربط» مكتملة.
7. لا تختبر DINSTAR قبل ضبط IP وبيانات الجهاز الحقيقي والتأكد من حدود المستخدم؛ المكالمة تستهلك رصيد SIM.

## الفحص وحل الأعطال

```bash
docker compose ps
docker compose logs --tail=200 backend
docker compose logs --tail=200 media-sfu
docker compose logs --tail=200 pstn-gateway
curl -f http://localhost/health
curl -f http://localhost/sfu-health
```

إيقاف الخدمات مع الاحتفاظ بالبيانات:

```bash
docker compose down
```

حذف البيانات بالكامل (خطر وغير قابل للتراجع):

```bash
docker compose down -v
```

## النسخ الاحتياطي الأولي

أنشئ مجلدًا خارج Git وخذ نسخة من الأسرار أولًا:

```bash
mkdir -p local-backup
cp RED_Ultimate/.env local-backup/red.env
cp -R RED_Ultimate/secrets local-backup/secrets
chmod -R go-rwx local-backup
```

ثم PostgreSQL:

```bash
docker compose -f RED_Ultimate/docker-compose.yml --env-file RED_Ultimate/.env exec -T db-postgres \
  pg_dump -U admin -d red_sovereign -Fc > local-backup/postgres.dump
```

ونسخة MongoDB:

```bash
docker compose -f RED_Ultimate/docker-compose.yml --env-file RED_Ultimate/.env exec -T db-mongo \
  mongodump --username red_user --password "$(grep '^MONGO_PASSWORD=' RED_Ultimate/.env | cut -d= -f2-)" \
  --authenticationDatabase admin --archive > local-backup/mongo.archive
```

MinIO يحتاج نسخ volume أو استخدام `mc mirror` في مرحلة النسخ الاحتياطي الكاملة. لا تُعد النسخة الاحتياطية مكتملة قبل تجربة الاستعادة على جهاز منفصل.

## حدود النسخة الحالية

- مناسبة لتجربة محلية أولى والحسابات والموافقة والبنية والوظائف المبنية حاليًا.
- غير مناسبة بعد للنشر العام أو تخزين بيانات حساسة حقيقية.
- مكالمات RED WebRTC الكاملة، Sender Keys للمجموعات، عرض الوسائط النهائي، incoming DINSTAR، واختبارات هاتفين/عتاد فعلي ما زالت بوابات إطلاق منفصلة.
