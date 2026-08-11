# تقرير تدقيق وتقوية البنية التحتية — يونس

**التاريخ:** 2026-08-11

**النطاق:** Docker / BuildKit / Nginx / TLS / Spring readiness / Flyway / مفاتيح الهوية / لوحة الإدارة / Media SFU / سكربتات التشغيل / CI

## الحكم التنفيذي

لم تكن عبارة «صفر أخطاء» قابلة للإثبات. المراجعة الفعلية وجدت أخطاء تشغيل وأمان كان بعضها سيظهر فقط بعد بدء الحاويات أو اعتماد أول جهاز. تم إصلاح الأخطاء المؤكدة وإضافة بوابات اختبار تمنع رجوعها، لكن لا يدّعي هذا التقرير نجاح تشغيل Docker أو Flyway الحي على هذه الآلة لأن Docker وJDK 21 غير متاحين فيها.

## تصحيح الادعاءات السابقة

| الادعاء السابق | النتيجة بعد فحص المصدر |
|---|---|
| أسماء Nginx ذات `_` غُيّرت | غير صحيح؛ كانت `backend_upstream` و`sfu_upstream` و`admin_upstream` ما تزال موجودة. |
| Nginx ذاتي الشفاء حتى عند تشغيله منفرداً | غير صحيح؛ volume الشهادة مركّب `:ro` بينما entrypoint حاول الكتابة داخله. |
| backend محدود بـ 512MB | غير دقيق؛ `-Xmx512m` حد heap للـ JVM وليس حداً لذاكرة الحاوية. لم يوجد `mem_limit`. |
| كل الحاويات Healthy وFlyway V26 نجح | غير قابل للتحقق من هذه الجلسة؛ لم يتوفر Docker. كما أن `/health` القديم كان يعيد HTTP 200 حتى عندما يقول الجسم `DOWN`. |
| BuildKit cache مضبوط بأقوى صورة | المبدأ صحيح، لكن الهدف كان `/root/.gradle` مع صورة Gradle التي يجب تشغيل بنائها كمستخدم `gradle`، ولم يكن `sharing=locked` موجوداً. |
| فحص SSL يتحقق من الصلاحيات والتطابق | الوصف قال ذلك، لكن السكربت القديم لم يفحص الصلاحيات فعلياً وكان يقبل ملفات غير فارغة قبل التحقق من صلاحيتها وتطابقها. |
| لا تعارض Flyway V26 | صحيح في الحالة الحالية فقط: يوجد ملف واحد لكل إصدار من V1 إلى V26. لم يُنفّذ ترحيل حي على PostgreSQL في هذه الجلسة. |

## الأخطاء المؤكدة التي تم إصلاحها

### 1. Host غير صالح يصل إلى Tomcat

بعض مسارات WebSocket وSFU لم تضبط `Host`، لذلك كان Nginx يستطيع تمرير اسم upstream المحتوي على `_`. تم:

- تغيير أسماء المجمعات إلى أسماء hostname-safe.
- توحيد WebSocket في `/ws/`.
- ضبط `Host` صالح لكل backend/SFU/admin.
- إضافة `X-Forwarded-Host` منفصل للحفاظ على المضيف الخارجي.

### 2. انتحال `X-Forwarded-For`

كان `SecurityEnhancer` يثق بالترويسة دائماً، بينما يستخدم AuthController خيار ثقة منفصلاً. كما كان Nginx يضيف قيمة العميل غير الموثوقة عبر `$proxy_add_x_forwarded_for`. تم:

- جعل الثقة معطّلة افتراضياً في Spring.
- تفعيلها فقط داخل Compose.
- جعل Nginx **يستبدل** XFF بـ `$remote_addr` بدل إلحاق مدخل العميل.
- إضافة اختبارات تثبت أن تغيير XFF لا يتجاوز rate limit عندما لا يكون البروكسي موثوقاً.

### 3. Docker health يعطي نجاحاً كاذباً

