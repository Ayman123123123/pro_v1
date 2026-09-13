# استكشاف الأخطاء

## فشل التشغيل

```bash
docker compose --env-file .env config --quiet
docker compose --env-file .env ps
docker compose --env-file .env logs --tail=200 backend
```

تحقق من أن Docker يعمل، وأن الأسرار الأساسية موجودة في `.env`، وأن منافذ 8088 و8443 غير مستخدمة.

## فشل الصحة

```bash
curl -v http://127.0.0.1:8088/health
curl -v http://127.0.0.1:8088/sfu-health
docker compose logs --tail=200 media-sfu
```

تحقق من صحة PostgreSQL وMongoDB وRedis وMinIO قبل تحليل backend.

## فشل Android

- استخدم `-PRED_SERVER_URL=http://SERVER_IP:8088` عند البناء.
- تأكد أن الهاتف يصل إلى عنوان الخادم من الشبكة نفسها.
- لا تستخدم `localhost` من الهاتف للوصول إلى جهاز التطوير.
- راجع logcat لعقد التسجيل وWebSocket وICE.

## فشل البناء

شغّل `./scripts/check-all.sh`، ثم افحص Java 21 وGradle وNode.js. إذا لم يتوفر Java محليًا، استخدم بوابة Docker أو CI وسجّل الخطأ بدل إعلان نجاح غير متحقق.
