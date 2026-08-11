# admin_dashboard/ — لوحة الإدارة القانونية الوحيدة

> **الحالة:** نشط — هذه هي لوحة الإدارة الوحيدة المعتمدة بعد توحيد نسخ `pro/` و`project/pro/` وحذف لوحات الأدمن المكررة.

## الوظيفة

تطبيق React 19 + TypeScript/Vite/Ant Design لإدارة منصة YOUNES/RED محليًا:

- دخول المسؤول عبر `/api/auth/login`.
- لوحة رئيسية ومقاييس حية.
- إدارة المستخدمين والموافقات والأجهزة.
- مراقبة المحتوى والبلاغات والإشراف.
- إدارة الإعلانات وأعلام الميزات والنسخ الاحتياطية.
- مركز الأمان: Kill Switch، remote wipe، صلاحيات PSTN، وسجل التدقيق.
- DINSTAR/PSTN، SFU/media، الرسائل، الإشعارات، البنية التحتية، والسجلات الحية.

اللوحة لا تتصل بقاعدة البيانات مباشرة؛ كل شيء يمر عبر backend/Nginx.

## قرار التوحيد

تم اختيار هذه النسخة لأنها الأحدث والأجمل والأوسع. ميزات النسخ القديمة دُمجت داخل صفحات حديثة، ثم حُذفت نسخ لوحة الأدمن المكررة من:

```text
pro/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/
project/pro/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/
```

راجع تقرير التوحيد:

```text
../../ADMIN_DASHBOARD_CONSOLIDATION_AR.md
```

## التشغيل

```bash
npm install
RED_API_TARGET=http://127.0.0.1:8080 npm run dev
```

### تشغيل كامل بقاعدة بيانات حقيقية (تطوير)

لا يتطلب JDK ولا PostgreSQL/Mongo/Redis/MinIO:

```bash
npm run dev:server         # نافذة 1 — خادم التطوير على 8080 (SQLite)
npm run dev                # نافذة 2 — اللوحة على 8088
npm run dev:server:reset   # لإعادة القاعدة إلى بيانات أولية نظيفة
```

`dev-server/` خادم تطوير مدعوم بقاعدة **SQLite حقيقية على القرص** عبر
`node:sqlite` المدمج في Node 22 — بلا أي حزمة جديدة في `package.json`
(متوافق مع `DEPENDENCY_POLICY.md`).

- `dev-server/db.cjs` — المخطط مشتق من كيانات JPA الحقيقية
  (`UserAccount` · `UserDevice` · `AdminAuditLog` · `AdminSessions` · `ContentModels`)،
  مع بيانات أولية عربية و30 يومًا من التحليلات.
- `dev-server/server.cjs` — **92 مسارًا** بأشكال مطابقة حرفيًا لعقد
  `AdminV2Controller.kt` (صفحات `{content,totalElements}` مقابل مصفوفات صريحة).

**كل إجراء يكتب فعليًا في القاعدة ويبقى بعد إعادة التشغيل.** الموافقة تنفّذ
منطق `RedApprovalService.processAction` كاملًا: تغيير الحالة، تسجيل
`approvedAt/approvedBy`، **إصدار شهادة تفويض موقّعة بـ ECDSA P-256**
(بنفس صيغة `DeviceCertificateService`: `v1|userId|redId|deviceId|fingerprint|issuedAt|expiresAt`)،
وعند الرفض/الحظر: إبطال جلسات التحديث وإلغاء الأجهزة. حسابات `ADMIN`
محمية من الحظر، وكل إجراء يولّد سجل تدقيق `ACCOUNT_<الحالة>` يُبَث مباشرة
في صفحة السجل الحي عبر WebSocket بعد تذكرة `POST /api/admin/ws-ticket`.

التسجيل (`POST /api/auth/register`) متاح أيضًا بنفس قواعد
`RegistrationService`: **بلا رقم هاتف ولا بريد ولا OTP**، والحساب والجهاز
كلاهما `PENDING` حتى موافقة المسؤول — وهو المسار الذي تظهر به صفوف جديدة
في صفحة الموافقات.

أي مسار غير معرّف يُعيد **404 صريحًا** ويُسجَّل في الطرفية — بدل رد نجاح
عام كان يُخفي النقص ثم ينهار في الواجهة بـ `undefined.filter`.

> للتطوير المحلي فقط: بلا مصادقة حقيقية، وغير مُضمَّن في صورة Docker.
> ملف القاعدة `dev-server/data/` مستبعد من Git ويُبنى تلقائيًا عند أول تشغيل.
> الإنتاج يمر عبر `backend-server` الحقيقي مع PostgreSQL/Mongo/Redis/MinIO.

