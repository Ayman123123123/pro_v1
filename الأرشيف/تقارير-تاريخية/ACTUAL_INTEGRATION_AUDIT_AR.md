# تدقيق الدمج الفعلي في المشروع

**التاريخ:** 2026-08-10  
**الفرع:** `arena/019fe965-pro-v1`  
**النطاق:** ما يدخل البناء والتشغيل فعلًا، وليس ما يوجد في تقارير أو مجلدات مرجعية فقط.

## الخلاصة التنفيذية

- لا توجد تغييرات Git معلّقة بين الفرع الحالي و`origin/main` عند بدء هذه الجولة؛ الفرع كان على نفس commit الدمج.
- المنتج Android الفعلي هو `RED_Ultimate/red-app/` ويُبنى باسم Gradle `:app`.
- المجلدات `RED_Ultimate/android/` و`RED_Ultimate/app-android/` و`RED_Ultimate/app/` **ليست تطبيقات إطلاق**؛ هي مصادر تاريخية/استخراجية خارج `settings.gradle.kts`.
- لذلك فإن وجود ملفات مثل `MainAppNavigation.kt` أو `DinstarAdminScreen.kt` داخل `android/` لا يعني أنها مدمجة في APK الرئيسي.
- لوحة الإدارة كانت تحتوي مدخل JSX قديمًا بينما نسخة TypeScript الأحدث ليست المدخل الفعلي. تم توحيد المدخل على TypeScript وإزالة النسخ المتعارضة.

## ما يدخل البناء الفعلي

| المكوّن | نقطة الدخول الفعلية | الحالة |
|---|---|---|
| Android | `RED_Ultimate/red-app/` عبر `:app` | يدخل البناء |
| Protocol | `RED_Ultimate/shared-proto/` | يدخل بناء Android/backend |
| Backend | `RED_Ultimate/backend-server/` عبر Gradle/Docker | يدخل التشغيل |
| Admin | `admin_dashboard/index.html` → `src/index.tsx` → `src/App.tsx` | موحّد ويدخل build |
| SFU | `media-sfu/server.js` | يدخل صورة الخدمة |
| PSTN | `pstn-asterisk/` + `backend-server/pstn` | مسار مستقل للصوت |

## عناصر كانت موجودة لكنها غير مدمجة في التطبيق الرئيسي

### 1. مصادر Android التاريخية

العناصر التالية موجودة في `RED_Ultimate/android/`، لكنها لا تدخل `:app`:

- `android/app/MainAppNavigation.kt` — شبكة مسارات قديمة وليست شبكة التطبيق الحالي.
- `android/features/dinstar/DinstarAdminScreen.kt`.
- `android/features/dinstar/DinstarSmsScreen.kt`.
- `android/features/dinstar/IncomingPstnCallScreen.kt`.
- `android/features/dinstar/PstnCallScreen.kt`.
- `android/features/dinstar/DinstarModels.kt` و`DinstarViewModel.kt` و`DinstarWebSocketBridge.kt`.
- `android/features/pstn/PstnDialerScreen.kt` و`PstnEngine.kt`.
- النسخ القديمة من `RedMainDashboard.kt` وميزات المكالمات/الإعدادات/الملف الشخصي.

لا ينبغي نسخ هذه الملفات كما هي إلى `red-app`: بعضها يستخدم حزمًا قديمة، بيانات تجريبية، وعقود API مختلفة. الدمج الصحيح يكون بإعادة كتابة الجزء المطلوب داخل الحزمة القانونية `com.red.sovereign` بعد اختبار العقد.

### 2. وظائف غير مكتملة في `red-app` نفسها

هذه ليست مفقودة من Git، لكنها ليست مفعّلة end-to-end بعد:

- **PSTN/DINSTAR للمستخدم:** `AuthViewModel.dialPstn()` ينشئ الطلب ويعرض «بدأ الاتصال»، لكن لا توجد في التطبيق دورة مكالمة PSTN حية كاملة تشمل hangup فعليًا، حالة المنفذ، وربط المكالمة الواردة من Asterisk.
- **المكالمات الواردة عبر DINSTAR:** يوجد منطق أحداث في backend، لكن لا يوجد مسار Android canonical يعرض مكالمة GSM الواردة للمستخدم.
- **إدارة DINSTAR وSMS/USSD:** مكانها القانوني لوحة الإدارة `admin_dashboard` وواجهات `/api/admin/dinstar/**`، وليست شاشة مستخدم داخل APK. النسخ الموجودة في `android/` لا تُعد مدمجة.
- **Spaces:** زر المساحات الصوتية معطّل صراحة في `RedDashboard.kt` ويعرض «قيد الربط».
- **رفع صورة الملف الشخصي:** يوجد placeholder في `RedDashboard.kt` (`Future: Add Avatar upload`) ولا يوجد عقد backend canonical لتحديث avatar الشخصي.
- **Certificate pinning الإنتاجي:** `CertificatePinner.loadPins()` و`savePins()` ما زالا no-op، ولا توجد pins حقيقية مضمّنة/موزعة لبيئة الإنتاج. TLS العادي لا يساوي pinning.
- **خصوصية الحالة:** `UserStatusService.kt` يحتوي TODO للتحقق من كون الطالب جهة اتصال ولإحضار اسم المستخدم؛ مسار CONTACTS ليس مكتملًا end-to-end.
- **إدارة أجهزة العرض القديمة:** `features/devices/DevicesScreen.kt` يحتوي قائمة ثابتة تجريبية وغير موصول من `RedDashboard`. المسار الفعلي لإدارة الأجهزة هو `settings/DeviceSettingsViewModel.kt` و`YounesSettingsSheet` ويقرأ `/api/devices`.

### 3. نقاط backend تحتاج إكمالًا قبل وصفها بأنها مكتملة

- `PstnCallController.hangup()` يحرر عداد المنفذ فقط؛ لا ينفذ hangup موثقًا في Asterisk ولا يتحقق حاليًا من ملكية `callId` للمستخدم الطالب.
- `PstnCallController.status()` يعيد حالة ثابتة (`active: true`) بدل حالة حية من مدير المكالمات.
- `DinstarHardwareService` يستخدم trust-all للشهادة وhostname verifier مفتوحًا على شبكة الإدارة الخاصة؛ هذا مقصود للتوافق مع شهادة DINSTAR الذاتية، لكنه ليس وضع إنتاج آمنًا دون تقييد شبكة الإدارة أو pin موثق.
- `DinstarLoadBalancer` يحسب WFQ، لكن يجب التأكد من تمرير `slot` فعليًا إلى originate/Asterisk؛ اختيار المنفذ وحده لا يثبت استخدامه في المكالمة.

## ما تم تصحيحه في هذه الجولة

1. جعل `admin_dashboard/index.html` يشغّل `src/index.tsx` بدل المدخل JSX القديم.
2. إزالة اعتماد `react-router-dom` غير الموجود من المدخل TypeScript.
3. توحيد عقد `Login.tsx` مع `App.tsx` بدل وجود عقدين متعارضين.
4. تحويل صفحة Diagnostics إلى `Diagnostics.tsx` مع نتائج probes حقيقية، وحذف صفحات JSX القديمة غير المربوطة.
5. إضافة script مفقود: `npm run check:api`.
6. إضافة بحث محلي فعلي في الرسائل المفكوكة على الجهاز، وربطه بزر البحث في `RedDashboard`; لا يغادر نص البحث الجهاز.
7. حذف النسخة الاحتياطية المربكة `red-app/.../RedDashboard.kt.bak`.
8. إكمال endpoints المطلوبة في `scripts/mock_backend.py` حتى يطابق smoke test العقد الموثق.
9. جعل `scripts/check-all.sh` يعمل على جهاز لا يحتوي PyYAML/Docker مع إبقاء `docker compose config` هو الفحص الحاسم في CI.

## الأدلة التي تم تشغيلها

- `npm run build` داخل `RED_Ultimate/admin_dashboard` — **نجح**.
- `npm run check:api` — **47 استدعاء، العقد سليم**.
- `RED_Ultimate/scripts/check-all.sh` — **11 نجحت، 0 فشلت**.
- `python3 scripts/check-schema-consistency.py` — **سليم**.
- `node --check media-sfu/server.js` — **نجح**.
- `python3 -m py_compile scripts/mock_backend.py` — **نجح**.
- بناء/اختبارات Kotlin لم تُشغّل محليًا لأن بيئة العامل لا تحتوي Java أو Android SDK؛ يجب اعتماد بوابة CI ذات JDK 21 وAndroid SDK قبل اعتبار APK مثبتًا.

## قاعدة الدمج الصحيحة من الآن

لا يُعتبر الملف «مدمجًا فعليًا» لمجرد وجوده أو ذكره في تقرير. يجب أن:

1. يكون ضمن build graph canonical.
2. يكون مستدعى من نقطة دخول فعلية.
3. يطابق عقد API/protocol الحالي.
4. يمر build وunit tests وAPI contract.
5. يمر اختبار runtime/جهاز عند الحاجة، خصوصًا WebRTC وKeystore وDINSTAR.