كان `/health` يعيد HTTP 200 سواء كانت التبعيات UP أم DOWN، ولذلك ينجح `curl -f`. تم:

- إعادة HTTP 503 عند تعطل PostgreSQL أو MongoDB أو Redis أو MinIO.
- تنفيذ Mongo `ping` حقيقياً بدلاً من قراءة `MongoDatabase.name` الكسولة.
- إغلاق اتصال Redis بعد الفحص.
- منع عرض نصوص الاستثناءات الداخلية في endpoint العام.

### 4. إعداد Actuator تحت مفتاح YAML خاطئ

كان `actuator` تحت `spring`، وهو مسار إعداد غير صحيح. نُقل إلى `management` مع probes وسياسة إظهار التفاصيل.

### 5. مفاتيح سلطة الهوية غير مربوطة بخاصية Spring

كان Compose يمرر `RED_IDENTITY_PRIVATE_KEY_PATH` و`RED_IDENTITY_PUBLIC_KEY_PATH`، لكن `DeviceCertificateService` يقرأ `red.identity-authority.*` بلا ربط في `application.yml`. النتيجة المتوقعة: فشل إصدار شهادة الجهاز عند أول اعتماد. أُضيف الربط الصريح ومدة صلاحية الشهادة.

### 6. مفتاح 0600 لا يقرأه مستخدم Java غير الجذر على Linux

bind mount يحتفظ بملكية الملف على المضيف، بينما backend يعمل بمستخدم آخر. أضيفت `identity-init` معزولة بلا شبكة:

- تقرأ ملفات المضيف 0600 كمرحلة تهيئة فقط.
- تنقلها ذرياً إلى named volume.
- تجعل الخاص UID 10001 يملك المفتاح بوضع 0400.
- ينتظر backend نجاحها ويركب volume للقراءة فقط.

### 7. «إصلاح» TLS كان يقبل ملفات تالفة أو غير متطابقة

تم فصل الكاتب عن القارئ:

- `certs-init` وحدها تكتب.
- Nginx يركب المفتاح `:ro` ويفشل بسرعة عند غيابه.
- التحقق يشمل parse، expiry، سلامة المفتاح، تطابق public key، وSAN الخاص بعنوان LAN.
- التوليد ذري، وأي تغيير لـ `TLS_SAN_IP` يعيد الشهادة.
- أضيفت صورة أدوات صغيرة تحتوي OpenSSL مسبقاً؛ لا يوجد `apk add` أثناء startup.

### 8. شهادة HTTPS لا تطابق عنوان LAN

كانت SAN تحتوي 127.0.0.1 فقط، بينما التشغيل يعلن عنوان LAN. أضيف `TLS_SAN_IP` إلى `.env.example` والشهادة، ويستبدله first-run بعنوان الخادم المكتشف.

### 9. BuildKit/Gradle cache غير مضبوط للمستخدم الصحيح

تم:

- إعلان Dockerfile frontend 1.7.
- البناء كمستخدم `gradle` غير الجذر.
- cache على `/home/gradle/.gradle` مع UID/GID صحيح و`sharing=locked`.
- إزالة retry بـ `--refresh-dependencies` الذي يعيد عملاً مكلفاً حتى عند خطأ compile حقيقي.
- عدم إدخال cache في image layers.

### 10. Shell entrypoint للـ JVM وإشارة الإيقاف

استُبدل `sh -c "java ${JAVA_OPTS} ..."` بـ exec-form مباشر و`JAVA_TOOL_OPTIONS`. بذلك تصل SIGTERM إلى Java مباشرة ولا يوجد تفسير shell لقيمة الخيارات.

### 11. JAR wildcard قابل للالتباس

تم تعطيل plain JAR وتثبيت اسم boot artifact إلى `red-backend.jar`، ثم نسخه بالاسم الصريح في Dockerfiles.

### 12. وصول مباشر إلى MinIO يتجاوز طبقة التفويض

أزيل `/storage` المباشر من Nginx لأن التنزيل الصحيح يمر عبر `/api/media/**` و`MediaAccessService`. منافذ MinIO أصبحت loopback-only بدلاً من كل interfaces.

