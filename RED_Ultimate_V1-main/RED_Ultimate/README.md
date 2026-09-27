# 🏛️ YOUNES Sovereign — منصة التراسل السيادية

<div align="center">

**أقوى منصة تراسل سيادية — مشفرة بالكامل — بدون Google/Apple**

[![Version](https://img.shields.io/badge/Version-1.0.0--alpha02-14C79A?style=for-the-badge)](CHANGELOG-ULTIMATE.md)
[![License](https://img.shields.io/badge/License-AGPL--3.0-E0B551?style=for-the-badge)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge)](backend-server/)
[![Android](https://img.shields.io/badge/Android-SDK_37-34A853?style=for-the-badge)](red-app/)
[![React](https://img.shields.io/badge/React-19-61DAFB?style=for-the-badge)](admin_dashboard/)

</div>

---

## 📋 جدول المحتويات

- [نظرة عامة](#-نظرة-عامة)
- [المميزات](#-المميزات)
- [بنية المشروع](#-بنية-المشروع)
- [المتطلبات](#-المتطلبات)
- [التشغيل السريع](#-التشغيل-السريع)
- [البورتات](#-البورتات)
- [التطوير](#-التطوير)
- [استكشاف الأخطاء](#-استكشاف-الأخطاء)

---

## 🌟 نظرة عامة

YOUNES Sovereign هي منصة تراسل **سيادية بالكامل** — لا تعتمد على Google FCM أو Apple APNS أو أي خدمة سحابية خارجية. تشمل:

- 📱 **تطبيق أندرويد** — Kotlin + Jetpack Compose + Material 3
- ⚙️ **خادم خلفي** — Spring Boot + Kotlin (PostgreSQL + MongoDB + Redis + MinIO)
- 🖥️ **لوحة إدارة** — React 19 + TypeScript + Ant Design
- 🔒 **تشفير E2E** — بروتوكول Signal + SQLCipher + Ed25519

---

## ✨ المميزات

### 💬 الرسائل
- رسائل نصية وصوتية ومرئية وملفات
- الرد والتوجيه والتعديل والحذف
- ردود تفاعلية (Reactions)
- رسائل مجدولة
- مؤشر الكتابة والقراءة
- بحث في الرسائل

### 📞 المكالمات
- مكالمات صوتية/مرئية (WebRTC + MediaSoup SFU)
- مكالمات جماعية حتى 100 مشارك
- مشاركة الشاشة (PiP)
- تسجيل المكالمات
- بث مباشر (Livestream)

### 👥 المجموعات والقنوات
- مجموعات حتى 1000 عضو
- قنوات بث
- مجتمعات (Communities)
- استطلاعات رأي تفاعلية
- أدوار ورتب المشرفين

### 📖 القصص
- نص وصور وفيديو
- موسيقى وموقع
- إعدادات خصوصية

### 🔐 الأمان
- تشفير E2E (Signal Protocol)
- قفل بالبصمة/الوجه
- شفافية المفاتيح
- UnifiedPush (بدون Google)
- شهادات Ed25519

---

## 🏗️ بنية المشروع

```
RED_Ultimate/
├── 📱 red-app/                  # تطبيق الأندرويد (Kotlin + Compose)
│   └── src/main/java/com/red/sovereign/
│       ├── auth/                # المصادقة والتوكنات
│       ├── calls/               # المكالمات (WebRTC)
│       ├── contacts/            # جهات الاتصال
│       ├── core/                # النواة والقاعدة
│       ├── features/            # الميزات (دردشة، قصص، قنوات)
│       ├── security/            # الأمان والتشفير
│       ├── settings/            # الإعدادات
│       └── ui/                  # واجهات المستخدم
│
├── ⚙️ backend-server/           # الخادم (Spring Boot + Kotlin)
│   └── src/main/kotlin/com/red/server/
│       ├── auth/                # المصادقة (JWT + Ed25519)
│       ├── calls/               # إدارة المكالمات
│       ├── config/              # تكوينات Spring
│       ├── controllers/         # نقاط API
│       ├── database/            # قواعد البيانات
│       ├── messaging/           # الرسائل
│       ├── media/               # الوسائط (MinIO)
│       ├── websocket/           # WebSocket
│       └── social/              # منشورات وقصص
│
├── 🖥️ admin_dashboard/          # لوحة الإدارة (React + TypeScript)
│   └── src/
│       ├── pages/               # 32+ صفحة إدارية
│       ├── components/          # مكونات مشتركة
│       ├── api/                 # عميل API
│       └── stores/              # حالة Zustand
│
├── 🔌 media-sfu/                # MediaSoup SFU (Node.js)
├── 🏗️ infrastructure/           # TLS وأدوات مساعدة
├── 📡 shared-proto/             # Protobuf مشترك
├── 🐳 docker-compose.yml       # 10 خدمات Docker
├── 🌐 nginx.conf               # بوابة HTTP/WS/SFU
└── 🔑 .env                     # متغيرات البيئة
```

---

## 📦 المتطلبات

| الأداة | الإصدار الأدنى | الملاحظات |
|--------|---------------|-----------|
| Docker | 24+ | مع Docker Compose V2 |
| Java JDK | 21+ | لبناء الباك إند |
| Android SDK | 37 (compileSdk) | لبناء التطبيق |
| Node.js | 24+ | للوحة الإدارة |
| RAM | 8 GB+ | للتشغيل المحلي |

---

## 🚀 التشغيل السريع

### 1. نسخ ملف البيئة
```bash
cp .env.example .env
# عدّل كلمات المرور في .env
```

### 2. إنشاء مفاتيح الهوية
```bash
./scripts/generate-identity-keys.sh
```

### 3. تشغيل الخدمات
```powershell
# Windows
powershell -ExecutionPolicy Bypass -File .\scripts\compose-recover.ps1 -RebuildBackend

# Linux/macOS
docker compose up -d --build
```

### 4. فتح اللوحة
افتح `http://127.0.0.1:8088/` ← بيانات الدخول من `.env`:
- **المستخدم**: `RED_ADMIN_USERNAME`
- **كلمة المرور**: `RED_ADMIN_PASSWORD`

### 5. بناء التطبيق
```bash
./gradlew :app:assembleDebug \
  -PRED_SERVER_URL=http://YOUR_SERVER_IP:8088 \
  --dependency-verification strict
```

---

## 🔌 البورتات

| البورت | الخدمة | الوصف |
|--------|--------|-------|
| `8088` (HTTP) | Nginx | البوابة الرئيسية (API + لوحة الإدارة) |
| `8443` (HTTPS) | Nginx | البوابة المشفرة |
| `4000` | MediaSFU | خادم المكالمات (WebSocket) |
| `3478` (TCP/UDP) | Coturn | STUN/TURN relay |
| `5349` (TCP/UDP) | Coturn | TURNS |
| `2586` | ntfy | UnifiedPush (بديل FCM) |
| `9000` (localhost) | MinIO | تخزين الوسائط (API) |
| `9001` (localhost) | MinIO | واجهة MinIO |
| `40000-40100` (UDP) | MediaSFU | RTC media ports |
| `45000-45050` (UDP) | Coturn | TURN relay ports |

> ⚠️ MinIO مقيّد على `127.0.0.1` — لا يُتاح للشبكة الخارجية.

---

## 💻 التطوير

### بناء الباك إند
```bash
cd backend-server && ./gradlew clean build
```

### تشغيل لوحة الإدارة محلياً
```bash
cd admin_dashboard && npm install && npm run dev
```

### بناء APK
```bash
./gradlew :app:assembleDebug
# APK → red-app/build/outputs/apk/debug/
```

---

## 🔧 استكشاف الأخطاء

| المشكلة | الحل |
|---------|------|
| `DB_PASSWORD is required` | تأكد من وجود `.env` مع كل المتغيرات المطلوبة |
| Backend لا يستجيب | انتظر 180 ثانية (start_period) ثم `docker logs red-backend` |
| خطأ TLS | أعد تشغيل `docker compose up -d certs-init` |
| MongoDB unhealthy | انتظر 40 ثانية (start_period لـ Windows Docker) |
| APK لا يتصل | تأكد من `RED_SERVER_URL` صحيح في `gradle.properties` |

---

## 📁 ملفات التشغيل الأساسية

| الملف | الدور |
|-------|-------|
| `docker-compose.yml` | الخدمات المحلية والـ volumes والشبكة |
| `nginx.conf` | بوابة HTTP/WebSocket/SFU/admin |
| `.env.example` | أسماء المتغيرات دون أسرار حقيقية |
| `LOCAL_FIRST_RUN_AR.md` | تجربة Windows/Linux الأولى |
| `W0_MODULE_BOUNDARIES.md` | ما هو قانوني وما هو مرجع |

---

## 🏗️ المكونات الأساسية

| المجلد | الحالة والدور |
|---|---|
| [`red-app/`](red-app/README.md) | تطبيق Android الرئيسي `:app` |
| [`backend-server/`](backend-server/README.md) | Backend الرئيسي (Spring Boot) |
| [`shared-proto/`](shared-proto/README.md) | Protobuf الموحد |
| [`admin_dashboard/`](admin_dashboard/README.md) | لوحة الإدارة (React) |
| [`media-sfu/`](media-sfu/README.md) | خادم المكالمات (MediaSoup SFU) |
| [`scripts/`](scripts/README.md) | أدوات التشغيل ومفاتيح الهوية |
| [`core/`](core/README.md) و [`lib/`](lib/README.md) | مكتبات مشتركة |
| [`infrastructure/`](infrastructure/README.md) | TLS وأدوات مساعدة |

---

<div align="center">

**صُنع بـ ❤️ في اليمن 🇾🇪**

</div>
