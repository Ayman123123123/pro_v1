# 🛡️ إصلاح الأعطال المؤكّدة — فحص عميق وتحقّق

> **التاريخ:** 2026-08-11
> **الأساس:** تحقّق مستقل من تقرير تدقيق عميق — أُصلح ما تأكّد فعلاً بعد فحص كل ادّعاء
> **الحالة:** 12 عيبًا مؤكّداً أُصلحت + حارس عقد شامل (23 فحص) لمنع الارتداد

---

## منهجية: تحقّق قبل الإصلاح

التقرير اعترف بنفسه بأخطاء سابقة (poll_votes كان موجودًا، READ_PHONE_STATE كان مستعملًا). لذا **تحقّقت من كل ادّعاء بشكل مستقل** قبل الإصلاح، واكتشفت أن التقرير **أخطأ في اثنين**:

| ادّعاء التقرير | الحقيقة بعد الفحص |
|---|---|
| `ui/StoriesScreen.kt` ميتة (176 سطر) | ❌ **خطأ** — تعرّف `StoryFullscreen` المستخدمة في `RedDashboard:554` |
| `settings/SettingsScreen.kt` ميتة (308 سطر) | ❌ **خطأ** — تعرّف `YounesSettingsSheet` المستخدمة في `RedDashboard:388` |

**الشاشات الميتة فعلاً:** `media/EventsScreen.kt` (784) + `media/PollsScreen.kt` (766) = **1,550 سطر** (لا 2,034).

---

## 🔴 عطلتا التصريف الحرجتان (التطبيق لا يُصرَّف)

### ١. الدوال الثلاث المفقودة في `YounesCallService`
`PhoneStateReceiver` يستدعي `silenceRinger()` / `holdActiveCall()` / `resumeRinger()` كدوال static — **لا واحدة منها معرّفة**. ⇒ `Unresolved reference` ×3 ⇒ لا APK.

**الإصلاح:**
- أضفت `ACTION_SILENCE_RINGER` / `ACTION_HOLD_ACTIVE` / `ACTION_RESUME_RINGER` كثوابت
- أضفت دوال companion: `silenceRinger(context)` / `holdActiveCall(context)` / `resumeRinger(context)` بنمط `startService` (لا `startForegroundService` — الخدمة تعمل مسبقًا، وforeground يرمي `ForegroundServiceStartNotAllowedException` على Android 12+)
- عالجت الأفعال في `onStartCommand` → `stopRingtone()` / `holdCall()` / إعادة الرنة إن كانت مكالمة واردة
- حدّثت `PhoneStateReceiver` ليمرّر `context`

### ٢. `ApiResult.Success` arity
`data class Success<T>(val code: Int, val value: T)` يأخذ **وسيطين**، لكن `AuthorizedApiClient.kt` كان يستعمله بوسيط واحد في **4 مواضع** ⇒ عطل تصريف ثانٍ.

**الإصلاح:** صحّحت الـ 4 مواضع `(code, value)` ومسحتُ المشروع كله — لم يبقَ نداء بوسيط واحد.

---

## 🔴 الأمان — سباق التوكن يطرد المستخدم من كل أجهزته

`AuthorizedApiClient.executeResponseWithRefresh` كان **بلا حارس تزامن** + يستعمل `runBlocking` على `Dispatchers.IO`. الخادم يُبطل **عائلة الجلسة كاملة** عند إعادة استخدام توكن (`RefreshTokenService:42-45`).

**السيناريو المؤكّد:** 3 طلبات متوازية تتلقى 401 ⇒ كلٌّ يُرسل نفس توكن التحديث ⇒ الأول ينجح ويُبطلها ⇒ الباقي يستعمل مُبطلًا ⇒ الخادم يعتبره سرقة ⇒ **طرد كل الأجهزة**.

**الإصلاح:**
- `Mutex` مشترك على مستوى companion (عبر كائنات العميل المختلفة)
- **فحص مزدوج:** بعد اكتساب القفل، إن تغيّر `tokens.accessToken` (طلب متوازٍ جدّده) نُعيد المحاولة بالجديد دون استدعاء refresh
- حذف `runBlocking` → استدعاء `suspend` مباشر (يمنع جمود خيوط IO)

---

## 🔴 ميزات لا تعمل

### ٣. زر إلغاء الحظر — 404 دائمًا
التطبيق: `POST /api/contacts/{redId}/unblock` · الخادم: `DELETE /api/contacts/{redId}/block`. **الفعل والمسار كلاهما مختلف.**

**الإصلاح:** وحّدت التطبيق ليرسل `DELETE /api/contacts/{redId}/block` (مطابق للخادم).

### ٤. تفاصيل فعالية — 405
التطبيق يطلب `GET /api/admin/content/events/{eventId}`؛ الخادم يعرّف `DELETE` فقط.

**الإصلاح:** أضفت `@GetMapping("/events/{eventId}")` + `ContentService.getEvent()` + استثناء `SecurityConfig` للمستخدم العادي.

