# 🔴 تحليل شامل — لوحة الإدارة القديمة vs الجديدة — فهم حرفي

> **القديمة** = ما قبل `f4bcfbba` (2026-08-08) — `App.jsx` + 13 تبويب داخل `MasterLayout` + مسارات إنجليزية  
> **الجديدة** = ما بعد `537465ec + f4bcfbba` (2026-08-09/10) — `App.tsx` الموحد 12 صفحة + 74 دالة `api.ts`  

---

## 1) الملفات القديمة المحفوظة حرفياً

### `src/_archive/App.jsx` (4044 بايت — 92 سطر)
```jsx
import { authStore } from './api';
import Login from './pages/Login';
import { DashboardOutlined, TeamOutlined, ... } // 6 أيقونات فقط
const Dashboard = lazy(...); // 6 صفحات فقط: Dashboard, MasterOverview, UserManagement, MasterLayout, DinstarControl, Diagnostics
const menuItems = [
  {key:'dashboard', icon:<DashboardOutlined/>, label:'Dashboard'},  // إنجليزي
  {key:'master', icon:<SafetyOutlined/>, label:'Master Control'},
  {key:'users', icon:<TeamOutlined/>, label:'User Management'},
  {key:'dinstar', icon:<MobileOutlined/>, label:'DINSTAR Control'},
  {key:'monitor', icon:<MonitorOutlined/>, label:'Live Monitor'},
  {key:'diagnostics', icon:<SettingOutlined/>, label:'Diagnostics'},
];
function App() {
  [authenticated, setAuthenticated] = useState(Boolean(authStore.access()||authStore.refresh()))
  [currentPage, setCurrentPage] = 'dashboard'
  useEffect on younes:auth-expired
  if (!authenticated) return <Login onSuccess=>setAuthenticated(true) />
  logout = () => { authStore.clear(); setAuthenticated(false); } // لا إخطار للخادم
  renderPage switch 6 حالات فقط
  return <ConfigProvider rtl dark #050A16><Layout><Sider collapsible><Menu items={menuItems} onClick=setCurrentPage/></Sider><Header younes السيادي + logout danger><Content margin 16 padding 24 #07111F>{renderPage()}<Suspense><Spin/></Suspense></Content></Layout>
}
```
**الفرق الجوهري:** لا `groupedMenu`، لا `groupLabels`، لا `BrowserRouter`، لا `Header` يعرض اسم الصفحة الحالية، لا `width 240` ثابت، لا `Tag` حالة الخدمة.

### `src/_archive/index.jsx` (209 بايت — 7 سطر)
```jsx
import React from 'react'; import {createRoot} from 'react-dom/client'; import App from './App';
const container = document.getElementById('root'); createRoot(container).render(<App/>);
```
**الجديد `src/index.tsx` (12 سطر):**
```tsx
import {BrowserRouter} from 'react-router-dom';
root.render(<React.StrictMode><BrowserRouter><App/></BrowserRouter></React.StrictMode>);
```
الفرق: القديم بلا `StrictMode` ولا `BrowserRouter` — لا يدعم `react-router-dom` ولا فحص الأخطاء المزدوج.

### `src/components/LiveMonitor.jsx` (القديم — 25 سطر)
```jsx
const LiveMonitor = () => {
  [stats, setStats] = {voip:0, pstn:0, msgs:0}
  useEffect interval 2000ms → fetch('/api/admin/monitor/stats') → setStats
  return <div><h2>Live System Monitoring</h2><div>4K VoIP Calls: {voip}</div><div>PSTN Calls (Dumin): {pstn}</div><div>Messages Delivered: {msgs}</div></div>
}
```
**المشاكل:** يستخدم `fetch` مباشر بلا `apiFetch` (لا JWT)، يفترض `4K` (الصحيح 1080p)، يسمي `Dumin` (القديم)، لا `apiStore`، لا معالجة 401.

**الجديد:** لا يوجد `LiveMonitor.jsx` منفصل — دُمج داخل `MasterOverview.tsx` و `Dashboard.tsx` مع `apiFetch` و `getRealtimeMetrics`.

---

## 2) الصفحات القديمة (6) vs الجديدة (12)

