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

### تشغيل كامل بلا خادم حقيقي (تطوير)

لا يتطلب JDK ولا PostgreSQL/Mongo/Redis/MinIO:

```bash
npm run mock     # نافذة 1 — خادم وهمي على 8080
npm run dev      # نافذة 2 — اللوحة على 8088
```

`mock-backend.cjs` يخدم **86 مسارًا** بأشكال مطابقة حرفيًا لعقد
`AdminV2Controller.kt` (صفحات `{content,totalElements}` مقابل مصفوفات صريحة)،
ويبث سجلًا حيًا حقيقيًا عبر WebSocket على `/ws/admin/logs` بعد تذكرة
`POST /api/admin/ws-ticket`. الإجراءات (موافقة/حظر/نشر/إنشاء) تُعدّل الحالة
في الذاكرة فيظهر أثرها في الواجهة.

أي مسار غير معرّف يُعيد **404 صريحًا** ويُسجَّل في الطرفية — بدل رد نجاح
عام كان يُخفي النقص ثم ينهار في الواجهة بـ `undefined.filter`.

> للتطوير المحلي فقط: بلا مصادقة حقيقية، وغير مُضمَّن في صورة Docker.

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
```

`npm run build` صارم: TypeScript check + Vite bundle.
`tsconfig.json` يعمل الآن بـ `"strict": true` بصفر أخطاء — أي تراجع يفشل البناء.

### حارس الواجهة

`npm run check:guards` يفشل عند:

1. أي أصل خارجي (خط/سكربت/نمط) — يخالف `default-src 'self'` في `nginx.conf` ومبدأ Local-first.
2. `setInterval` خام بدل `usePolling`.
3. أي سر مكتوب داخل الشيفرة.

## الملفات الأساسية

- `src/App.tsx` — shell الحديث الوحيد.
- `src/pages/Login.tsx` — دخول حديث مع فحص `/health`.
- `src/api.ts` — عميل API موحد.
- `src/components/ErrorBoundary.tsx` — حد أخطاء لكل صفحة؛ عطل في قسم لا يُسقط اللوحة.
- `src/components/Chart.tsx` — غلاف ECharts بتسجيل انتقائي للوحدات (حزمة المخططات ‎562KB بدل ‎1.14MB).
- `src/hooks/usePolling.ts` — استطلاع دوري يتوقف عند إخفاء التبويب/انقطاع الشبكة ويمنع تداخل الطلبات.
- `scripts/check-api-contract.mjs` — فاحص عقد الواجهة والخادم.
- `scripts/check-frontend-guards.mjs` — حارس Local-first والاستطلاع والأسرار.
- `mock-backend.cjs` — خادم تطوير وهمي (86 مسارًا + بث WebSocket) مطابق لعقد الخادم.
- `Dockerfile` + `dashboard.nginx.conf` — بناء وتقديم الإنتاج.

## الخطوط

الخطوط العربية (Cairo/Tajawal) مُجمّعة محليًا عبر `@fontsource` وتُبنى داخل
`dist/assets` كملفات woff2. لا يوجد أي اتصال بـ Google Fonts: الاستيراد الخارجي
السابق كان يُحجب في الإنتاج بسبب CSP فيسقط الخط العربي إلى خط احتياطي.

## ملاحظات أمان مستقبلية

- نقل refresh token من `localStorage` إلى HttpOnly cookie عند الانتقال للإنتاج العام.
- عدم تمرير tokens في query string لأي SSE/WebSocket إلا بتذكرة قصيرة العمر.
- إبقاء `RED_API_TARGET` واضحًا في التطوير لتجنب proxy loop.
