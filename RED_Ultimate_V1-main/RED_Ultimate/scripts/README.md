# أدوات الفحص والتحقق — RED Ultimate

سكريبتات جاهزة تعمل من جذر `RED_Ultimate/` (أو من أي مكان مع المسار الكامل).

| الأداة | ماذا تفعل | التشغيل |
|---|---|---|
| `check-schema-consistency.py` | يقارن كيانات JPA/Hibernate مع ترحيلات Flyway (V1–V13) ليمنع انهيار `ddl-auto: validate` وقت الإقلاع. | `python3 scripts/check-schema-consistency.py` |
| `check-lfs-pointers.sh` | يكشف ملفات Git LFS pointer المكسورة (130 بايت) التي لا يملك المستودع محتواها الفعلي. | `bash scripts/check-lfs-pointers.sh` |
| `generate-local-identity-authority.sh` | يولّد سلطة هوية ECDSA P-256 محلية للمستخدمين والأجهزة. | `bash scripts/generate-local-identity-authority.sh` |
| `local-first-run.sh` | يشغّل المنظومة كاملة محليًا (Docker Compose + إعداد `.env` و`secrets/`). | `./scripts/local-first-run.sh <IP>` |
| `prefetch-android-crypto.ps1` | ينزّل ملفات libsignal الكبيرة إلى cache محلي resumable مع تحقق SHA-256. | PowerShell |
| `build-android-local.ps1` | يبني APK مضبوطًا على عنوان الخادم المحلي. | PowerShell |

## أدوات لوحة الإدارة (من داخل `admin_dashboard/`)

| الأمر | ماذا يفعل |
|---|---|
| `npm run check:api` | فاحص عقد API: يطابق كل استدعاءات الواجهة مع مسارات الخادم. |
| `npm run check` | فحص العقد + فحص صيغة `src/api.ts`. |
| `npm run build` | فحص TypeScript كامل + بناء إنتاج Vite. |

كل الأدوات أعلاه موصولة بوظائف CI في `.github/workflows/docker-image.yml`.
