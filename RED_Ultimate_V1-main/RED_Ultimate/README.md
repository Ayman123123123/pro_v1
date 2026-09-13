# RED_Ultimate/ — دليل المشروع القانوني

هذا المجلد يحتوي المنتج والبنية التحتية ومصادر Signal التاريخية. **وجود مجلد لا يعني أنه يدخل البناء**؛ المرجع الحاسم هو [`settings.gradle.kts`](settings.gradle.kts).

## graph الحالي

```text
:app → red-app/
:shared-proto → shared-proto/
included build → build-logic/

backend-server/ بناء Spring مستقل يضم shared-proto
admin_dashboard/, media-sfu/ تبنيها Docker/CI
```

## المكونات الأساسية

| المجلد | الحالة والدور |
|---|---|
| [`red-app/`](red-app/README.md) | تطبيق Android القانوني `:app` |
| [`backend-server/`](backend-server/README.md) | Backend القانوني |
| [`shared-proto/`](shared-proto/README.md) | Protobuf الموحد |
| [`admin_dashboard/`](admin_dashboard/README.md) | لوحة الإدارة القانونية |
| [`media-sfu/`](media-sfu/README.md) | mediasoup SFU |
| [`scripts/`](scripts/README.md) | تشغيل محلي ومفاتيح الهوية |
| [`gradle/`](gradle/README.md) | Wrapper/catalogs/dependency verification |
| [`build-logic/`](build-logic/README.md) | منطق وأدوات Gradle |
| [`wire-handler/`](wire-handler/README.md) | Wire build-time handler |
| [`app/`](app/README.md) | Signal gold mine خارج البناء |
| [`core/`](core/README.md) و[`lib/`](lib/README.md) | مكتبات Signal قديمة خارج graph |
| [`feature/`](feature/README.md) | ميزات Signal قديمة خارج graph |
| [`infrastructure/`](infrastructure/README.md) | أدوات مساعدة؛ Compose هو المرجع |

## التشغيل الحقيقي

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\compose-recover.ps1 -RebuildBackend
```

ثم افتح `http://127.0.0.1:8088/`. بيانات الدخول تُنشأ من `.env` (`RED_ADMIN_USERNAME` و`RED_ADMIN_PASSWORD`).

## ملفات التشغيل الأساسية

- `docker-compose.yml`: الخدمات المحلية والـ volumes والشبكة.
- `nginx.conf`: بوابة HTTP/WebSocket/SFU/admin.
- `.env.example`: أسماء المتغيرات دون أسرار حقيقية.
- `LOCAL_FIRST_RUN_AR.md`: تجربة Windows/Linux الأولى.
- `W0_MODULE_BOUNDARIES.md`: ما هو قانوني وما هو مرجع.

## أوامر التحقق

```bash
cd backend-server && gradle clean build
cd .. && ./gradlew :app:assembleDebug -PRED_SERVER_URL=http://SERVER_IP --dependency-verification strict
./scripts/local-first-run.sh SERVER_IP
```

على Windows استخدم `scripts/local-first-run.ps1`. لا تحفظ `.env` أو `secrets/` أو artifacts في Git.
