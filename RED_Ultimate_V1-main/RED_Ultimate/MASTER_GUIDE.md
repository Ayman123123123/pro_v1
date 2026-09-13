# دليل تشغيل منظومة RED

## البنية التحتية

من مجلد `RED_Ultimate` شغّل:

```bash
./scripts/local-first-run.sh 192.168.0.244
```

أو استخدم `scripts/local-first-run.ps1` على Windows. ينشئ السكربت الإعداد المحلي، يجهز مفاتيح الهوية، يتحقق من Compose، ثم يبني ويشغل الخدمات.

## بناء Android

```bash
cd red-app
./gradlew :app:assembleDebug
```

## الإدارة

افتح لوحة الإدارة على `http://localhost:8088/`. الحساب الأول يُدار وفق تدفق الموافقة الإدارية، ولا تُستخدم كلمات مرور تجريبية.

## الفحص

```bash
./scripts/check-all.sh
```

يجب تشغيل بوابة التحقق المناسبة قبل إعلان أي ميزة مكتملة.