### 13. خادم التطوير لا يغطي عقد Android

كشف الاختبار الحي ثلاثة مسارات مفقودة:

- `/api/contacts/presence/detailed`
- `/api/admin/content/sticker-packs/{id}/stickers`
- `/api/admin/content/sticker-packs/{id}/install`

أضيفت قاعدة SQLite وسلوك install/uninstall وقوائم published/installed واختبارات دورة كاملة بصلاحية مستخدم عادي.

### 14. سياقات Docker كانت تنسخ `node_modules`

لم توجد `.dockerignore` داخل admin/SFU/PSTN context. كان `COPY . .` قادراً على نسخ dependencies من نظام المضيف فوق dependencies المبنية داخل Linux image. أضيفت ملفات ignore محلية لكل context.

### 15. Media SFU يعمل كـ root

صورة SFU الآن تشغّل Node كمستخدم `node`، وCompose يضيف `no-new-privileges` و`cap_drop: ALL` وinit ومعالجة توقف.

### 16. سكربتات التشغيل تعلن النجاح قبل الجاهزية

تم توحيد `run.sh` و`build-and-run.sh` و`red-cli up` على `local-first-run.sh` الذي:

- يولد أسراراً عشوائية بدلاً من نسخ placeholders.
- يدعم فعلياً `--server-ip` و`--build-android` (كان `build-fresh.sh` يمررهما لسكربت لا يفهمهما).
- يتحقق من IPv4 والمنفذ وCompose.
- يمنع تكرار CORS origins في كل تشغيل.
- ينتظر readiness بدلاً من مساواة `up -d` بالنجاح.

### 17. CI الفعلي كان مجرد Hello World

أزيل workflow الوهمي وأضيف quality gate حقيقي لأربعة محاور:

- backend tests على JDK 21.
- admin static/build/live integration.
- Compose/Nginx/TLS/production backend image.
- Media SFU install/syntax.

## نتائج الاختبارات المنفذة فعلياً

| الاختبار | النتيجة |
|---|---:|
| Infrastructure regression checks | 74/74 PASS |
| خادم لوحة الإدارة الحي | 25/25 PASS |
| ربط Android ↔ server ↔ dashboard | 36/36 PASS |
| تطابق Asterisk fleet | PASS لثلاث بوابات + رفض anonymous |
| Admin API/guards/roles/TypeScript | PASS |
| Vite production build | PASS، 5446 module |
| SQL static validator | PASS، 27 ملفاً، 70 جدولاً، 121 index |
| Kotlin static/integrity | 255 + 38 PASS |
| TLS positive + mismatched-key negative | PASS |
| TLS SAN rotation/reuse + identity UID/mode | PASS |
| quality gate المحلي | 21 PASS، 0 FAIL، 2 SKIP |
| Media SFU JavaScript syntax | PASS |

### اختبارات لم تُنفّذ محلياً ولماذا

1. **Docker Compose/Nginx image/backend image:** Docker غير مثبت في بيئة الجلسة.
2. **Backend Gradle/JUnit:** JDK 21 غير موجود، وتنزيل JDK تعطل بسبب TLS/network في البيئة.
3. **Flyway V1..V26 على PostgreSQL حقيقي:** يحتاج Docker/PostgreSQL؛ الفحص الحالي يثبت uniqueness/contiguity والبنية الساكنة فقط.
4. **mediasoup native worker install:** تنزيل prebuilt worker ثم libuv فشل بسبب TLS إلى GitHub/dist.libuv.org؛ `npm ci --ignore-scripts` و`node --check` نجحا. CI الجديد يعيد المحاولة على GitHub runner.
5. **تشغيل CI الجديد عن بُعد:** اتصال GitHub App الحالي رفض دفع تعديل `.github/workflows` لغياب صلاحية `workflows`. يلزم إعادة ربط GitHub بهذه الصلاحية ثم دفع الفرع.

