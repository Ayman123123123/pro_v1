# 🚀 حزمة الميزات الأربع — A1 + A3 + B1 + B6

> **التاريخ:** 2026-08-11
> **الميزات:** شاشة البروفايل + قفل البصمة + تعديل/حذف للجميع + Presence/آخر ظهور
> **الحالة:** مُنفّذة عبر كل الطبقات (Backend + Android)

---

## A1 — شاشة البروفايل + رفع الصورة ✅

### Backend
- **V25 migration** (`V25__User_Avatar_And_Bio.sql`): إضافة `avatar_url` (VARCHAR 255) و`bio` (VARCHAR 280) لجدول `users`
- **`UserAccount.kt`**: حقول `avatarUrl` + `bio`
- **`AuthController.updateProfile`**: يقبل الآن `displayName` + `avatarUrl` + `bio` (PATCH /api/auth/profile)
- **`PublicRedProfile`** (backend): حقل `avatarUrl` جديد
- **`ContactService`**: كل الاستعلامات (`contacts`, `incoming`, `blocked`) تشمل `avatar_url`

### Android
- **`ProfileViewModel.kt`** (جديد): رفع صورة مشفّرة عبر `MediaApi` → ربط بـ `/api/auth/profile`
- **`ProfileScreen.kt`** (جديد): صورة + اسم + username + بايو + QR للهوية — RTL كامل
- **`PublicRedProfile`** (Android): حقل `avatarUrl`
- **`MoreScreen`**: بطاقة البروفايل قابلة للنقر تفتح الشاشة
- الصورة تُرفع **مشفّرة E2EE** — الخادم يخزّن `objectKey` فقط (لا يرى الصورة)

---

## A3 — قفل التطبيق بالبصمة (Biometric AppLock) ✅

### Android
- **`build.gradle.kts`**: `implementation(libs.androidx.biometric)` (كان معرّفاً في `libs.versions.toml` غير مُفعّل)
- **`AppLockScreen.kt`** (جديد): `BiometricPrompt` الرسمي — بصمة/وجه/نمط الجهاز
- **`YounesSettings`**: حقل `appLockEnabled` + `hideLastSeen` (محفوظ في SharedPreferences)
- **`SettingsViewModel`**: `setAppLockEnabled` + `setHideLastSeen`
- **`MainActivity.onResume()`**: قفل تلقائي عند العودة للتطبيق إن كان AppLock مفعّلاً
- **`PrivacySettings`**: toggle "قفل التطبيق بالبصمة" + "إخفاء آخر ظهور"
- البصمة متصلة بـ **Android Keystore** — لا تُخزّن أي بيانات حساسة

---

## B1 — تعديل/حذف رسالة للجميع (Edit/Unsend) ✅

**موجود ومكتمل بالفعل** — تم التحقق:
- **EDIT**: `RichMessage(action="EDIT", editOf=...)` — يحدّث الرسالة الأصلية محلياً وعند الطرف المستقبِل (E2EE)
- **DELETE for everyone**: `RichMessage(action="DELETE", deleteOf=...)` — يحذف عند كل الأطراف
- الـ UI: قائمة إجراءات الرسالة (long-press) فيها "تعديل" و"حذف لدى الجميع"
- `resolveRichMessages` يطبّق التعديل والحذف على العرض

---

## B6 — Presence + آخر ظهور ✅

### Backend
- **`ContactService.presenceDetailed`** (جديد): يرجع `Map<String, PresenceInfo>` مع `online` + `lastSeen`
- **`PresenceInfo`** model (جديد)
- **`ContactController`**: endpoint جديد `/api/contacts/presence/detailed`
- `lastSeen` يُؤخذ من `UserAccount.last_seen`

### Android
- **`DirectoryViewModel`**: `lastSeenByContact` map + `lastSeenLabel(redId)` — نص عربي ("متصل الآن"، "آخر ظهور: 5 دقيقة"، …)
- **`refreshPresence`**: يستخدم endpoint مفصّل مع fallback للبسيط
- **رأس المحادثة** في `RedDashboard`: يعرض "آخر ظهور" بدل المعرّف عند التوفّر
- **`hideLastSeen`** setting: إعداد لإخفاء آخر ظهور (في PrivacySettings)

---

## ملفات تم تعديلها/إنشاؤها

### Backend (6 ملفات)
| الملف | التغيير |
|---|---|
| `V25__User_Avatar_And_Bio.sql` (جديد) | migration: avatar_url + bio |
| `UserAccount.kt` | حقول avatarUrl + bio |
| `AuthController.kt` | updateProfile يقبل avatarUrl + bio |
| `PublicDirectoryController.kt` | PublicRedProfile + avatarUrl |
| `ContactService.kt` | avatar_url في الاستعلامات + presenceDetailed + PresenceInfo |
| `ContactController.kt` | endpoint /presence/detailed |

### Android (8 ملفات)
| الملف | التغيير |
|---|---|
| `ProfileViewModel.kt` (جديد) | رفع صورة + تحديث بروفايل |
| `ProfileScreen.kt` (جديد) | شاشة بروفايل كاملة + QR |
| `AppLockScreen.kt` (جديد) | BiometricPrompt |
| `build.gradle.kts` | اعتماد biometric |
| `SettingsViewModel.kt` | appLockEnabled + hideLastSeen |
| `MainActivity.kt` | قفل onResume + AppLockScreen |
| `SettingsScreen.kt` | toggles في PrivacySettings |
| `DirectoryViewModel.kt` | PresenceInfo + lastSeenByContact + lastSeenLabel |
| `RedDashboard.kt` | PROFILE screen + MoreScreen + عرض آخر ظهور |

---

## بوابة الميزة — حالة الامتثال

| معيار | A1 | A3 | B1 | B6 |
|---|---|---|---|---|
| Owner واضح | ✅ | ✅ | ✅ | ✅ |
| Threat model | ✅ (صورة مشفّرة) | ✅ (Keystore) | ✅ (E2EE) | ✅ (إخفاء اختياري) |
| API/Proto | ✅ PATCH /profile | ✅ (محلي) | ✅ (RichMessage) | ✅ /presence/detailed |
| اختبارات | ⏳ | ⏳ | ✅ (موجودة) | ⏳ |
| RTL | ✅ | ✅ | ✅ | ✅ |
| build evidence | ⏳ CI | ⏳ CI | ✅ | ⏳ CI |

> الميزات تنتظر اختبار runtime على جهازين فعليين (Alpha gate). الـ CI على GitHub سيبني ويتحقق.