| الصفحة | القديمة | الجديدة | الفرق الحرفي |
|---|---|---|---|
| **Login** | `Login.tsx` 3140 بايت — `onSuccess` بسيط — Card 400 | نفس الملف لكن **الجديد** يتوقع `onLogin(username,password)` في `App.jsx` القديم، بينما `App.tsx` الجديد يتوقع `onSuccess` (تم توحيده) — التصميم نفسه لكن الجديد أُصلح لـ `adminLogin` الصحيح |
| **Dashboard** | كان `Dashboard.tsx` قديم 11810 بايت — 3 إحصائيات فقط — بدون `getSystemAnalytics` السبعة أيام — Chart بسيط | **الجديد 11810 بايت لكن مع 7 أيام analytics + 4 Stats + 2 Charts + Health Row + Storage** — إضافة `getSystemAnalytics(start,end)` + `formatBytes` + `Progress` |
| **UserManagement** | كان `UserApproval.tsx` قديم (2715 بايت) — Table `name/email/date/status` + 3 Buttons Approve/Reject/Ban → `api/admin/users/update-status?userId=&status=` (مسار قديم غير موجود الآن) | **الجديد `UserManagement.tsx` 14021 بايت** — Table `Avatar/redId/displayName/@username/status/role/PSTN/createdAt/actions` + 6 إجراءات (Approve/Reject/Promote/Ban/Unban/Delete) + Search + Status/Role Filter + Modals + `getUsers({page,size,search,status,role})` الصحيح |
| **DinstarControl** | كان `DinstarControl.tsx` قديم 7899 بايت — `Port` بسيط — بدون `resolveOp` المصحح — Alert بدون capabilities | **الجديد نفس الملف لكن مع YEMEN_OP المصحح (71/73/77/78/70/10) + capabilities + CDR Table + Modal USSD + Progress** |
| **MasterLayout** | كان يحتوي 9 تبويبات فقط (`AuthorityTab, DinstarTab...`) — Sider بدون width ثابت — Header بلا Badge حالة | **الجديد `App.tsx` يحتوي 12 صفحة خارج MasterLayout + MasterLayout نفسه أصبح تبويب واحد من 12 (`master`)** |
| **Diagnostics** | `Diagnostics.jsx` 3209 بايت — List 4 Systems (A VoIP 4K/B PSTN Dumin/C Messaging/Storage) → `api/master/v1/stats/realtime` | بقي كما هو — لكن الجديد أضاف `sfu-health` وأصلح `4K → 1080p` و `Dumin → DINSTAR` |
| **MasterOverview** | `MasterOverview.tsx` 1883 بايت — Row 4 Stats (active_users, pending_approvals, signal, db_health) — 5s interval | بقي كما هو — لكن الجديد يستخدمه كـ `monitor` تبويب واحد ضمن 12 |

**الجديدة أضافت 6 صفحات لم تكن موجودة إطلاقاً:**
- `Announcements.tsx` 10555 بايت — إعلانات مجدولة
- `AuditLog.tsx` 12719 بايت — سجل تدقيق مع Filter/Export
- `Backups.tsx` 12625 بايت — نسخ مع Trigger/Restore
- `ContentManagement.tsx` **29607 بايت** (الأضخم) — إدارة منشورات/استطلاعات/فعاليات/هاشتاقات/ملصقات
- `Reports.tsx` 12253 بايت — بلاغات محتوى
- `FeatureFlags.tsx` 7950 بايت — أعلام ميزات

---

## 3) التبويبات الـ 13 القديمة (داخل MasterLayout) — تحليل سطر سطر

كلها في `src/pages/tabs/` — كانت **الواجهة الوحيدة** قبل `f4bcfbba`:

| التبويب | السطور | المسار الحرفي | الحالة الآن |
|---|---|---|---|
| **AuthorityTab** 67 سطر | `GET /api/admin/users/pending` → Table redId/displayName/@username/createdAt/devices×Tag gold — Expand بصمة Identity copyable — Buttons موافقة/رفض + Modal سبب | **لا يزال يعمل لكنه مكرر** — `UserManagement` الجديد يغنيه — يبقى للتوافق داخل MasterLayout |
| **DinstarTab** 189 سطر | `Promise.allSettled(discover, status)` 5s interval — Summary 4 Stats + Grid 8 Cards — Yemen operators مصحح | **مكرر** — `DinstarControl` الجديد هو النسخة المتقدمة (مع USSD/CDR) — DinstarTab بقي كعرض بسيط داخل MasterLayout |
| **OverviewTab** 91 سطر | `GET /api/master/v1/stats/realtime + /health + /active-calls` 5s — 3 Stats + Health Tag | مكرر — `Dashboard` الجديد يغنيه |
| **SecurityTab** 118 سطر | Kill-switch `POST /admin/security/kill-switch?reason=` + Wipe `POST /wipe` + Table audit | لا يزال فريد — لم يُنقل لصفحة مستقلة بعد |
| **UserIntelligenceTab** 133 سطر | `GET /api/admin/users` → Table + Drawer 720px overview (online, messagesSent/Received, calls, securityEvents) + Modal كلمة مؤقتة ≥12 | فريد — الأقوى — لم يُنقل بعد |
| **NotificationsTab** 192 سطر | `getNotifications + markRead` | فريد |
| **LogStreamerTab** 52 سطر | `WebSocket /ws/admin/logs` | فريد |
| **BackupTab** 23 سطر | أوامر `pg_dump/mongodump/minio` copyable — Alert 5 أجزاء | **استُبدل** بـ `Backups.tsx` الجديد (378 سطر) المتكامل مع API |
| **InfrastructureTab** 19 سطر | placeholder | فارغ — يجب إخفاؤه |
| **MediaTab** 16 سطر | placeholder | فارغ |
| **MessagingTab** 67 سطر | placeholder رسائل | فارغ |
| **ModerationTab** 23 سطر | placeholder | **استُبدل** بـ `Reports.tsx` |
| **PstnAccessTab** 31 سطر | `pstnEnabled/pstnDailyLimit` | فارغ — دُمج في `UserManagement` |

