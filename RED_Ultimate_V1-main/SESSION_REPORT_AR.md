# تقرير الفحص والتشغيل والتحسين — جلسة 2026-08-08

> المشروع: RED Ultimate V1 (منظومة يونس السيادية للمراسلة والمكالمات)
> المستودع: `RED_Ultimate_V1-main` — الفرع: `arena/019fe3bc-pro-v1`
> الغرض: شغّل · افحص · اختبر · تحقق · فكّر · حسّن · أصلح · طوّر · بيّن · أكمل

---

## 1) ملخص تنفيذي

تم في هذه الجلسة:

| المحور | النتيجة |
|---|---|
| تشغيل فعلي | ✅ لوحة الإدارة تعمل وتُبنى وتُخدم عبر معاينة حية (Vite على المنفذ 8088) |
| فحص معماري | ✅ فحص 92+ ملف Kotlin للخادم، 22+ ملف واجهة، البروتوكول، SFU، Docker، Nginx |
| اختبار/تحقق آلي | ✅ 35 استدعاء واجهة ↔ 122 مسار خادم — العقد سليم 100% |
| فحص الاستيرادات | ✅ لا يوجد استيراد محلي مكسور في الخادم (بعد استثناء الموجَّدات المتداخلة والموّلّدة) |
| أخطاء مُصلحة | 3 مكوّنات واجهة مكسورة العقد + إصلاحات جودة (println → logger، عمليات Mongo مكتوبة) |
| تحسينات مضافة | وحدة بث مباشر موصولة بواجهة API + فاحص عقد آلي + إعدادات Vite قابلة للضبط |
| ما تعذّر تشغيله | الخادم (Spring/Kotlin) و Android و Docker — بسبب قيود بيئة الفحص (لا JDK/لا Docker/شبكات Maven وGoogle محجوبة) مع توثيق الأسباب والخطوات |

---

## 2) بيئة الفحص المتاحة

| الأداة | الحالة |
|---|---|
| Node.js v22.22.3 + npm | ✅ متاحة (registry.npmjs.org يعمل) |
| Python 3.11 + pip | ✅ متاحة |
| OpenSSL / git / gh | ✅ متاحة |
| Java/JDK | ❌ غير مثبتة، وكل مصادر تنزيل JDK (Adoptium API، GitHub releases assets، Microsoft) محجوبة من الشبكة |
| Docker | ❌ غير مثبت |
| Maven Central / Google Maven / Gradle Plugin Portal | ❌ محجوبة على مستوى الشبكة (SSL/اتصال مقطوع) |
| GitHub (الصفحات) | ✅ يعمل — لكن أصول الإصدارات (objects.githubusercontent.com) محجوبة |

**النتيجة العملية:** كل ما يعتمد على Node.js يمكن تشغيله فعليًا. كل ما يعتمد على JVM (الخادم الخلفي، Android، فاحصات Gradle) أو Docker (Compose كامل) **لا يمكن بناؤه أو تشغيله داخل هذه البيئة** — وهذا قيد بيئة وليس عيبًا في المشروع، وتم التحقق السكوني الكامل بدلًا من ذلك.

---

## 3) ما تم تشغيله فعليًا ✅

### 3.1 لوحة الإدارة (Admin Dashboard) — تعمل الآن
- `npm install` نجح، `npm run build` (tsc + vite) نجح بلا أخطاء.
- خادم التطوير يعمل على `0.0.0.0:8088` (معاينة حية).
- التحقق من الخدمة: `HTTP 200` على `/` مع واجهة عربية RTL صحيحة.
- التحسين: أصبح الـ proxy في `vite.config.js` قابلاً للضبط عبر `RED_API_TARGET` (الافتراضي `http://backend:8080` للعمل داخل Docker دون تغيير السلوك).

### 3.2 وسيط الوسائط (Media SFU) — فحص كامل
- `node --check server.js` ✅ (بناء الجمل سليم).
- مراجعة كاملة: مصادقة JWT (HS256 بنفس اشتقاق المفتاح في الخادم)، بروتوكول join/createTransport/produce/consume، `/health` و`/metrics`، تنظيف الجلسات عند الإغلاق — تصميم سليم ومتسق مع nginx.
- **لم يُشغَّل** لأن mediasoup يتطلب تنزيل العامل الجاهز أو بناءه من المصدر، وكلاهما يتطلب GitHub releases / wrapdb المحجوبة في هذه البيئة.

