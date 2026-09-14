# المرحلة 10 — CI واحد + Compose لكل بيئة (2026-09-15)

> الفرع: `arena/01a0a14b-pro-v1` — الكميت: `a96858d` (محلي، بلا دفع).

## 1) CI واحد: `.github/workflows/red-ultimate-ci.yml`

| الخاصية | القيمة |
|---|---|
| المشغّلات | `push` + `pull_request` (بفلتر `paths`) + `workflow_dispatch` |
| `paths` | `RED_Ultimate_V1-main/RED_Ultimate/**` + ملف الـ workflow نفسه |
| `concurrency` | `red-ci-{workflow}-{ref}` مع `cancel-in-progress: true` |
| الصلاحيات | `contents: read` فقط |
| الوظائف (5) | Infrastructure and Docker / Backend tests (JDK 21) / Admin and app contract integration / Android 17 build and unit tests / Media SFU install and syntax |
| الجديد عن quality-gate | فحص render لمصفوفة `base+dev/staging/prod` + توثيق تعيين `:app:`→`red-app/` + LF |

ملاحظات تحقق (قبل الدمج في الملف الموحد):
- `:app:testDebugUnitTest :app:assembleDebug` **سليم** — `settings.gradle.kts` يصرّح `project(":app").projectDir = file("red-app")`، ومسار الـ artifact (`red-app/build/...`) متسق معه.
- اختبارات الباكند وحدات خالصة بلا Testcontainers/`@SpringBootTest` — لا خدمات حية لازمة.
- كل مراجع الوظائف موجودة: `scripts/check-infrastructure.py`، `scripts/verify-ssl-certs.sh`، `nginx.conf`، `.env.example`، `backend-server/Dockerfile` + `gradlew`، `admin_dashboard` (`check`+`build`+lock)، `media-sfu` (`check`+lock).
- ما أُسقط عمدًا من `build-red.yml`: مسارات `project/pro/...` الميتة، وإرفاق APK بالـ Release (نشر CD لا CI).

## 2) الأرشيف: `الأرشيف/ci-2026-09-15/`

`blank.yml` (قالب فارغ) + `build-red.yml` (مسارات ميتة) + `extract-project.yml` (مهمة لمرة واحدة) + `quality-gate.yml` (مدمج في الموحد) — منقولة بـ `git mv` (السجل محفوظ) مع `README.md` يوثق سبب كل واحدة. خارج `.github/workflows` فلا تُشغَّل.

## 3) Compose: ملف واحد لكل بيئة

| الملف | الحالة بعد P10 |
|---|---|
| `docker-compose.yml` | الأساس (12 خدمة) — بلا تغيير |
| `docker-compose.dev.yml` | **أُعيدت كتابته**: دمج `fix.yml` (إطفاء Flyway) + `host-debug.yml` (منافذ loopback) + زوج مراقبة خفيف حقيقي (retention يوم) بدل المقاطع المعلّقة |
| `docker-compose.staging.yml` | **أُعيدت كتابته**: زوج مراقبة حقيقي (retention 7 أيام) بدل المقاطع المعلّقة، بلا منافذ منشورة (خلف nginx كالإنتاج) |
| `docker-compose.prod.yml` | كامل أصلًا (coturn + المراقبة + المصدّرون) — بلا تغيير |
| `fix.yml` + `host-debug.yml` | **مدمجان ثم منقولان** إلى `الأرشيف/compose-2026-09-15/` مع README |

**عطل كان موجودًا**: مقاطع `prometheus:`/`grafana:` في `dev`/`staging` كانت overrides بلا `image` لخدمات غير موجودة في الأساس (المراقبة تعيش في `prod` فقط) — أي `base+dev` و`base+staging` كانا **يفشلان في الـ render**. أُصلح بتعريف الخدمتين كاملتين في كل بيئة.

**تحقق آلي (بلا Docker)**: parse كل الملفات + كل override إما خدمة كاملة (`image`/`build`) أو موجودة في الأساس + الشبكات/المجلدات/`depends_on` محلولة + كل متغيرات `${VAR:?}` مغطاة في `.env.example` — الكل ✅. وأُضيفت للـ `.env.example` عناصر ناقصة: `TURN_USERNAME`/`TURN_PASSWORD`/`SIP_REALM`/`TURN_INTERNAL_IP`/`GRAFANA_ADMIN_PASSWORD`.

## 4) فرز PRs

- المفتوح الوحيد: **#55** (فرع جلسة أخرى `arena/01a0a0cf-pro-v1`، 148 ملفًا، +21223/−9030).
- القرار: **يُترك مفتوحًا، غير جاهز للدمج** — CI أحمر (4 وظائف فاشلة، `UNSTABLE`) + تعارض دلالي مؤكد مع المراحل 8–10 غير المدفوعة (PSTN/Dinstar + CI).
- الإجراء: تعليق فرز موثّق على الـ PR (اخضرار CI ← ثم rebase على `main` بعد دفع المراحل ← ثم مراجعة المالك). لا إغلاق أحادي لعمل جلسة أخرى.

## 5) حماية `main` — ⚠️ تتطلب المالك (فشل API: 403)

التوكن الحالي بلا صلاحية إدارة الفروع (`Resource not accessible by integration`).
على المالك (أو أي admin) تنفيذ أحد:

**أ) من الواجهة**: Settings ← Branches ← Add rule للـ `main`:
- ✅ Require a pull request before merging (1 approval على الأقل)
- ✅ Dismiss stale pull request approvals when new commits are pushed
- ✅ Do not allow bypassing the above settings
- ✅ Restrict deletions + Block force pushes
- ⏳ Require status checks to pass (تُفعَّل **بعد أول تشغيل أخضر** للـ CI الجديد): `Infrastructure and Docker` / `Backend tests (JDK 21)` / `Admin and app contract integration` / `Android 17 build and unit tests` / `Media SFU install and syntax` + `Require branches to be up to date`

**ب) سطر واحد (admin + `gh auth` بصلاحية admin)**:
```bash
gh api repos/Ayman123123123/pro_v1/branches/main/protection -X PUT \
  -f required_pull_request_reviews='{"required_approving_review_count":1,"dismiss_stale_reviews":true}' \
  -f enforce_admins=true -f allow_force_pushes=false -f allow_deletions=false
```

## 6) ما تبقى (خارج P10)

- تشغيل CI الجديد فعليًا يتطلب دفعه (ممنوع حتى يأذن المالك) — أول تشغيل أخضر شرط تفعيل required-checks.
- `node-exporter` في `prod` يعمل بـ `network_mode: host` (موثّق في الملف) — يعمل على Linux فقط؛ على Docker Desktop يُتجاهل صامتًا.
