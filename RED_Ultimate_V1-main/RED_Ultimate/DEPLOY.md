# RED Deployment Guide

## المتطلبات

- Docker وDocker Compose.
- Java 21 لبناء backend محليًا، أو Docker لبناء الصورة.
- Node.js لبناء لوحة الإدارة عند الحاجة.

## التشغيل

```bash
cp .env.example .env
# استبدل كل القيم السرية
./scripts/local-first-run.sh SERVER_IP
```

تتوفر الواجهة عبر `http://SERVER_IP:8088/`، وتتوفر health checks على `/health` و`/sfu-health`.

## بناء التطبيق

```bash
cd red-app
./gradlew :app:assembleDebug
```

## الأمان

- لا ترفع `.env` أو مجلد `secrets/` إلى Git.
- وافق على الحسابات والأجهزة من لوحة الإدارة.
- استخدم kill switch وتدوير الأسرار وفق سياسة التشغيل.
