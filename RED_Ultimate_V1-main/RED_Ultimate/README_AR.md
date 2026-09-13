# RED Ultimate V1 — المنصة السيادية

منصة مراسلة اجتماعية ومكالمات صوتية ومرئية مبنية حول هوية RED، مع لوحة إدارة ووسيط وسائط SFU.

## المكونات

```text
RED_Ultimate/
├── backend-server/         Kotlin Spring Boot API
├── red-app/                Android Jetpack Compose
├── admin_dashboard/        React + TypeScript
├── media-sfu/              mediasoup SFU
└── nginx.conf              Reverse proxy
```

## التشغيل السريع

```bash
cd RED_Ultimate/backend-server
./gradlew bootRun

cd ../admin_dashboard
npm install
npm run dev

cd ..
./gradlew :app:assembleDebug
```

للتشغيل المتكامل استخدم `docker-compose.yml` و`local-first-run.sh` أو النسخة PowerShell المقابلة.

## الميزات

- مراسلة ومجموعات ومحتوى اجتماعي.
- مكالمات صوتية ومرئية داخلية عبر WebRTC وSFU.
- رسائل صوتية ووسائط مع فحص النوع والتشفير المناسب.
- هوية RED، موافقة إدارية، JWT/refresh، وسجل تدقيق.
- PostgreSQL وMongoDB وRedis وMinIO.

## الاختبارات

```bash
cd backend-server && ./gradlew test
cd ../red-app && ./gradlew test
```

لا تُحفظ ملفات `.env` أو الأسرار أو مخرجات البناء في Git.
