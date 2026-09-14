#!/usr/bin/env node
console.error(`
YOUNES لا يشغّل خادم Node+SQLite بعد الآن.

المسار الوحيد:
  1) Docker Desktop أخضر
  2) powershell -ExecutionPolicy Bypass -File ..\\scripts\\compose-recover.ps1 -RebuildBackend
  3) افتح http://127.0.0.1:8088/
  4) ادخل بـ RED_ADMIN_USERNAME / RED_ADMIN_PASSWORD من ملف RED_Ultimate/.env

اللوحة في Docker تتحدث إلى Kotlin + PostgreSQL + Mongo + Redis + MinIO.
`);
process.exit(1);
