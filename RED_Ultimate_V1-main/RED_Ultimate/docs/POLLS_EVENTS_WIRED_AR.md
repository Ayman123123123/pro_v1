# 🗳️📅 وصل شاشتي الاستطلاعات/الفعاليات كميزة مستخدم

> **التاريخ:** 2026-08-11
> **المبدأ:** بدل حذف الكود غير المستخدم، أوصله وأكمله وأطوره
> **الحالة:** ميزة مجتمعية موصولة بالكامل + أدوات إدارة محمية بـ role

---

## القرار والمبدأ

سابقاً حذفتُ `EventsScreen`/`PollsScreen` (كانتا ميتتين + مختلطتين إداري/مستخدم). المبدأ الجديد: **بدل حذف غير المستخدم، أوصله وأكمله وطوره.**

فاسترجعتُ الملفات الخمسة من التاريخ ووصلتها كميزة مستخدم حقيقية، مع معالجة جذرية لمشكلة الـ 403.

---

## المشكلة الجذرية وكيف حُلّت

المشكلة: المستخدم العادي (`AccountRole.USER`) يرى أزرار إدارية (إنشاء/إغلاق/حذف) تفشل بـ **403** → تجربة مكسورة.

**الحل: تمرير دور الحساب (role) عبر المسار الكامل + إخفاء الأدوات الإدارية بذكاء:**

### تمرير role (Backend → Android)
- `UserResponse.role` موجود بالفعل في Android (والـ backend يرجعه)
- `TokenStore`: أضفت `role` + `isAdmin` getter + حفظ في `save()` + إزالة في `clearSession()`
- `AuthState.Authenticated`: أضفت `isAdmin: Boolean`
- `AuthViewModel`: يمرّر `role == "ADMIN"` في `applyAuth` و `restore`

### إخفاء الأدوات الإدارية (nullable callbacks)
- `EventsScreen(isAdmin)`: زر «إنشاء» يظهر فقط لـ admin
- `EventDetailSheet(onCancel?, onDelete?)`: أزرار «إلغاء/حذف» تظهر فقط لـ admin (nullable)
- `PollsScreen(isAdmin)`: زر «إنشاء» يظهر فقط لـ admin
- `PollDetailSheet(onClosePoll?, onDelete?)`: زر «خيارات» + أزرار «إغلاق/حذف» تظهر فقط لـ admin

### فتح قراءة المحتوى للمستخدم
الشاشة تستدعي `GET /polls` و`GET /events` (القائمة الكاملة) — كانت تتطلب ADMIN.
- `SecurityConfig`: أضفت `GET /api/admin/content/polls` و`GET /api/admin/content/events` للمصادَق
- القراءة العامة آمنة (المحتوى منشور للمجتمع)؛ الإنشاء/التعديل/الحذف تبقى إدارية

---

## الوصل في التنقّل

- `SovereignScreen.EVENTS` و `SovereignScreen.POLLS` (جديد)
- `MoreScreen`: زرّا «الفعاليات» و«الاستطلاعات» مع أيقونات ووصف
- الشاشات تأخذ `TokenStore` (تُنشئ `AuthorizedApiClient` منها)

---

## ما يعمل للمستخدم العادي الآن

| العملية | الحالة |
|---|---|
| عرض قائمة الاستطلاعات/الفعاليات | ✅ (GET مسموح) |
| فلترة بالنوع/الحالة | ✅ |
| فتح تفاصيل | ✅ (GET مسموح) |
| **التصويت** على استطلاع | ✅ (POST vote مستثنى) |
| **RSVP** لفعالية (سأحضر/ربما/لن أحضر) | ✅ (POST rsvp مستثنى) |
| **تسجيل الحضور** (check-in) | ✅ (POST checkin مستثنى) |

## ما يعمل للمشرف (admin) فقط

| العملية | الحالة |
|---|---|
| إنشاء استطلاع/فعالية | ✅ (زر ظاهر لـ admin) |
| إغلاق استطلاع | ✅ (زر خيارات ظاهر لـ admin) |
| إلغاء/حذف فعالية | ✅ (أزرار ظاهرة لـ admin) |
| حذف استطلاع | ✅ (زر خيارات ظاهر لـ admin) |

---

## الملفات (8 معدّلة/مسترجعة)

| الملف | التغيير |
|---|---|
| `media/EventsScreen.kt` (مسترجع) | `isAdmin` param + إخفاء أدوات الإدارة |
| `media/PollsScreen.kt` (مسترجع) | `isAdmin` param + إخفاء أدوات الإدارة |
| `media/EventsApi.kt` (مسترجع) | — |
| `media/PollsApi.kt` (مسترجع) | — |
| `test/media/PollsApiTest.kt` (مسترجع) | — |
| `auth/TokenStore.kt` | `role` + `isAdmin` |
| `auth/AuthViewModel.kt` | `AuthState.Authenticated(isAdmin)` + تمرير role |
| `ui/RedDashboard.kt` | EVENTS/POLLS screens + MoreScreen + isAdmin |
| `config/SecurityConfig.kt` | GET /polls + GET /events للمصادَق |

---

## التحقق
- فاحص التكامل: 27/27 أخضر ✅
- schema + catalog: سليم ✅
- متوافق مع المبدأ الجديد: لا حذف، بل وصل + إكمال + تطوير
