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

إنتاجيًا عبر Docker Compose، Nginx الرئيسي يمرر:

```text
/       -> admin-panel:3000
/api    -> backend:8080
/ws     -> backend WebSocket
/sfu    -> media-sfu:4000
```

## التحقق

```bash
npm run build:check
npm run check:api
npm run build
```

`npm run build` صار صارمًا: TypeScript check + Vite bundle.

## الملفات الأساسية

- `src/App.tsx` — shell الحديث الوحيد.
- `src/pages/Login.tsx` — دخول حديث مع فحص `/health`.
- `src/api.ts` — عميل API موحد.
- `scripts/check-api-contract.mjs` — فاحص عقد الواجهة والخادم.
- `Dockerfile` + `dashboard.nginx.conf` — بناء وتقديم الإنتاج.

## ملاحظات أمان مستقبلية

- نقل refresh token من `localStorage` إلى HttpOnly cookie عند الانتقال للإنتاج العام.
- عدم تمرير tokens في query string لأي SSE/WebSocket إلا بتذكرة قصيرة العمر.
- إبقاء `RED_API_TARGET` واضحًا في التطوير لتجنب proxy loop.