### ٥. التصويت يكذب على المستخدم
`ContentService.vote()` had 3 عودات صامتة + `success:true` دائمًا + لا تحقّق optionId + تحويل غير آمن ⇒ 500.

**الإصلاح:**
- أخطاء صريحة: `POLL_NOT_FOUND` (404) / `POLL_NOT_ACTIVE` / `POLL_ENDED` / `ALREADY_VOTED` (409) / `INVALID_OPTION` (400)
- **تحقّق أن optionId ينتمي للاستطلاع** (يمنع إفساد النتائج بخيار من استطلاع آخر)
- تحويل آمن `as? List<*>` + معالجة 400 بدل 500

---

## 🟠 ميزات تعمل بشكل خاطئ

### ٦. تعارض قناة `red_calls`
`SovereignNotificationRouter` ينشئها بـ `IMPORTANCE_MAX` بينما البقية `IMPORTANCE_HIGH` ⇒ أندرويد يثبّت الأولى ويتجاهل الباقي (غير حتمي).

**الإصلاح:** وحّدت الراوتر لـ `IMPORTANCE_HIGH`.

### ٧. `CallTelemetry.flush` — تسريب نطاق
`CoroutineScope(Dispatchers.IO)` محلّي لكل نداء (يتيم لا يموت) بلا `SupervisorJob`.

**الإصلاح:** نطاق مشترك بعمر المفرد + `SupervisorJob()` (فشل إرسال لا يُلغي البقية).

---

## 🟡 بنيوي

### ٨. ProGuard سيُحطّم الإصدار
كان **3 أسطر** فقط. مع `isMinifyEnabled=true`، الإصدار ينهار عند أول استعمال (لا عند التصريف).

**الإصلاح:** قواعد كاملة (11 قسم): `@Serializable` (82 صنفًا) · WebRTC JNI · Room · OkHttp · SQLCipher · libsignal · Protobuf · Coil/Lottie · Kotlin metadata.

### ٩. 13 تحويلًا غير آمن ⇒ 500 بدل 400
`body["title"] as String` ⇒ `ClassCastException` على جسم مشوّه.

**الإصلاح:** كل التحويلات في `ContentController` + `AdminV2Controller` أصبحت `as? String ?: return 400` + رسالة خطأ نظيفة.

### ١٠. N+1 في حذف الاستطلاع
`findByPollId().forEach { delete(it) }` + أصوات يتيمة غير محذوفة.

**الإصلاح:** `@Modifying @Query("DELETE ... WHERE pollId = :pollId")` (حذف مجمّع) + حذف الأصوات لمنع اليتم.

---

## 🛡️ حارس العقد الشامل (جديد)

`scripts/check-android-integrity.py` — **23 فحصًا** يغطّي كل العيوب أعلاه، مُدمج في `check-all.sh` و CI (`build-red.yml`). **أُثبتت فعاليته بحقن عطب** (أُعيد unblock لـ POST، التقطه الفاحص، عاد أخضر بعد الإصلاح).

يفحص: عقد API · عطل التصريف · arity · سباق التوكن · التحويلات غير الآمنة · ProGuard · votePoll · events GET · N+1 · قناة red_calls · تسريب CallTelemetry.

---

## ✅ ما تأكّد سليمًا (لم ألمسه)

- اللوحة ↔ الخادم: 89/89 مسار مطابق
- ترحيلات Flyway: V1→V25 بلا فجوة
- التشفير الجماعي: `GroupCipher` + `SenderKeyDistribution` مكتمل
- تجديد المفاتيح: `PreKeyPoolManager` يعمل ويُستدعى
- الأسرار: صفر مكشوف · `ddl-auto: validate`
- المفاتيح الخاصة لا تغادر الجهاز

---

## 📊 الإحصائيات

- **14 ملفًا** معدّلة + فاحص جديد
- +238/−48 سطر
- 12 عيبًا مؤكّداً أُصلح
- 23 فحص حارس أخضر
- فحص schema + catalog: ✅ سليم

---

## ⏳ ما لم يُنفّذ (قرارات منتج)

| البند | السبب |
|---|---|
| وصل `EventsScreen`/`PollsScreen` للتنقّل | قرار منتج: ميزة مستخدم أم إدارة؟ أضفت endpoints + استثناءات SecurityConfig جاهزة |
| حذف `SovereignNotificationRouter` | خدمة زائدة لكن حذف Service يتطلب manifest + caller — أصلحت تعارض القناة فقط |
| `MinioUploader` ميت | كود ميت غير ضار — يُحذف بقرار |
| `app/` (Signal AGPLv3) | قرار ترخيص خاص بك |

## ⚠️ حدّ التحقّق
لا JDK/Android SDK هنا ⇒ لا تصريف محلي. الإصلاحات مبنية على مسح دقيق + فاحص آلي. الـ CI (`build-red.yml` الآن يبدأ بفاحص التكامل) سيُصدّر ويتحقّق.
