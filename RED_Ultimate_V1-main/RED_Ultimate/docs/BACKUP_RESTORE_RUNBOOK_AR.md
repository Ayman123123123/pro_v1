# دليل النسخ والاستعادة الحقيقي

> **تنبيه:** النسخ لا تُشغّل من لوحة الإدارة أو من Backend. المهمة التشغيلية تحتاج Docker host وصلاحية للوصول للحاويات، ولهذا لا نمنح Backend Docker socket. تشغيلها مسؤولية مشغل موثوق.

## ما الذي تنسخه الأداة؟

`../scripts/backup-platform.sh` ينشئ أرشيف GPG مشفراً يضم:

- PostgreSQL: `pg_dump --format=custom`.
- MongoDB: `mongodump --archive --gzip`.
- Redis: RDB snapshot.
- MinIO: snapshot لكائنات `/data`.
- مفاتيح سلطة الهوية الموجودة في volume الخلفية.
- `manifest.env` و`SHA256SUMS` لكل ملف داخل الأرشيف.

لا تنشئ الأداة نسخة plaintext. يجب ضبط `BACKUP_GPG_RECIPIENT` بمستلم GPG مُستورد على الجهاز.

## قبل النسخ

1. نفّذ الأمر من جذر `RED_Ultimate` على Docker host.
2. تأكد من وجود `.env` وكلمات مرور صحيحة.
3. استورد public key للمستلم في GPG.
4. وفر تخزيناً خارج الجهاز للحزمة الناتجة في `backups/`.

```bash
export BACKUP_GPG_RECIPIENT='Operations Backup Key <backup@example.invalid>'
./scripts/backup-platform.sh
```

## التحقق والاستعادة

لا تطبق الاستعادة مباشرة على الإنتاج. ابدأ دائماً ببيئة Docker معزولة:

```bash
./scripts/restore-platform.sh /secure/offsite/younes-platform-<timestamp>.tar.gz.gpg --verify-only
```

التطبيق المدمر يتطلب acknowledgement صريحاً:

```bash
export I_UNDERSTAND_THIS_DESTROYS_CURRENT_DATA=RESTORE_YOUNES_PLATFORM
./scripts/restore-platform.sh /secure/offsite/younes-platform-<timestamp>.tar.gz.gpg --apply
```

بعدها افحص health، Flyway، تسجيل دخول مدير، وسائط MinIO، واتصال Android قبل قبول أي traffic.

## حدود الاتساق

النسخة `online-best-effort`: PostgreSQL dump متسق داخلياً، لكن MongoDB/Redis/MinIO لا تملك transaction موزعة مع PostgreSQL. للحصول على نقطة استعادة متطابقة تماماً، ضع التطبيق في maintenance mode وأوقف writers قبل النسخ. يجب تنفيذ restore drill دوري وتسجيل النتيجة في سجل التدقيق.
