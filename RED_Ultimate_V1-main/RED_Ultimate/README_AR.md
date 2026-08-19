# 🇾🇪 RED Ultimate V1 — منصة السيادية

<div align="center">

**منصة محادثات ومكالمات سيادية متكاملة — اليمن أولاً**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF.svg)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-6DB33F.svg)](https://spring.io)
[![Java](https://img.shields.io/badge/Java-21-ED8B00.svg)](https://openjdk.org)
[![React](https://img.shields.io/badge/React-19.2-61DAFB.svg)](https://react.dev)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178C6.svg)](https://typescriptlang.org)
[![AntD](https://img.shields.io/badge/AntD-6.1-0170FE.svg)](https://ant.design)
[![Compose](https://img.shields.io/badge/Compose-Latest-4285F4.svg)](https://developer.android.com/jetpack/compose)

</div>

---

## 🎯 الرؤية

RED Ultimate V1 هو **منصة اتصالات سيادية يمنية** متكاملة تجمع:
- 💬 **محادثات** فورية ومشفّرة
- 📞 **مكالمات صوتية ومرئية** (VoIP + PSTN عبر DINSTAR)
- 📡 **بث مباشر** (Live Streaming)
- 🗳️ **استطلاعات رأي** تفاعلية
- 📅 **فعاليات ومؤتمرات**
- 🏷️ **هاشتاقات واتجاهات**
- 🎨 **ملصقات (Stickers)**
- 📖 **قصص (Stories)** و Highlights
- 💾 **نسخ احتياطي** و **استعادة** كاملة
- 🔒 **أمان** عالي (E2E Encryption, Magic Bytes, Audit Log)

---

## 🏗️ البنية المعمارية

```
RED_Ultimate/
├── backend-server/         🟢 Kotlin Spring Boot (Backend API)
├── red-app/                📱 Android (Jetpack Compose)
├── admin_dashboard/        🎨 React + TypeScript (لوحة الإدارة)
├── pstn-asterisk/          📞 Asterisk PBX integration
└── nginx.conf              🌐 Reverse proxy
```

### 🔧 التقنيات

| الطبقة | التقنية | الإصدار |
|--------|---------|---------|
| **Backend** | Kotlin | 2.2.20 |
| | Spring Boot | 3.5.16 |
| | Java | 21 |
| | MongoDB | 7.x |
| | PostgreSQL | 16.x |
| | Redis | 7.x |
| **Android** | Kotlin | 2.2.20 |
| | Jetpack Compose | BOM 2024.x |
| | Material 3 | Latest |
| | ExoPlayer/Media3 | 1.4.x |
| | Min SDK | 23 |
| | Target SDK | 35 |
| **Admin Dashboard** | React | 19.2 |
| | TypeScript | 5.9 |
| | Vite | 7.2 |
| | Ant Design | 6.1 |
| | ECharts | 6.0 |
| | React Router | 7.0 |

---

## 🚀 التشغيل السريع

### التشغيل المحلي الكامل

يشغّل المسار المحلي الأساسي **الخادم وقواعد البيانات وRedis وMinIO ولوحة الإدارة وMediaSFU وTURN وNginx**. يحتاج Docker Compose v2 وذاكرة متاحة لا تقل عن 6 GiB، ويفضل 8 GiB. يولد السكربت ملف `.env` محليًا بأسرار عشوائية ومفاتيح هوية للتطوير؛ لا ترفعه إلى Git.

```bash
cd RED_Ultimate
# Linux/macOS
./scripts/local-first-run.sh --server-ip 192.168.1.50

# Windows PowerShell
.\scripts\local-first-run.ps1 -ServerIp 192.168.1.50
```

بعد اجتياز فحوص الجاهزية تكون لوحة الإدارة والخادم متاحين على `http://<LAN-IP>:8088/` و`/health`. لبناء APK موجه للخادم المحلي أضف `--build-android` في Linux/macOS أو `-BuildAndroid` في PowerShell.

> PSTN وDINSTAR لا يبدأان افتراضيًا، لأنهما يحتاجان بوابة حقيقية على شبكة إدارة معزولة وأسرارًا فريدة. بعد إعدادها فقط استخدم `./scripts/local-first-run.sh --server-ip <IP> --enable-telephony` أو `.\scripts\local-first-run.ps1 -ServerIp <IP> -EnableTelephony`. لا تستخدم بيانات اعتماد المصنع أو تضع أسرار العتاد في Git.

### Backend

```bash
cd RED_Ultimate/backend-server
./gradlew bootRun
# يعمل على http://localhost:8080
```

### Admin Dashboard

```bash
cd RED_Ultimate/admin_dashboard
npm install
npm run dev
# يعمل على http://localhost:5173
```

### Android

```bash
cd RED_Ultimate/red-app
./gradlew assembleDebug
# الـ APK في: build/outputs/apk/debug/
```

---

## 📚 الوحدات الرئيسية

### 🎙️ الرسائل الصوتية (Voice Messages)

ملف كامل في `red-app/src/main/java/com/red/sovereign/media/voice/`:
- **`VoiceColors.kt`** — لوحة ألوان احترافية
- **`VoiceBubble.kt`** — فقاعة محادثة بمخطط موجي + playhead
- **`VoiceRecorderPanel.kt`** — واجهة التسجيل + المعاينة قبل الإرسال
- **`VoiceMessageViewModel.kt`** — 4 مستويات جودة: COMPACT 64kbps / STANDARD 96kbps / HIGH 128kbps / ULTRA 192kbps
- **`VoiceNotePlayer.kt`** — مشغل ExoPlayer مع drag-to-seek

**الميزات الأمنية:**
- ✅ Magic bytes check على الملفات الصوتية
- ✅ Lock-to-record
- ✅ Drag-to-cancel
- ✅ Preview قبل الإرسال
- ✅ تشفير End-to-End

### 🗄️ قواعد البيانات (20 Migration)

- **V1-V18**: Schema الأساسي + DINSTAR + PSTN + الرسائل
- **V19**: Admin Audit + Analytics + System Health (9 جداول)
- **V20**: Content Features المتقدمة (11 جدول)

### 🛡️ لوحة الإدارة (12 صفحة)

| الصفحة | الوصف |
|--------|-------|
| **Dashboard** | نظرة عامة + ECharts + Health |
| **UserManagement** | إدارة المستخدمين (CRUD + Ban/Promote) |
| **ContentManagement** | 4 تبويبات: Polls / Events / Hashtags / Stickers |
| **Reports** | البلاغات والإجراءات التأديبية |
| **AuditLog** | سجل كامل بعمليات الإدارة |
| **Backups** | النسخ الاحتياطية والاستعادة |
| **Announcements** | إعلانات النظام |
| **FeatureFlags** | تفعيل/تعطيل الميزات |
| **MasterLayout** | التخطيط الرئيسي |
| **DinstarControl** | إدارة بوابات DINSTAR GSM |
| **MasterOverview** | نظرة شاملة على الـ Master |
| **Diagnostics** | تشخيص وأعطال |

---

## 🧪 الاختبارات

### Backend (JUnit 5 + Mockito)

```bash
cd RED_Ultimate/backend-server
./gradlew test
```

**التغطية الحالية:**
- `MessageServiceTest` (25+ tests)
- `AdminServiceTest` (20 tests)
- `ContentServiceTest` (16 tests)
- `VoiceManifestTest` (5 tests)
- `VoiceMessageTypeTest` (4 tests)
- `VoiceMessageMetadataTest` (3 tests)
- `MediaSecurityScannerTest` (25+ tests)

### Android

```bash
cd RED_Ultimate/red-app
./gradlew test
```

---

## 🔒 الأمان

- ✅ JWT Access + Refresh tokens مع rotation
- ✅ BCrypt password hashing
- ✅ Magic bytes validation (audio/video/image)
- ✅ Audit log كامل لكل عملية إدارة
- ✅ Rate limiting + DDoS protection (Nginx)
- ✅ End-to-End encryption للرسائل
- ✅ Session management + kill switch
- ✅ IP allowlist للوحة الإدارة

---

## 📊 APIs (60+ endpoint)

### Admin
- `GET /api/admin/dashboard/summary` — ملخص لوحة الإدارة
- `GET /api/admin/users` — قائمة المستخدمين
- `POST /api/admin/users/{id}/approve` — موافقة
- `POST /api/admin/users/{id}/ban` — حظر
- `PUT /api/admin/users/{id}/role` — تغيير الدور
- `GET /api/admin/audit` — سجل التدقيق
- `GET /api/admin/feature-flags` — Feature flags
- `GET /api/admin/reports` — البلاغات

### Content (V20)
- `GET /api/admin/content/polls` — الاستطلاعات
- `POST /api/admin/content/polls/{id}/vote` — تصويت
- `GET /api/admin/content/events` — الفعاليات
- `POST /api/admin/content/events/{id}/rsvp` — تأكيد حضور
- `GET /api/admin/content/hashtags/trending` — هاشتاقات رائجة
- `GET /api/admin/content/sticker-packs` — حزم الملصقات

### Streaming
- `GET /api/admin/events/stream` — SSE stream مباشر
- `GET /api/admin/metrics/realtime` — metrics حية

---

## 🌐 الـ Deployment

```bash
# Docker Compose (بعد إنشاء .env ومفاتيح الهوية محليًا)
cd RED_Ultimate
docker compose up -d
# أضف --profile telephony فقط عند وجود DINSTAR/Asterisk مصرح بهما.

# أو يدوياً:
# 1. PostgreSQL + MongoDB + Redis
# 2. Backend (Spring Boot)
# 3. Admin Dashboard (Nginx serves static)
# 4. Android (APK distribution)
```

---

## 📜 الترخيص

جميع الحقوق محفوظة © 2026 RED Ultimate Team — سيادي 🇾🇪

---

## 🤝 المساهمة

هذا مشروع سيادي. للمساهمة:
1. Fork
2. Feature branch
3. Commit + Push
4. Pull Request

---

<div align="center">

**صُنع بـ ❤️ في اليمن — YOUNES Development Team**

</div>
