# تقرير توحيد لوحة الإدارة — RED/YOUNES

**التاريخ:** 2026-08-10  
**الفرع:** `arena/019fe92e-pro-v1`  
**الهدف:** اختيار النسخة الأحدث والأجمل والأصح من واجهات الويب، دمج الميزات المفيدة من النسخ القديمة، ثم اعتماد لوحة إدارة واحدة وحذف نسخ لوحة الأدمن المكررة.

---

## القرار النهائي

تم اعتماد لوحة الإدارة القانونية الوحيدة:

```text
RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/
```

وحُذفت نسختا لوحة الأدمن المكررتان:

```text
pro/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/
project/pro/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/
```

سبب الحذف: بعد الفحص، النسخة القانونية الحديثة تحتوي كل الصفحات والوظائف الموجودة في النسخ القديمة، مع صفحات إضافية أحدث، TypeScript، Vite، build إنتاجي، أصول بصرية، API client أوسع، وواجهة RTL حديثة.

---

## ما تم فحصه

تمت مقارنة وفحص هذه النسخ الثلاث:

| النسخة | الحالة قبل التوحيد | القرار |
|---|---|---|
| `RED_Ultimate.../admin_dashboard` | الأحدث والأوسع والأجمل، React/Vite/TS، 20 قسمًا تقريبًا | اعتمدت كأساس |
| `pro/.../admin_dashboard` | نسخة وسيطة، فيها App.jsx/index.jsx قديمان وميزات أقل | حُذفت بعد التحقق |
| `project/pro/.../admin_dashboard` | نسخة أقدم وأصغر بكثير | حُذفت بعد التحقق |

كما فُحصت ملفات:

- `App.tsx` / `App.jsx`
- `Login.tsx`
- `Dashboard.tsx`
- `UserManagement.tsx`
- `DinstarControl.tsx`
- `MasterLayout.tsx`
- `api.ts`
- كل صفحات `src/pages/`
- كل تبويبات `src/pages/tabs/`
- `Dockerfile`
- `dashboard.nginx.conf`
- `vite.config.js`
- فاحص عقد API

---

## الدمج والتحسينات

### 1) اعتماد واجهة واحدة فقط

أُزيلت الواجهة القديمة الداخلية:

```text
src/pages/MasterLayout.tsx
src/_archive/
```

وأُزيلت الملفات غير المستخدمة التي كانت تمثل تبويبات/واجهات قديمة بعد دمج ميزاتها في الصفحات الحديثة:

```text
src/pages/UserApproval.tsx
src/components/LiveMonitor.jsx
src/pages/tabs/BackupTab.tsx
src/pages/tabs/PstnAccessTab.tsx
src/pages/tabs/UserIntelligenceTab.tsx
src/pages/tabs/OverviewTab.tsx
```

الميزات لم تُحذف وظيفيًا؛ بل أصبحت ضمن الصفحات الحديثة:

| القديم | المدموج في الحديث |
|---|---|
| `UserIntelligenceTab` | `UserManagement.tsx` |
| `PstnAccessTab` | `SecurityCenter.tsx` |
| `BackupTab` | `Backups.tsx` |
| `AuthorityTab` | `Approvals.jsx` مع إبقاء tab داخلي مستخدم |
| `LogStreamerTab` | `SystemLogs.tsx` |
| `MediaTab` | `MediaCenter.tsx` |
| `MessagingTab` | `MessagingCenter.tsx` |
| `InfrastructureTab` | `InfrastructureCenter.tsx` |
| `ModerationTab` | `ModerationCenter.tsx` |
| `NotificationsTab` | `NotificationsCenter.tsx` |

### 2) تطوير شاشة تسجيل الدخول

تم اختيار التصميم الحديث كأساس وتطويره بدل الرجوع للنسخ القديمة. شاشة الدخول الآن تشمل:

- Hero احترافي RTL.
- بطاقات ميزات موحدة.
- فحص `/health` وعرض حالة الخادم.
- دعم props القديم (`onLogin`, `onSuccess`, `isLoading`) لضمان عدم كسر الاستخدام السابق.
- رسالة أمنية واضحة للمسؤول.
- توثيق بصري بأن النسخ القديمة دُمجت داخل لوحة واحدة.

### 3) إصلاح Vite Proxy

كان proxy في التطوير يشير إلى نفس منفذ Vite `8088`، وهذا يسبب مشاكل عند `/api` و`/ws`.

أصبح الآن:

```js
const apiTarget = process.env.RED_API_TARGET || 'http://127.0.0.1:8080';
```

وبالتالي يمكن تشغيل اللوحة محليًا مع backend حقيقي:

```bash
RED_API_TARGET=http://127.0.0.1:8080 npm run dev
```

### 4) تفعيل البناء الصارم

أصبح `npm run build` ينفذ:

```bash
tsc --noEmit && vite build
```

بدل بناء Vite فقط، حتى لا تمر أخطاء TypeScript إلى Docker.

### 5) إصلاح TypeScript

تم إصلاح خطأ `SecurityCenter.tsx` الذي كان يمنع:

```bash
npm run build:check
```

### 6) إصلاح فاحص عقد API

كان فاحص العقد يقرأ `method` من الدالة التالية أحيانًا بسبب نافذة 300 حرف، فينتج false positives. تم تصحيحه ليقرأ method من نفس statement فقط.

كما حُذفت helpers غير مستخدمة من `api.ts` كانت تشير لمسارات غير موجودة في backend.

النتيجة النهائية:

```text
95 استدعاء واجهة مقابل 237 مسار خادم
العقد سليم ✅
```

### 7) إصلاح تعارضات backend المؤثرة على لوحة الأدمن

تمت إزالة تعارضات mappings التالية:

```text
GET /api/admin/users
GET /api/admin/audit
```

بتحويل legacy routes إلى:

```text
GET /api/admin/users/legacy
GET /api/admin/audit/recent
```

وبذلك تبقى المسارات الحديثة التي تستخدمها اللوحة بلا تعارض.

---

## الحالة النهائية للوحة

المسار الوحيد المعتمد:

```text
RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/
```

الصفحات الحديثة المعتمدة:

- Dashboard
- UserManagement
- Approvals
- ContentManagement
- Reports
- AuditLog
- ModerationCenter
- MessagingCenter
- Announcements
- FeatureFlags
- Backups
- SecurityCenter
- NotificationsCenter
- SystemLogs
- MediaCenter
- InfrastructureCenter
- DinstarControl
- MasterOverview
- Diagnostics

والتبويبات المتبقية تحت `pages/tabs` أصبحت مكونات داخلية للصفحات الحديثة، وليست لوحة أدمن ثانية.

---

## التحقق

تم تشغيل:

```bash
npm run build:check
npm run build
node scripts/check-api-contract.mjs
```

والنتائج:

```text
TypeScript check ✅
Vite production build ✅
API contract ✅
Duplicate admin dashboard directories removed ✅
Backend duplicate admin mappings removed ✅
```

> لم يتم تشغيل اختبارات JVM لأن بيئة sandbox الحالية لا تحتوي Java/JDK.

---

## ملاحظات مستقبلية

- ما زالت هناك مجلدات كبيرة خارج نطاق لوحة الأدمن تحت `pro/` و`project/pro/` تمثل نسخًا لمشروع كامل، لكن في هذه الجولة تم حذف **نسخ لوحة الأدمن** تحديدًا كما طُلب. حذف جذور `pro/` و`project/pro/` كاملة يحتاج جولة تنظيف مستقلة لأن عدد الملفات كبير جدًا.
- يفضّل لاحقًا نقل المكونات الداخلية `tabs/` إلى أسماء أوضح مثل `sections/` لأنها لم تعد تبويبات لوحة قديمة.
- يفضّل لاحقًا تحويل تخزين refresh token من `localStorage` إلى HttpOnly cookie.