### 3.3 فاحص عقد API — أداة جديدة تعمل
`admin_dashboard/scripts/check-api-contract.mjs` يقرأ كل استدعاءات `apiFetch` في الواجهة ويطابقها مع خرائط Spring في الخادم.
- النتيجة الآن: **35 استدعاء ✅ / 0 مكسور**.
- أُضيف كأمر: `npm run check:api` و`npm run check`.

---

## 4) مصفوفة التحقق التفصيلية

| # | الفحص | الأداة/الطريقة | النتيجة |
|---|---|---|---|
| 1 | بناء لوحة الإدارة (TypeScript + Vite) | `npm run build` | ✅ بلا أخطاء |
| 2 | خدمة لوحة الإدارة | `curl http://localhost:8088` | ✅ 200 |
| 3 | عقد API (واجهة ↔ خادم) | `node scripts/check-api-contract.mjs` | ✅ 35/35 |
| 4 | استيرادات Kotlin للخادم (92 ملف) | سكربت تحليل استيرادات محلي | ✅ لا يوجد مكسور |
| 5 | بروتوكول Wire المشترك | فحص `red_protocol.proto` مقابل الاستخدام | ✅ `RedProtos`/`SignalCase` متطابقان |
| 6 | أمان JWT (الخادم) | مراجعة `JwtService`/`JwtAuthenticationFilter` | ✅ سر ≥32 حرفًا، تحقق من حالة الحساب والجهاز، HS256 |
| 7 | دوران Refresh Tokens | مراجعة `RefreshTokenService` | ✅ كشف إعادة الاستخدام + إبطال العائلة |
| 8 | تسجيل بدون هاتف/بريد/OTP | مراجعة `RegistrationService` + `SecurityConfig` | ✅ مطابق للمبدأ |
| 9 | ترحيلات قاعدة البيانات | فحص `db/migration` | ✅ 13 ملفات V1..V13 (users, devices, pstn, prekeys, audit, grants…) |
| 10 | صحة Docker Compose | `yaml.safe_load` | ✅ 10 خدمات، متغيرات `.env.example` كاملة، 7 فحوصات صحة |
| 11 | إعدادات Nginx | مراجعة المسارات والـ upstreams | ✅ `/api/ /ws/ /sfu /sfu-health` كلها صحيحة |
| 12 | رسائل مؤكدة (UUID v7 + ACK + مزامنة) | مراجعة `MessageService` + `RedMasterHandler` | ✅ تحقق UUID v7، تصفية تكرار، تفويض ACK، عضوية المجموعات، منع البلوك |
| 13 | مفتاح libsignal لا يغادر Android | فحص إعدادات Android | ✅ لا يوجد اتصال بخوادم Signal في `red-app` |
| 14 | ميزات وهمية (println) | فحص شامل | تم إصلاحها (انظر §6) |

---

## 5) الأخطاء المكتشفة والمُصلحة

### 5.1 مكوّنات واجهة بعقد مكسور (لم تكن موصولة فعليًا لكنها كانت قنابل)
- `src/pages/Approvals.jsx`: كان يستدعي `GET /api/admin/pending-users` و `POST /api/admin/approve/{id}?status=` — **مساران غير موجودين في الخادم**، وأعمدته `name/email/date` لا تطابق استجابة الخادم (`username/displayName/createdAt/devices`).
- `src/pages/UserApproval.tsx`: نفس مشكلة الأعمدة (عرض أسماء/بريد/تواريخ فارغة).
- `src/components/LiveMonitor.jsx`: كان يقرأ `{voip,pstn,msgs}` بينما الخادم يعيد `{active_users,total_messages,jvm_memory_percent,uptime_ms,…}`.

**الإصلاح:** إعادة كتابة الثلاثة على العقد الحقيقي:
- `GET /api/admin/users/pending` + `POST /api/admin/users/action {userId, action, reason}`.
- أعمدة مطابقة لاستجابة الخادم، واجهة عربية RTL، نافذة سبب الرفض، تأكيد قبل الحظر.
- LiveMonitor يعرض الآن المقاييس الحقيقية من `/api/admin/monitor/stats`.

### 5.2 ملف `public/index.html` قديم ومضلل
كان نسخة إنجليزية قديمة (RED MASTER) مكررة من `index.html` الجذر — حُذف لضمان عدم تلويث مخرجات البناء لاحقًا (المخرج الحالي سليم، لكن وجوده فخ مستقبلي).

### 5.3 إعداد Vite غير مرن
الـ proxy كان مقفلاً على `http://backend:8080` — أصبح `RED_API_TARGET` قابلاً للضبط مع الحفاظ على الافتراضي المتوافق مع Docker.