إنتاجيًا عبر Docker Compose، Nginx الرئيسي يمرر:

```text
/       -> admin-panel:3000
/api    -> backend:8080
/ws     -> backend WebSocket
/sfu    -> media-sfu:4000
```

## التحقق

```bash
npm run check        # عقد API + حارس الواجهة + فحص الأنواع (يشغّلها CI أيضًا)
npm run build        # tsc --noEmit ثم حزمة Vite للإنتاج
npm run check:server # فحص تنفيذي: كل إجراء يغيّر الحالة فعليًا (يتطلب dev:server مشغّلًا)
```

`npm run build` صارم: TypeScript check + Vite bundle.
`tsconfig.json` يعمل الآن بـ `"strict": true` بصفر أخطاء — أي تراجع يفشل البناء.

### حارس الواجهة

`npm run check:guards` يفشل عند:

1. أي أصل خارجي (خط/سكربت/نمط) — يخالف `default-src 'self'` في `nginx.conf` ومبدأ Local-first.
2. `setInterval` خام بدل `usePolling`.
3. أي سر مكتوب داخل الشيفرة.

## عطل مُصلَح: صف الموافقة لا يختفي

**العرض:** الموافقة على مستخدم في «الموافقات المعلقة» تُظهر رسالة نجاح، لكن
الصف يبقى في القائمة بعد التحديث.

**السبب الجذري:** الخادم الوهمي السابق كان يرد `{success:true}` على
`POST /api/admin/users/action` **دون تغيير أي حالة**. الواجهة كانت سليمة:
`handleAction()` يستدعي الإجراء ثم `await load()`، فيُعيد الجلب نفس القائمة.
نفس النمط كان يصيب كل المسارات الكاتبة (النشر، الحظر، حل البلاغات...).

**الإصلاح:** استُبدل الخادم الوهمي بخادم مدعوم بـ SQLite ينفّذ منطق
`RedApprovalService.processAction` الحقيقي ويكتب التغيير في القاعدة.

**طريقة التحقق:** `npm run check:server` — كل فحص يتبع النمط
«نفّذ ← أعد الجلب ← تأكد أن الحالة تغيّرت»، وهو النمط الوحيد الذي يكشف
هذه الفئة من الأعطال. الفحص يُنشئ بياناته بنفسه عبر التسجيل، فينجح
مرارًا على نفس القاعدة.

## الملفات الأساسية

- `src/App.tsx` — shell الحديث الوحيد.
- `src/pages/Login.tsx` — دخول حديث مع فحص `/health`.
- `src/api.ts` — عميل API موحد.
- `src/components/ErrorBoundary.tsx` — حد أخطاء لكل صفحة؛ عطل في قسم لا يُسقط اللوحة.
- `src/components/Chart.tsx` — غلاف ECharts بتسجيل انتقائي للوحدات (حزمة المخططات ‎562KB بدل ‎1.14MB).
- `src/hooks/usePolling.ts` — استطلاع دوري يتوقف عند إخفاء التبويب/انقطاع الشبكة ويمنع تداخل الطلبات.
- `scripts/check-api-contract.mjs` — فاحص عقد الواجهة والخادم.
- `scripts/check-frontend-guards.mjs` — حارس Local-first والاستطلاع والأسرار.
- `dev-server/db.cjs` — قاعدة SQLite حقيقية (مخطط مشتق من كيانات JPA) + إصدار شهادات ECDSA والتحقق منها.
- `dev-server/server.cjs` — خادم تطوير (92 مسارًا + بث WebSocket) مطابق لعقد الخادم، كل إجراء يكتب في القاعدة.
- `scripts/check-dev-server.mjs` — 25 فحصًا تنفيذيًا: نفّذ الإجراء ← أعد الجلب ← تأكد أن الحالة تغيّرت.
- `Dockerfile` + `dashboard.nginx.conf` — بناء وتقديم الإنتاج.

## الخطوط

الخطوط العربية (Cairo/Tajawal) مُجمّعة محليًا عبر `@fontsource` وتُبنى داخل
`dist/assets` كملفات woff2. لا يوجد أي اتصال بـ Google Fonts: الاستيراد الخارجي
السابق كان يُحجب في الإنتاج بسبب CSP فيسقط الخط العربي إلى خط احتياطي.

## ملاحظات أمان مستقبلية

- نقل refresh token من `localStorage` إلى HttpOnly cookie عند الانتقال للإنتاج العام.
- عدم تمرير tokens في query string لأي SSE/WebSocket إلا بتذكرة قصيرة العمر.
- إبقاء `RED_API_TARGET` واضحًا في التطوير لتجنب proxy loop.
