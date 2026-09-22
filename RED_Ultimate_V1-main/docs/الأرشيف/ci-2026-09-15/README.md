# أرشيف CI — 2026-09-15 (المرحلة 10)

سير العمل الوحيد الحي الآن: `.github/workflows/red-ultimate-ci.yml`.
نُقلت الملفات التالية إلى هنا (خارج `.github/workflows` فلا تُشغَّل):

| الملف | سبب الأرشفة |
|---|---|
| `blank.yml` | قالب GitHub الابتدائي (`echo Hello, world!`) — بلا قيمة |
| `build-red.yml` | مسارات ميتة `project/pro/...` + مشغّل `project/**` غير موجود؛ بناء الأندرويد الحي في CI الموحد (`:app:` → `red-app/`) |
| `extract-project.yml` | مهمة لمرة واحدة (استخراج `pro.rar`) — أدّت غرضها |
| `quality-gate.yml` | **دُمج** في `red-ultimate-ci.yml` (نفس الوظائف الخمس + فحص `dev/staging/prod` + LF) |

إحياء أيٍّ منها = إعادته إلى `.github/workflows/` — ممنوع إلا بقرار موثّق،
لأن GitHub Actions يشغّل كل ملف هناك تلقائيًا.