---

## 6) التحسينات والتطوير المضاف

### 6.1 الخادم الخلفي (Kotlin/Spring)
1. **`LiveStreamService`** — أُعيد بناؤه:
   - `println` → `SLF4J Logger` (مبدأ الترميز السليم).
   - حالة كاملة للبث (المُذيع، وقت البدء، المشاهدون) في `ConcurrentHashMap` آمن للخيوط.
   - دالة `getActiveStreams()` وقيم حقيقية بدل أرقام وهمية.
2. **`LiveStreamController` جديد** — يوصّل ميزة البث المباشر فعليًا بواجهة API:
   - `GET /api/live/streams`، `GET|POST` للمشاهدين، `POST /api/live/admin/streams/{id}/start|stop` (محمية بدور ADMIN عبر `SecurityConfig`).
3. **`AdvancedMessageService`** — إصلاحات:
   - `println` → Logger.
   - عمليات Mongo مكتوبة بأنواعها (`MessageDocument::class.java`) بدل النصوص الخام.
   - حذف ناعم متسق مع `DeleteService` (تصفير الحمولة + `deletedAt`) بدل `remove` الخام الذي يهدم أرقام التسلسل.
4. **`MessageDocument`** — أضيف `isEdited` و`editedAt` لدعم التعديل فعليًا (الاستخدامات القائمة بمعاملات مسمّاة، فالتغيير متوافق تمامًا).

### 6.2 لوحة الإدارة
- `npm run check:api` / `npm run check` — فحص العقد كجزء من QA.
- Proxy قابل للضبط، تنظيف الملفات الميتة، مكوّنات الموافقة صحيحة وجاهزة للاستخدام.

### 6.3 توثيق
- تصحيح `FINAL_SUMMARY.md`: عدد خدمات Docker الفعلي 10 (وليس 13) مع تعداد فحوصات الصحة (7/10).

---

## 7) ما تعذّر تشغيله هنا ولماذا (مع الحل)

| المكوّن | السبب | الحل عند توفر بيئة كاملة |
|---|---|---|
| الخادم الخلفي (Spring Boot/Kotlin 2.4, Java 21) | لا JDK، و Maven Central محجوبة | `docker compose build backend && docker compose up backend` أو `./gradlew bootJar` بجهاز فيه JDK 21 وإنترنت مفتوح |
| تطبيق Android (`red-app`) | لا Android SDK ولا وصول لـ Google Maven | `scripts/local-first-run.sh 192.168.1.50` مع `BUILD_ANDROID=1` بعد `scripts/prefetch-android-crypto.ps1` |
| Media SFU (mediasoup) | تنزيل العامل الجاهز/البناء يحتاج GitHub releases + wrapdb (محجوبان) | `docker compose up media-sfu` (داخل Docker يصل للشبكة) |
| المنظومة كاملة | لا Docker | `./scripts/local-first-run.sh <IP>` (Linux) أو `local-first-run.ps1` (Windows) — يبني 10 خدمات ويصدر APK |

> ملاحظة أمانة: أي تعديل في Kotlin لم يُختبر بترجمة فعلية بسبب غياب JDK؛ التعديلات حافظت على الأنماط القائمة وتم التحقق سكونيًا من الاستيرادات، لكن **خطوة `bootJar` الفعلية تبقى إلزامية** قبل أي إصدار.

---

## 8) كيف تشغّل المنظومة كاملة (المرجع)

```bash
cd RED_Ultimate_V1-main/RED_Ultimate
chmod +x scripts/local-first-run.sh
./scripts/local-first-run.sh 192.168.1.50          # أو مع BUILD_ANDROID=1 لبناء APK
```

- لوحة الإدارة: `http://<IP>:8088/`
- صحة الخادم: `http://<IP>:8088/health` — صحة SFU: `http://<IP>:8088/sfu-health`
- بيانات المسؤول في `RED_Ultimate/.env` (لا تُرفع إلى Git).

لوحة الإدارة وحدها بدون Docker:

```bash
cd RED_Ultimate/admin_dashboard
npm install && RED_API_TARGET=http://localhost:8080 npm run dev
```

---

## 9) الخطوات التالية المقترحة (بترتيب الأولوية)

