# أرشيف Compose — 2026-09-15 (المرحلة 10)

نُقلت هذه الملفات من `RED_Ultimate_V1-main/RED_Ultimate/` لأن القاعدة الآن
**ملف واحد لكل بيئة**: `docker-compose.yml` (الأساس) + `dev` + `staging` + `prod`.

| الملف الأصلي | المصير |
|---|---|
| `docker-compose.fix.yml` (إطفاء Flyway + `ddl-auto: update`) | **دُمج** في `docker-compose.dev.yml` (قسم `backend.environment`) |
| `docker-compose.host-debug.yml` (منافذ loopback للـ JVM المضيف) | **دُمج** في `docker-compose.dev.yml` (منافذ `db-postgres`/`db-mongo`/`cache-redis`) |

لا تحذف هذا الأرشيف — مرجع تاريخي فقط. أي تعديل جديد على بيئة التطوير
يكون في `docker-compose.dev.yml` مباشرة.