## أخطاء متوقعة ما زالت تحتاج مراقبة

### أولوية عالية

1. **صلاحية pg_trgm:** V26 ينفذ `CREATE EXTENSION pg_trgm`. مستخدم PostgreSQL محدود في production قد لا يملك الصلاحية. يجب تثبيت الامتداد بواسطة DBA أو migration role موثوق قبل النشر.
2. **ترحيل قاعدة موجودة:** نجاح قاعدة فارغة لا يثبت نجاح V24/V26 على بيانات تاريخية، خصوصاً قيود RED ID والفهارس الفريدة. يلزم clone مجهول الهوية من production ونسخة احتياطية واختبار upgrade/rollback تشغيلي.
3. **صور Docker mutable:** postgres:16 وmongo:8 وminio/minio وغيرها ليست مثبتة بـ digest. يجب تثبيت digests بعد نجاح CI وتحديثها دورياً بأداة آلية؛ لم تُخترع digests بلا pull موثوق.
4. **ذاكرة الحاويات:** `-Xmx512m` لا يمنع OOM على مستوى المضيف ولا يشمل native/metaspace/ffmpeg. يلزم قياس RSS تحت load ثم وضع limits مناسبة، لا افتراض 512MB.
5. **Redis eviction:** `allkeys-lru` قد يطرد بيانات ليست cache فقط عند الضغط. يجب فصل rate/session/presence عن cache القابل للطرد أو اعتماد سياسة مبنية على تصنيف البيانات.

### أولوية متوسطة

6. **تعارض المنافذ:** 8088/8443، 4000، 3478، 5060، نطاقات UDP 10000-10100 و40000-40100 و45000-45050، وloopback 9000/9001 قد تكون مشغولة.
7. **NAT/firewall:** SFU وTURN وAsterisk لن تعمل خارج LAN بمجرد نجاح health؛ يجب فتح UDP وتحديد announced/public IP واختبار ICE من شبكة خارجية.
8. **ثقة الشهادة:** self-signed تصلح للتطوير ولا تجعل المتصفح/Android يثق تلقائياً. production يجب أن يستخدم CA موثوقة أو pinning مدروس وتدوير آمن.
9. **انحراف الوقت:** JWT/TURN/certificates تتأثر بساعة النظام. استخدام `Instant.now()` لا يصلح ساعة جهاز خاطئة؛ يلزم NTP ومراقبة clock offset.
10. **WebSocket idle/NAT:** timeout ساعة يمنع Nginx من القطع المبكر لكنه لا يمنع NAT/mobile carrier من إسقاط socket. يلزم heartbeat وإعادة اتصال backoff واختبار شبكة هاتف حقيقية.
11. **نمو rate limiter داخل الذاكرة:** كثرة عناوين العملاء تخلق entries كثيرة ولا تتشارك بين replicas. الإنتاج متعدد النسخ يحتاج limiter مركزياً في Redis مع cleanup.
12. **اعتماد TLS/SFU على الشبكة أثناء build:** أزيل التنزيل أثناء startup، لكن أول build ما زال يحتاج registries وMaven/npm. يلزم registry mirror موثوق وcache احتياطي واختبار disaster recovery.

## أوامر التحقق الموصى بها على جهاز Docker

```bash
cd RED_Ultimate_V1-main/RED_Ultimate
./scripts/local-first-run.sh --server-ip 192.168.1.50
./scripts/red-cli.sh health
./scripts/red-cli.sh test
./scripts/red-cli.sh ssl:verify

docker compose --env-file .env ps
docker compose --env-file .env logs --tail=200 backend nginx certs-init identity-init
curl -fsS http://127.0.0.1:8088/health
curl -kfsS https://127.0.0.1:8443/health
```

لا يُعتمد وصف «جاهز للإنتاج» قبل نجاح CI الجديد، تشغيل Compose كاملاً، تنفيذ Flyway على نسخة بيانات واقعية، واختبار اتصال Android وWebSocket وTURN من جهاز وشبكة منفصلين.
