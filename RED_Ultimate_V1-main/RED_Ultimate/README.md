# RED_Ultimate/ — دليل المشروع القانوني

هذا المجلد يحتوي المنتج والبنية التحتية ومصادر Signal التاريخية. **وجود مجلد لا يعني أنه يدخل البناء**؛ المرجع الحاسم هو [`settings.gradle.kts`](settings.gradle.kts).

## graph الحالي

```text
:app → red-app/
:shared-proto → shared-proto/
included build → build-logic/

backend-server/ بناء Spring مستقل يضم shared-proto
admin_dashboard/, media-sfu/, pstn-asterisk/ تبنيها Docker/CI
```

## المجلدات الأربعة والعشرون

| المجلد | الحالة والدور |
|---|---|
| [`red-app/`](red-app/README.md) | تطبيق Android القانوني `:app` |
| [`backend-server/`](backend-server/README.md) | Backend القانوني |
| [`shared-proto/`](shared-proto/README.md) | Protobuf الموحد |
| [`admin_dashboard/`](admin_dashboard/README.md) | لوحة الإدارة القانونية |
| [`media-sfu/`](media-sfu/README.md) | mediasoup SFU |
| [`pstn-asterisk/`](pstn-asterisk/README.md) | DINSTAR/Asterisk صوت فقط — الدليل: [`docs/DINSTAR_UC2000_GUIDE_AR.md`](docs/DINSTAR_UC2000_GUIDE_AR.md) |
| [`scripts/`](scripts/README.md) | تشغيل محلي ومفاتيح الهوية |
| [`gradle/`](gradle/README.md) | Wrapper/catalogs/dependency verification |
| [`build-logic/`](build-logic/README.md) | منطق وأدوات Gradle |
| [`wire-handler/`](wire-handler/README.md) | Wire build-time handler |
| [`app/`](app/README.md) | Signal gold mine خارج البناء |
| [`android/`](android/README.md) | AQYAL reference خارج البناء |
| [`app-android/`](app-android/README.md) | DevelopedChat reference خارج البناء |
| [`core/`](core/README.md) | مكتبات Signal قديمة خارج graph |
| [`lib/`](lib/README.md) | مكتبات Signal قديمة خارج graph |
| [`feature/`](feature/README.md) | ميزات Signal قديمة خارج graph |
| [`demo/`](demo/README.md) | عينات غير منشورة |
| [`fast-lint/`](fast-lint/README.md) | Lint تاريخي غير مسجل حاليًا |
| [`lintchecks/`](lintchecks/README.md) | Detectors تاريخية غير مسجلة |
| [`benchmark/`](benchmark/README.md) | Macrobenchmark تاريخي |
| [`microbenchmark/`](microbenchmark/README.md) | Microbenchmark تاريخي |
| [`baseline-profile/`](baseline-profile/README.md) | Profile generator تاريخي |
| [`reproducible-builds/`](reproducible-builds/README.md) | أدوات مقارنة APK تحتاج مواءمة |
| [`infrastructure/`](infrastructure/README.md) | أدوات مساعدة؛ Compose هو المرجع |

## ملفات التشغيل الأساسية

- `docker-compose.yml`: الخدمات المحلية والـ volumes والشبكة.
- `nginx.conf`: بوابة HTTP/WebSocket/SFU/admin.
- `.env.example`: أسماء المتغيرات دون أسرار حقيقية.
- `LOCAL_FIRST_RUN_AR.md`: تجربة Windows/Linux الأولى.
- `W0_MODULE_BOUNDARIES.md`: ما هو قانوني وما هو مرجع.

## التوثيقات الشاملة

- [`docs/01-PROJECT-OVERVIEW.md`](docs/01-PROJECT-OVERVIEW.md)
- [`docs/02-DATABASES.md`](docs/02-DATABASES.md)
- [`docs/03-SERVER-ADMIN-PANEL.md`](docs/03-SERVER-ADMIN-PANEL.md)
- [`docs/04-APPS.md`](docs/04-APPS.md)

## أوامر التحقق

```bash
# Backend + tests
cd backend-server && gradle clean build

# Android المحلي
cd .. && ./gradlew :app:assembleDebug \
  -PRED_SERVER_URL=http://SERVER_IP \
  --dependency-verification strict

# المنظومة
./scripts/local-first-run.sh SERVER_IP
```

على Windows استخدم `scripts/local-first-run.ps1`. لا تحفظ `.env` أو `secrets/` أو artifacts في Git.
