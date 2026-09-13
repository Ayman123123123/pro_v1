# دليل التشغيل — RED Ultimate

## التشغيل السريع

```bash
cd RED_Ultimate
./run.sh
```

للتشغيل الكامل عبر Docker:

```bash
./scripts/local-first-run.sh 192.168.0.244
# Windows
.\scripts\local-first-run.ps1 -ServerIp 192.168.0.244
```

## الوصول بعد التشغيل

| الخدمة | العنوان |
|---|---|
| لوحة الإدارة | `http://<IP>:8088/` |
| صحة الخادم | `http://<IP>:8088/health` |
| صحة SFU | `http://<IP>:8088/sfu-health` |
| HTTPS عند تفعيله | `https://<IP>:8443` |

بيانات المسؤول في `RED_Ultimate/.env` ولا تُرفع إلى GitHub.

## الفحص الآلي

```bash
bash scripts/check-all.sh
```

يشمل الفحص بنية Kotlin، عقد API، مخطط البيانات، لوحة الإدارة، SFU، وملفات Compose.

## اختبار Android

1. ابنِ APK من `red-app` أو من CI.
2. ثبّته على جهازين.
3. سجّل بهوية RED وانتظر موافقة المسؤول.
4. اختبر الرسائل والمجموعات والوسائط والمكالمات الداخلية.

## النسخ الاحتياطي

```bash
mkdir -p local-backup
cp .env local-backup/red.env
cp -R secrets local-backup/secrets
docker compose --env-file .env exec -T db-postgres pg_dump -U admin -d red_sovereign -Fc > local-backup/postgres.dump
docker compose --env-file .env exec -T db-mongo mongodump --archive > local-backup/mongo.archive
```

## مراجع المشروع

- `API_REFERENCE.md`: مسارات API الحالية.
- `docs/01-PROJECT-OVERVIEW.md`: المعمارية.
- `docs/02-DATABASES.md`: قواعد البيانات.
- `README.md`: الحالة العامة.
