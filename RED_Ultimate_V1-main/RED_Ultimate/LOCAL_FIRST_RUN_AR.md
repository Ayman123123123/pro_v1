# التشغيل المحلي الأول — RED Ultimate

هذه تجربة محلية للتحقق من backend وAndroid ولوحة الإدارة وSFU وTURN. ليست إصدار إنتاج.

## المتطلبات

- Docker Desktop أو Docker Engine مع Compose v2.
- 6 جيجابايت ذاكرة Docker على الأقل، و8 جيجابايت مفضلة.
- Java 21 وAndroid SDK عند البناء خارج Docker.

## Linux/macOS

```bash
cd RED_Ultimate
./scripts/local-first-run.sh SERVER_IP
```

## Windows

```powershell
cd RED_Ultimate
.\scripts\local-first-run.ps1 -ServerIp SERVER_IP
```

يقوم السكربت بإنشاء `.env` محليًا، توليد مفاتيح هوية غير موجودة، والتحقق من Compose، ثم بناء وتشغيل PostgreSQL وMongoDB وRedis وMinIO وbackend وSFU وTURN ولوحة الإدارة وNginx.

## التحقق

```bash
docker compose --env-file .env config --quiet
curl http://SERVER_IP:8088/health
curl http://SERVER_IP:8088/sfu-health
bash scripts/check-all.sh
```

## بناء Android

```bash
./gradlew :app:assembleDebug -PRED_SERVER_URL=http://SERVER_IP:8088 --dependency-verification strict
```

## استكشاف الأخطاء

```bash
docker compose --env-file .env ps
docker compose --env-file .env logs --tail=200 backend
docker compose --env-file .env logs --tail=200 media-sfu
```

إذا لم يكن Java متاحًا فلن يعمل Gradle المحلي؛ استخدم صورة البناء أو ثبّت JDK 21. لا تُحفظ الأسرار ومخرجات البناء في Git.
