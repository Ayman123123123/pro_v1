# تقرير تفعيل الميزات واستعادة بوابات المهام

**التاريخ:** 2026-08-11

## لماذا ظهر أن المهام نُقّصت؟

لم تُحذف اختبارات QA أو fast-lint أو build-logic. التغيير السابق جعل composite build يُحمّل فقط عندما تكون المهمة المطلوبة واحدة من مهام الجودة، حتى لا تدخل أدوات Signal القديمة في classpath الخاص بـ`assembleDebug` وتسبب تعارض Kotlin/AGP.

لإزالة أي غموض أضيفت بوابات جذرية واضحة:

| المهمة | ما تنفذه |
|---|---|
| `androidCheck` | unit tests + Android lint + assembleDebug |
| `backendCheck` | اختبارات Spring عبر wrapper المستقل المثبت |
| `ci` | Android check + build logic + stopship + fast-lint |
| `qa` | Android check + lint الكامل + build logic |
| `qualityGate` | CI gate + backendCheck؛ بوابة الإصدار الشاملة |
| `qaRemote` / `ciRemote` | البوابات السابقة مع screenshot validation |
| `format` | تنسيق التطبيق وbuild-logic |

عند تشغيل `assembleDebug` وحده لا تُحمّل الأدوات الثقيلة، وعند تشغيل `qualityGate` تُحمّل جميعها تلقائياً. هذا **lazy task graph** وليس حذفاً للمهام.

## الميزات التي كانت موجودة شكلياً وتم تفعيلها فعلياً

### 1. الاستكشاف والبث والمساحات

كانت شاشة Explore تعرض قائمتين ثابتتين وأزرار دخول بلا أي إجراء. الآن:

- `GET /api/livestream/public` يعرض البثوث الحية الحقيقية.
- `POST /api/livestream/create` ينشئ بثاً مرتبطاً بالمستخدم المصادق.
- `POST /api/livestream/{id}/join` يتحقق من السماح ويحدّث عدد المشاهدين.
- `GET /api/conference/public?isSpace=true` يعرض المساحات الحقيقية.
- إنشاء ودخول المساحة يمران عبر backend قبل تشغيل WebRTC service.
- البحث مؤجل 300ms لمنع الطلبات الزائدة.
- أزيلت fixtures الوهمية والـcallbacks الفارغة.
- اكتشاف البث من مركز المكالمات يفتح شاشة Explore الحقيقية بدلاً من اعتبار عبارة البحث stream ID.

### 2. دورة المجتمعات

زر «منضم» كان disabled رغم وجود leave API. أصبح:

- إنشاء مجتمع.
- قائمة وبحث.
- انضمام.
- مغادرة فعلية.
- حذف للمالك فقط.
- state حقيقية في خادم التطوير واختبار memberCount ودورة join/leave.

### 3. جودة المكالمات

`RedQualityManager` كان orphan رغم وجوده. أصبح موصولاً بـWebRTC:

- اختيار جودة الالتقاط الأولية حسب Wi-Fi/Ethernet/4G/3G وإعداد Data Saver.
- bitrate/simulcast الأولي لا يبدأ دائماً HD.
- adaptive bitrate لا يعيد تشغيل الكاميرا إذا أوقفها المستخدم يدوياً.
- auto-download يستخدم مدير الجودة نفسه بدلاً من منطق ثانٍ مكرر.

### 4. WebSocket والاستقرار

عملاء signaling الثلاثة كانوا يستخدمون HTTP client بمهلة قراءة 20 ثانية. أصبحوا يستخدمون client مخصصاً:

- read timeout غير محدود للاتصال الحي.
- ping كل 25 ثانية.
- SPKI policy نفسها.
- retryOnConnectionFailure.

### 5. TLS certificate pinning

الميزة كانت اسمية: load/save فارغتان، وحساب pin كان يجزئ كامل الشهادة بينما OkHttp يتطلب SHA-256 للـSPKI، كما أن عدة pins كانت تُدمج في String واحدة غير صالحة. تم:

- توليد `sha256/SPKI` الصحيح.
- التحقق من digest بطول 32 بايت.
- حفظ السياسة مشفرة عبر Android Keystore/SecureStore.
- تحميلها قبل أول OkHttp/WebSocket client.
- دعم current + backup pins عبر `RED_TLS_PINS`.
- تطبيق pins على HTTP وWebSocket والرفع والتنزيل.
- cache واحد فقط لمنع فتح عدة OkHttp Caches على المجلد نفسه.

صيغة البناء:

```text
-PRED_TLS_PINS='api.example.com=sha256/current|sha256/backup'
```

الـpins ليست أسراراً؛ يجب أن تتضمن دائماً مفتاحاً احتياطياً لتدوير الشهادة بلا قطع الخدمة.

### 6. القصص

تم إصلاح أخطاء تصريف ووظائف:

- إضافة Text وVoice لكل `when` المغلقة الخاصة بـStoryViewerState.
- تمرير callbacks للتفاعل والرد بدلاً من مرجع ViewModel غير موجود في scope.
- رد الحالة يستخدم conversation ID صحيحاً لا String فارغاً.
- Voice story يشغّل URI المحلي المفكوك لا media key البعيد.
- التقدم التلقائي يدعم الحالة النصية.

### 7. واجهة الدردشة

- زر الدردشة العائم كان يشير إلى `showDirectory` داخل دالة أخرى؛ أصبح يفتح Contacts فعلياً.
- `VoiceMessage` أصبحت `@Composable` كما تتطلب APIs التي تستدعيها.
- استدعاء Communities أصبح يمرر TokenStore المطلوب.
- imports المفقودة لأيقونات Compose أضيفت وفاحص آلي يمنع عودتها.
- مشاركة المنشور أصبحت تفتح Android Sharesheet بدلاً من زر disabled.

### 8. إدارة جلسات البث في backend

- إنشاء stream يقبل ID محدداً من عميل WebRTC مع validation.
- لا يمكن لمستخدم الاستيلاء على stream ID يملكه broadcaster آخر.
- join يستخدم هوية JWT ويحدّث viewer count.
- أضيف leave وstop مع تحقق أن من يوقف البث هو صاحبه.
- Conference join يحدّث participant count، وأضيف leave/close مع تحقق المضيف.
- `/api/live/admin/**` أصبح ADMIN-only.
- viewerId لم يعد يأتي من query قابلة للانتحال.

## الميزات التي لم تُفعّل قسراً ولماذا

1. **CallCaptionManager:** Android SpeechRecognizer لا يضمن المعالجة المحلية وقد ينافس WebRTC على الميكروفون. تفعيله قسراً قد يقطع صوت المكالمة أو يرسل الصوت لخدمة النظام. يحتاج AudioSink + نموذج on-device حقيقي قبل عرضه كميزة خصوصية.
2. **النسخ السحابي لمفاتيح الهوية:** بقاؤه معطلاً قرار أمان؛ النسخ المحلي المشفر موجود. لا يُرفع private identity key إلى cloud لمجرد إظهار زر فعال.
3. **AGP 9.3/Kotlin 2.4:** مؤجلان حتى تجديد dependency SHA-256 وتشغيل CI متصل، وليس لأن المهام حُذفت.

## الاختبارات التنفيذية

- خادم الإدارة: 25/25.
- التكامل Android ↔ server ↔ dashboard: 36/36، ويشمل الآن بثاً ومساحة ومجتمعاً كاملاً.
- Asterisk fleet: ناجح.
- Android integrity: 89 فحصاً ناجحاً قبل إضافة هذه الجولة إلى الحراس النهائية.
- Version catalog: 190 alias سليمة.
- Kotlin static: 255 فحصاً.
- Infrastructure: 74/74.
- Admin TypeScript + Vite production: ناجح، 5446 module.

لا يُعلن نجاح APK النهائي حتى يتوفر JDK/Android SDK أو تعمل GitHub workflows ويُنفذ D8/R8/AAPT الحقيقي.