**الحكم:** 5 تبويبات قوية (Authority, Dinstar, Security, UserIntelligence, Notifications) تستحق البقاء، 8 تبويبات إما مكررة أو placeholder يجب إخفاؤها أو دمجها.

---

## 4) `api.ts` — القديم vs الجديد

- **القديم (قبل 2026-08-08):** كان `api.ts` يحتوي `authStore + apiFetch + adminLogin + getDinstarPorts` فقط — حوالي 100 سطر — بدون `getUsers`, بدون `DashboardSummary`, بدون Content — كل الصفحات كانت تستخدم `fetch` مباشر.
- **الجديد (الآن 764 سطر — 74 دالة):** كل شيء يمر عبر `apiFetch` الموحد مع `401→rotate` — Interfaces `UserRecord`, `PageResponse`, `DashboardSummary`, `SystemHealth` — Content 28 دالة — Audit/Backups/Announcements...

**الفرق:** القديم كان يفشل عند 401 (لا rotate)، الجديد يدور تلقائياً ويحافظ على الجلسة.

---

## 5) التسلسل الزمني — Git حرفياً

```
140a5899 Initial: لوحة بسيطة (2 صفحات)
4187c017 🚀 تطوير شامل — أضاف MasterLayout 9 تبويبات + DinstarTab + Diagnostics
b3ca1c6f AuthFlow + TypeScript — حول App.jsx → App.tsx (لكن بقي 6 صفحات)
02750fcb YOUNES brand — أضاف icons + manifest
5c83eaaf Restore auth — أصلح logout الحقيقي
13d9323c دمج كل فروع arena — أضاف Dinstar مصحح
a4720c23 تحسينات — logout + جودة + API
0c0dcc28 Merge PR #7 — أصبحت اللوحة 6 صفحات + 13 تبويب (الوضع قبل التكامل)
---
f4bcfbba feat(admin-ui): 6 صفحات جديدة (Announcements...ContentManagement 882س) ← نقطة التحول
537465ec feat(content): نظام V20 ← أضاف api.ts 28 دالة Content
a533d97  دمج الموحد الآن ← أصبحت 12 صفحة + _archive
```

**القديمة** كانت `App.jsx` (إنجليزي 6 عناصر) + `MasterLayout` (9 تبويبات) = واجهتان متداخلتان.  
**الجديدة** وحدتهما في `App.tsx` واحد (12 صفحة مقسمة 3 مجموعات + MasterLayout كتبويب واحد).

---

## 6) ماذا يجب أن يحدث للقديمة؟

**لا تحذفها الآن — لكن:**
1. `src/_archive/` يبقى كمرجع — لكن لا يُبنى (Vite لا يستورده) — آمن.
2. التبويبات الـ 8 الفارغة (`Infrastructure, Media, Messaging, Moderation, Backup`...) يجب إخفاؤها من `MasterLayout` أو ربطها بـ API حقيقي — الآن تشغل مساحة بلا فائدة.
3. `LiveMonitor.jsx` و `UserApproval.tsx` القديمتان يجب حذفهما نهائياً — حل محلهما `MasterOverview` و `UserManagement` الجديدتان.
4. `api.ts` القديم (fetch مباشر) لا يوجد الآن — تم استبداله بالكامل — لا رجعة.

> **الخلاصة:** القديمة كانت **English + 6 صفحات + 13 تبويب متداخل + fetch بلا JWT** — الجديدة هي **عربي RTL + 12 صفحة مقسمة + 74 دالة موحدة + lazy + groupedMenu + preview كامل** — القديمة محفوظة في `_archive` و `tabs/` للتوافق، لكن المنتج القانوني هو `App.tsx` الجديد.
