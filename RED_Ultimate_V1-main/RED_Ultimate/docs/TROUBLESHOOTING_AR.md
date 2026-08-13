# 🔧 استكشاف الأخطاء وإصلاحها

## Docker Desktop توقف + اللوحة لا تجد السيرفر + صفحة المستخدمين تنهار

هذه ثلاث مشاكل منفصلة كانت تُخلط معًا:

| العَرَض | السبب الحقيقي | المصدر الصحيح |
|---|---|---|
| `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine` | محرّك Docker Desktop انهار | أعد تشغيل Docker Desktop وانتظر الحوت الأخضر |
| `/health` فاشل في المتصفح و«بقايا» جلسة قديمة | لا أحد يستمع على البروكسي | افتح **http://127.0.0.1:8088** لا 8080 |
| `function lower(bytea) does not exist` عند «المستخدمون» | Hibernate كان يربط `:search` كـ `bytea` ثم يستدعي `LOWER()` | أُصلح بـ JPA Specification + `ILIKE` — أعد بناء صورة `backend` |

### القاعدة الوحيدة بعد الإصلاح

- **Docker = المصدر الإنتاجي:** Kotlin + PostgreSQL + Mongo + Redis + Nginx على المنفذ **8088**.
- **Node + SQLite** (`npm run dev:server`) يحتل المنفذ **8080** على ويندوز وهو خادم تطوير فقط. لا تشغّله مع Docker.
- الباك اند داخل الحاوية يسمع على 8080 *داخل الشبكة الافتراضية فقط*. المتصفح لا يصل إليه مباشرة.

```powershell
# من مجلد RED_Ultimate بعد أن يصبح Docker Desktop أخضر:
powershell -ExecutionPolicy Bypass -File .\scripts\compose-recover.ps1 -RebuildBackend
```

بعد نجاح `/health` افتح اللوحة من `http://127.0.0.1:8088/` وامسح كاش المتصفح لتبويب الدخول القديم.

حساب المسؤول يُقرأ من `.env` (`RED_ADMIN_USERNAME` / `RED_ADMIN_PASSWORD`). لا تعتمد كلمة مرور مكتوبة في محادثة سابقة بعد مسح القاعدة.

## Mongo على `localhost:27017` بدل `db-mongo`

داخل Docker، `localhost` هو الحاوية نفسها. Mongo اسمه `db-mongo` على شبكة `red-net` فقط.

- الباك اند في Compose يجب أن يطبع عند الإقلاع: `INFRA binding: runtime=DOCKER mongodb.host=db-mongo`
- `/health` يعرض `bindings.mongodbHost`. إن ظهر `localhost` فأنت تشغّل JVM على ويندوز أو نسخت URI خاطئ.
- لا تشغّل `bootRun` بجانب Compose إلا عبر `docker-compose.host-debug.yml` و`SPRING_PROFILES_ACTIVE=host`.

## DINSTAR جاهز على `192.168.11.1` — لا تغيّر الـ IP إلى Wi-Fi

الجهاز بلا Wi-Fi. كرت Realtek ↔ `192.168.11.1` هو مسار الإدارة الصحيح. فكّ الكابل = انقطاع مهما كان العنوان.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\enable-dinstar-ready.ps1
```

في واجهة الجهاز (`enFrame.htm` → SIP Server) ضع **عنوان ويندوز على 192.168.11.x** والمنفذ 5060. Asterisk يتعرّف على البوابة بالعنوان (`type=identify`).

---

# 🔧 Docker Build DNS Issues

## ❌ المشكلة

```
UnknownHostException: repo.maven.apache.org
```

الـ Docker container **لا يستطيع الوصول لـ Maven Central** بسبب:
1. ❌ مشكلة DNS في الـ container (`8.8.8.8` غير قابل للوصول)
2. ❌ Firewall يمنع الـ outbound traffic
3. ❌ Slow / unstable internet connection

---

## ✅ الحل 1: استخدام DNS مختلفة

### الخطوة 1: افتح `gradle.properties` وأضف:
```properties
# Use HTTPS-only Google Maven mirror (no DNS issues)
systemProp.https.proxyHost=
systemProp.https.proxyPort=

