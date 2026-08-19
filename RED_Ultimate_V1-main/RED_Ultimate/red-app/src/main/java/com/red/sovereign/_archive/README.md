# 📦 أرشيف الملفات المهملة (Deprecated Archive)

> هذا المجلد يحتوي على ملفات قديمة/مكررة نُقلت من الكود الإنتاجي.
> **لا تستورد من هذا المجلد في الكود الجديد.**

_آخر تدقيق: 2026-08-19 — طُوبق المحتوى مع الملفات الموجودة فعلاً._

---

## الملفات الموجودة حالياً

### `AuthFlow.legacy.kt.archived`
- **المسار الأصلي:** `com.red.sovereign.ui.AuthFlow` (`AuthFlow.kt`)
- **تاريخ الأرشفة:** 2026-08-11
- **الحجم:** 821 سطراً
- **السبب:** تدفق قديم محظور `PHONE_INPUT`/`OTP_VERIFICATION` مع
  `CountryCode +967` — يخالف مبدأ السيادة (بدون هاتف/SIM/OTP).
  الفاحص `check-sovereign-boundaries.sh` يفشل بوجوده.
- **البديل النشط:** `ui/AuthScreens.kt` (601 سطر) + `auth/AuthViewModel.kt`
  — username + password + RED ID، مُستخدم في `MainActivity`.
- **تحقق 2026-08-19:** البديل موجود وأشمل ✅

---

## ملفات أُرشفت سابقاً ثم حُذفت نهائياً

هذه كانت مذكورة في نسخة سابقة من هذا الملف لكنها **لم تعد موجودة**
في المجلد. تُوثَّق هنا للسجل فقط؛ بدائلها النشطة تعمل بالكامل:

| الملف المحذوف | تاريخ الأرشفة | البديل النشط | الحالة |
|---|---|---|---|
| `VoiceRecorder-core.kt.archived` | 2026-08-09 | `core/utils/VoiceRecorder.kt` (54س) | ✅ موجود |
| `VoiceRecorder-features-chat.kt` | 2026-08-09 | `media/VoiceMessageViewModel.kt` (463س) | ✅ موجود |
| `MediaBubble-features-chat.kt` | 2026-08-09 | `VoiceMessage` + `AttachmentMessage` في `RedDashboard.kt` | ✅ موجود |

> ملاحظة: نسخة أخرى من `MediaBubble.kt` (198 سطراً) ما زالت موجودة في
> `android/features/chat/` — وهي مجلد مرجع استخراج خارج الـ build graph.

---

## 📋 السياسة

- **لا تحذف** الملفات من هذا المجلد إلا بعد 6 أشهر من الأرشفة.
- إذا احتجت استعادة ملف، انقله إلى مكانه الأصلي وحدّث هذا الـ README.
- ملفات الأرشيف التي قد تسبب تكرار فئات يجب أن تبقى بامتداد غير `.kt`
  حتى لا تدخل الـ build.
- **عند حذف ملف من الأرشيف نهائياً:** انقل سطره إلى جدول
  "حُذفت نهائياً" أعلاه بدل إزالته من التوثيق.

---

### `SovereignCallSystem.kt.archived`
- **المسار الأصلي:** `com.red.sovereign.features.calls` (`features/calls/SovereignCallSystem.kt`)
- **تاريخ الأرشفة:** 2026-08-19
- **الحجم:** 128 سطراً
- **السبب:** شاشة مكالمة نشطة (`SovereignActiveCallScreen`) **لا يستدعيها أي كود**،
  وتعمل على نموذج بيانات خاص بها (`SovereignCall`) منفصل تماماً عن آلة الحالة
  الحقيقية `CallUiState`. فهي لا تعرف الحالات النهائية الخمس
  (`Declined`/`Busy`/`NoAnswer`/`CallEnded`/`Reconnecting`) ولا الحجب/الانتظار،
  ونموذجها المكرَّر كان سيتطلّب مزامنة يدوية دائمة مع الحالة الفعلية.
- **البديل النشط:** `calls/CallOverlay.kt` → `YounesCallOverlay` — موصول عبر
  `UnifiedCallOverlays` من `RedDashboard`، ويقرأ `CallRuntime.state` مباشرةً
  فيعرض كل الحالات بما فيها الخمس الجديدة.
