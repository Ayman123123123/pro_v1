# RED Ultimate V1

منصة RED المحلية للمراسلة الاجتماعية والمكالمات. المشروع القانوني داخل [`RED_Ultimate/`](RED_Ultimate/README.md).

## 🚀 خطوات التشغيل السريع

### 1. تشغيل الخوادم
```bash
./scripts/local-first-run.sh 192.168.1.50 --build-android
```
هذا يشغّل **10 خدمات Docker**: backend, PostgreSQL, MongoDB, Redis, MinIO, Coturn, media-sfu, Nginx, ntfy, certs-init.

### 2. ضبط ملف `.env`
```
DB_PASSWORD=...
MONGO_PASSWORD=...
REDIS_PASSWORD=...
JWT_SECRET=...
CLIENT_LAN_IP=192.168.1.50  # عنوان IP لجوالك
```

### 3. بناء تطبيق Android
```bash
cd RED_Ultimate
.\gradlew.bat :app:assembleDebug -PRED_SERVER_URL=http://192.168.1.50:8088
```

### 4. التسجيل والاتصال
- ثبّت APK على الجوال
- أنشئ حساب ← التطبيق يتصل تلقائيًا عبر mDNS/LAN

## المكونات القانونية

- Android: `RED_Ultimate/red-app/` كـ Gradle `:app`.
- Backend: `RED_Ultimate/backend-server/`.
- Protocol: `RED_Ultimate/shared-proto/`.
- Admin: `RED_Ultimate/admin_dashboard/`.
- SFU: `RED_Ultimate/media-sfu/`.
- Runtime: `RED_Ultimate/docker-compose.yml`.

> `app/` و`android/` و`app-android/` مصادر تاريخية خارج البناء، وليست تطبيقات إطلاق إضافية.

## مبادئ المشروع

- التسجيل يعتمد على هوية RED وموافقة الإدارة، وليس على هاتف أو بريد أو OTP.
- المكالمات الصوتية والمرئية داخل المنصة تستخدم WebRTC وRED ID.
- مفاتيح libsignal الخاصة لا تغادر Android.
- المحتوى الاجتماعي العام ليس E2EE.
- لا توصف ميزة بأنها مكتملة قبل البناء واختبار runtime والجهاز المناسب.

## 🔧 التكامل المستمر (CI/CD)

خط أنابيب GitLab CI مُفعّل في `.gitlab-ci.yml`:
- **Backend Build**: بناء + اختبارات JUnit
- **Android Build**: تجميع APK تجريبي
- **Static Checks**: فحص التوافق والـ STOPSHIP gate
- **Docker Validate**: التحقق من Docker Compose
- **Deploy**: نشر تلقائي لـ staging/production

## التحقق

بوابة CI تبني backend وAPK ولوحة الإدارة وتفحص SFU. استخدم `docker-compose.yml` وملفات التشغيل العامة من أجل التحقق المحلي.

كل مجلد تشغيلي داخل `RED_Ultimate/` يحتوي `README.md` يوضح وظيفته وحالته وعلاقته بباقي النظام.

## 📁 بنية المشروع

```
RED_Ultimate_V1-main/
├── RED_Ultimate/                    # المشروع الرئيسي
│   ├── backend-server/              # سبرينغ بوت (REST API + WebSocket)
│   ├── red-app/                     # تطبيق أندرويد (Compose)
│   ├── admin_dashboard/             # لوحة الإدارة
│   ├── media-sfu/                   # خادم WebRTC SFU
│   ├── docker-compose.yml           # تشغيل كل الخدمات
│   └── shared-proto/                # بروتوكولات مشتركة
├── .gitlab-ci.yml                   # خط أنابيب GitLab CI
├── .github/workflows/               # GitHub Actions CI
├── scripts/                         # سكربتات التشغيل
└── docs/                            # التوثيق
```

## ⚡ البنية المعمارية

```
┌─────────────┐     HTTP:8088      ┌──────────┐     WS:8088      ┌──────────┐
│  Android    │ ──────────────►    │  Nginx   │ ──────────────►   │ Backend  │
│  App        │ ◄──────────────    │          │ ◄──────────────   │ Spring   │
└──────┬──────┘                    └──────────┘                    └────┬─────┘
       │                                                              │
       │  ──► ws://server/ws/master (messages)                    PostgreSQL
       │  ──► ws://server/ws/calls  (WebRTC)                      MongoDB
       │  ──► ws://server/ws/conf    (conference)                  Redis
       │  ──► ntfy:2586 (push, no FCM)                           MinIO
       │  ──► coturn:3478 (TURN/STUN)                            Coturn
       │  ──► media-sfu:4000 (SFU)                                SFU
```