# Add DNS overrides
org.gradle.jvmargs=-Xmx12g -Dfile.encoding=UTF-8
```

### الخطوة 2: شغّل pre-warm script (قبل Docker build):

**Windows (PowerShell):**
```powershell
cd C:\Users\hpc01\Pictures\pro\RED_Ultimate_V1-main\RED_Ultimate
.\scripts\prewarm-gradle-cache.ps1
```

**Linux/Mac:**
```bash
cd /path/to/RED_Ultimate
./gradlew dependencies --configuration runtimeClasspath
```

---

## ✅ الحل 2: تخطي Docker build للـ backend مؤقتاً

إذا استمرت المشكلة، يمكنك تشغيل الـ backend **مباشرة بدون Docker**:

### الخيار A: تشغيل محلي
```bash
# تأكد من Java 21 + Gradle 8.12
cd backend-server
./gradlew bootRun
# يعمل على http://localhost:8080
```

### الخيار B: تشغيل من IDE (IntelliJ IDEA)
1. افتح المشروع
2. اختر `RedSovereignApplication.kt`
3. اضغط `Shift+F10` (Run)

---

## ✅ الحل 3: استخدام Gradle cache محلي

الـ pre-warm script يقوم بـ:

1. **ينزل كل الـ dependencies** على جهازك أولاً
2. **يضعها في Docker volume** اسمه `gradle-cache`
3. **Docker build يستخدم الـ cache** بدون الحاجة للإنترنت

```powershell
# Pre-warm
.\scripts\prewarm-gradle-cache.ps1

# Then build
.\scripts\local-first-run.ps1 -ServerIp 192.168.137.19
```

---

## ✅ الحل 4: تخطي الـ backend مؤقتاً

إذا كنت تريد تشغيل باقي الـ services:

```bash
# شغّل فقط الـ services التي لا تحتاج Java
docker compose up -d db-postgres db-mongo cache-redis minio pstn-gateway media-sfu admin-panel
```

---

## 🐛 تشخيص المشكلة

### اختبار DNS:
```bash
# في الـ PowerShell
nslookup repo.maven.apache.org
nslookup maven-central.storage-download.googleapis.com
ping 8.8.8.8
```

### إذا فشل DNS:
```powershell
# شغّل Docker مع DNS يدوي
docker run --dns 1.1.1.1 --dns 8.8.4.4 --rm alpine nslookup maven-central.storage-download.googleapis.com
```

---

## 📊 جدول المصادر البديلة لـ Maven

| Mirror | URL | السرعة |
|--------|-----|--------|
| **Google Cloud Storage** | `https://maven-central.storage-download.googleapis.com/maven2` | ⚡ سريع |
| **Aliyun** (China-friendly) | `https://maven.aliyun.com/repository/public` | ⚡ سريع |
| **Maven Central** | `https://repo.maven.apache.org/maven2` | 🐌 بطيء |
| **JCenter** (deprecated) | `https://jcenter.bintray.com` | ❌ مغلق |

**الـ settings.gradle.kts** الآن يستخدم Google Cloud Storage mirror أولاً!

---

## 🔍 اختبارات سريعة

```bash
# اختبار الـ Docker DNS
docker run --rm alpine:3.20 sh -c "wget -qO- https://maven-central.storage-download.googleapis.com/maven2/ 2>&1 | head -5"

# اختبار gradle مباشرة
cd backend-server && ./gradlew --version
cd backend-server && ./gradlew dependencies | head -20
```

---

## 💡 نصيحة احترافية

إذا كنت في شبكة Yemen أو China، استخدم **Aliyun mirror**:

```kotlin
maven {
    url = uri("https://maven.aliyun.com/repository/public")
}
```

---

## 📞 إذا لم تنجح الحلول

افتح issue على GitHub مع:
- كامل الـ log
- نتيجة `docker run --rm alpine nslookup maven-central.storage-download.googleapis.com`
- محتوى `~/.gradle/init.d/`