- **ملاحظة:** حُذف مجلد `features/calls/` لأنه صار فارغاً بعد النقل.

---

### `PstnCallScreen.calls.kt.archived` و `PstnCallScreen.features.kt.archived`
- **المساران الأصليان:** `calls/PstnCallScreen.kt` (389س) و
  `features/pstn/PstnCallScreen.kt` (312س)
- **تاريخ الأرشفة:** 2026-08-19
- **السبب:** ثلاث شاشات لمكالمة البوابة كانت موجودة معًا، اثنتان منها
  **لا يستدعيهما أحد**، وتحملان الاسم `PstnCallScreen` نفسه في حزمتين
  مختلفتين. الملف الأول يعترف في توثيقه بوجود «نسخة فاخرة» تسبقه.
- **البديل النشط:** `calls/PstnCallOverlay.kt` →
  `Material3ExpressivePstnCallScreen` في `CallScreen.kt`، موصولة عبر
  `UnifiedCallOverlays` وتُنهي المكالمة على الخادم فعليًّا
  (`POST /api/pstn/calls/{id}/hangup`) فتحرّر منفذ GSM.
- **لماذا لا تُوصَل بدل أرشفتها:** كلتاهما تعرض زرَّي كتم ومكبّر صوت
  بمعالِجات فارغة `{}`. صوت مكالمة البوابة يمرّ عبر شبكة المشغّل لا
  عبر محرّك WebRTC، فلا يملك التطبيق مقبضًا يكتمه. وصلهما كان
  سيُنتج أزرارًا تتبدّل صورتها ولا تفعل شيئًا — وهو أسوأ من غيابها.
- **ما استُخرج قبل الأرشفة:** `PstnCallStatus` (يعتمد عليها 30 موضعًا)
  و`CallMetrics` و`formatPstnDuration` نُقلت إلى
  `calls/PstnCallModels.kt`. حذف الملف دونها كان سيُسقط سلسلة PSTN كلها.
- **تحقق 2026-08-19:** البديل موصول، و30/30 مرجعًا لـ `PstnCallStatus`
  ما زالت تعمل ✅

---

### `DinstarViewModel.kt.archived` و `DinstarWebSocketBridge.kt.archived`
- **المساران الأصليان:** `features/dinstar/DinstarViewModel.kt` (28 كB)
  و`features/dinstar/DinstarWebSocketBridge.kt` (13.6 كB)
- **تاريخ الأرشفة:** 2026-08-19
- **السبب:** لوحة إدارة أسطول بوابات كاملة داخل **تطبيق المستخدم
  العادي**. لا يستدعي `DinstarViewModel` أيُّ ملف في التطبيق (صفر
  مراجع)، ومستهلك `DinstarWebSocketBridge` الوحيد كان هو — أي عنقود
  ميت مغلق.
- **وهو ليس ميتًا فحسب:** كان يستدعي أحد عشر مسارًا تحت
  `/api/admin/dinstar/**` (اكتشاف الأجهزة، تشغيل/إطفاء المنافذ،
  إعادة الضبط، تحويل المكالمات، USSD، طوابير SMS). كلها تتطلب دور
  ADMIN في `SecurityConfig`، فكانت سترد 403 لكل مستخدم عادي. رصده
  حارس المستودع `admin_dashboard/scripts/check-app-roles.mjs`
  وكان يفشل بـ«11 مسار إداري بلا استثناء معلن»؛ بعد الأرشفة يمرّ
  الحارس نظيفًا (تحقّق فعلي بتشغيله).
- **البديل النشط:** إدارة الأسطول مكانها لوحة الإدارة
  (`admin_dashboard`) التي تعمل بحساب ADMIN. أما شاشة الاتصال في
  التطبيق فهي `DinstarPhoneScreen` في `ui/CallsScreens.kt` وتعمل عبر
  `AuthViewModel` ومسارات `/api/pstn/**` المسموحة للمستخدم.
- **ما لم يُؤرشف:** `features/dinstar/DinstarModels.kt` باقٍ لأن
  `YemenOperator` فيه مستعمَل في 19 موضعًا حيًّا (كاشف المشغّل وشارة
  الواجهة). وُثّقت حالة بقيّة نماذجه داخل الملف نفسه.
- **تحقق 2026-08-19:** `npm run check:roles` ✅ · `YemenOperator` سليمة ✅