1. بناء وتشغيل الخادم فعليًا (`bootJar` ثم Compose) وتشغيل اختبارات JUnit العشرة الموجودة في `backend-server/src/test`.
2. تشغيل `docker compose up` كاملًا على جهاز فيه Docker واختبار سيناريو الهاتفين (تسجيل → موافقة → رسائل → مكالمة).
3. بناء APK وتثبيته على جهازين فعليين (WebRTC + مفاتيح libsignal) — بوابة الإطلاق المتبقية.
4. اختبار DINSTAR على عتاد فعلي (UC2000) بعد ضبط IP وحدود المستخدمين — يستهلك رصيد SIM.
5. تشغيل `./gradlew ci` (يتضمن ktlint + lint + اختبارات + فحص STOPSHIP) قبل أي إصدار.

---

*أُعدّ هذا التقرير آليًا خلال جلسة عمل تفاعلية؛ جميع النتائج قابلة لإعادة الإنتاج بالأوامر المذكورة.*

---

# ملحق الجولة الثانية (2026-08-08) — التعميم الكامل والدفع إلى GitHub

## ما أُنجز في هذه الجولة

### 1) الدفع الكامل إلى GitHub
- تم دفع الفرع `arena/019fe3bc-pro-v1` بالكامل إلى `github.com/Ayman123123123/pro_v1` (كل الملفات الـ 10,268+ في المستودع).
- التأكد: لا توجد ملفات غير ملتزمة سوى مخلفات البناء المستبناة عمدًا (`dist/`, `node_modules/`)، ولا أسرار مكشوفة (`.env` و`secrets/` مستبناة، ومفتاح التوقيع `red-debug.p12` عام للتصحيح فقط كما هو موثق).

### 2) فحوصات عميقة إضافية
| الفحص | النتيجة |
|---|---|
| تطابق كل كيانات JPA (users, user_devices, refresh_sessions, recovery_codes, audit_events) مع ترحيلات Flyway (V1–V13) بما فيها ALTER TABLE | ✅ متطابق (يمنع انهيار ddl-auto=validate) |
| عقد Android ↔ الخادم (54 مسارًا في تطبيق red-app) | ✅ كلها موجودة في الخادم |
| ازدواج الفئات في نفس الحزمة (الخادم) | ✅ صفر |
| بقايا println في الخادم و red-app | ✅ صفر بعد إصلاح الجولة الأولى |
| اختبارات red-app (UuidV7, SafetyQrPayload) | ✅ حقيقية وسليمة المرجع |
| 24 مجلدًا علويًا لكل منها README | ✅ 24/24 |
| مولّد YOUNES ID (RedIdGenerator) مقابل نمط التحقق | ✅ متطابق |

### 3) إصلاحات ونواقص أُغلقت
- **اختبار وهمي**: `MessageServiceTest` كان يطبع "PASS" فقط — استُبدل بـ **9 اختبارات حقيقية** (رفض UUID غير v7، رفض هويات خاطئة، رفض أنواع ciphertext مجهولة، منع الحسابات المحجوبة، تفويض ACK للجهاز المستهدف فقط، تقدم الحالات SENT→READ، منع الازدواجية في التخزين).
- **Gradle Wrapper للخادم الخلفي**: أُضيف `backend-server/gradlew` + properties (Gradle 8.12 المطابق لصورة Docker) — يمكن الآن بناء الخادم محليًا بـ `./gradlew test bootJar` وضمن CI.
- **CI وهمي**: ملف `docker-image.yml` كان قالبًا عامًا (`my-image-name`) — استُبدل بـ **5 وظائف CI حقيقية**: (1) بناء واختبار الخادم، (2) لوحة الإدارة: عقد API + بناء إنتاج، (3) فحوصات ثابتة: تطابق الكيانات + صيغة SFU، (4) صحة docker compose، (5) بناء APK أندرويد مع رفع الـ artifact.
- **ملفات ميتة**: حُذفت `temp-dc.yml` و`build_log.txt` (سجل فشل UTF-16 من جلسة سابقة) و`rebuild_log.txt` — لا يشير إليها أي سكربت أو وثيقة.

### 4) أدوات جديدة
- `RED_Ultimate/scripts/check-schema-consistency.py` — فاحص تطابق الكيانات مع الترحيلات (يمنع انهيار الإقلاع)، يعمل من الجذر: `python3 scripts/check-schema-consistency.py` ✅ سليم.
- `admin_dashboard/scripts/check-api-contract.mjs` — فاحص عقد الواجهة (من الجولة الأولى) ✅ 35/35.
- كلاهما موصول بـ CI.

### 5) التشغيل
- لوحة الإدارة ما زالت حية على `http://localhost:8088` (HTTP 200) — وستظهر عبر المعاينة.
