# تقرير تثبيت والتحقق من تكامل DINSTAR / PSTN / SMS

**التاريخ:** 21 أغسطس 2026  
**النطاق:** مشروع RED Ultimate المحلي على Windows، وخادم Spring Boot، وحاوية Asterisk، وتطبيق Android.  
**حالة التقرير:** تحقق محلي من البناء والاختبارات والخدمات التشغيلية؛ لا يُعد بديلاً عن مكالمة أو رسالة مدفوعة إلى رقم حقيقي.

## الملخص التنفيذي

تمت استعادة بوابة القبول الخاصة بتطبيق Android بنجاح، ثم تمرير اختبارات الوحدة كاملة. كما تم تثبيت عقد أحداث المكالمة الواردة في Asterisk وتفعيله في الحاوية الحية، وإصلاح انجراف خدمات Docker الذي كان يجعل الخادم يعيد التشغيل بسبب غياب اسم `db-postgres` وتعطل Redis. في نهاية التحقق أصبحت PostgreSQL وRedis والخادم وAsterisk بحالة صحية، واتصل الخادم بواجهة AMI وسجل مستمع أحداث DINSTAR.

تؤكد حالة Asterisk الحالية أن مسارات SIP الخاصة بمنافذ DINSTAR من `0` إلى `7`، إضافة إلى `dinstar-gateway` والاسم المشتق من العنوان، في حالة **Avail**. أما `red-webrtc-client` فهو **Unavailable** حالياً لأن تطبيق Android ليس مسجلاً كعميل SIP نشط أثناء الفحص؛ هذه حالة متوقعة وليست عطل dialplan. خادم HTTP في Asterisk مفعّل ويعرض مسار WebSocket `/ws`، ولذلك لا ينبغي تنفيذ استنتاج بأن منفذ WebSocket أو Asterisk معطل لمجرد غياب contact لتطبيق غير مفتوح.

> **الاستنتاج التشغيلي:** الجهاز UC2000-VE متصل ويستجيب لإشارات SIP من Asterisk، ولا يوجد دليل من هذا التحقق على الحاجة إلى استبداله. بقيت مصادقة HTTP API اليدوية بحاجة تحقق: طلب `get_port_info` المباشر عاد بـ HTTP 401، لذلك لا ينبغي إرسال SMS فعلي أو تغيير كلمة مرور الجهاز قبل تأكيد بيانات اعتماد API/دور المستخدم من واجهة الجهاز.

| بوابة القبول | النتيجة | الدليل |
|---|---:|---|
| تجميع Android `:app:compileDebugKotlin` | ناجح | `BUILD SUCCESSFUL` خلال 5 دقائق و59 ثانية |
| اختبارات وحدة Android `:app:testDebugUnitTest` | ناجح | 51 حزمة، 224 اختباراً، 0 failures، 0 errors |
| اختبارات غلاف PSTN WebSocket | ناجح | 3 اختبارات: مكالمة واردة مغلفة، SMS واردة مغلفة، توافق الرسالة المسطحة القديم |
| اختبارات الخادم | ناجح سابقاً | `BUILD SUCCESSFUL` في الجولة السابقة؛ لم يتغير منطق خادم Kotlin بعد ذلك، باستثناء تنظيف نهاية الأسطر |
| صحة PostgreSQL / Redis / backend / Asterisk | ناجح | جميع الحاويات الصحية عند آخر تحقق |
| AMI ومستمع DINSTAR | ناجح | الخادم سجّل `DinstarEventListener` واتصال AMI مع `red_admin@pstn-gateway` |
| SIP DINSTAR | ناجح | contacts للمنافذ 0–7 والجذع DINSTAR بحالة `Avail` |
| SIP/WebRTC للتطبيق | مشروط | البنية جاهزة، لكن لا يوجد تسجيل Android نشط أثناء التحقق |
| مكالمة PSTN أو SMS إلى رقم حقيقي | مؤجل | يحتاج رقماً اختبارياً وموافقة لتجنب تكلفة أو اتصال غير مقصود |

## التغييرات المثبتة

### عقد أحداث المكالمة الواردة

جرى تفعيل `extensions.conf` داخل حاوية Asterisk ثم إعادة تحميل `dialplan` وPJSIP. السياق `from-dinstar` يعرض الآن حقول UserEvent القياسية التي تستطيع مكتبة Asterisk-Java قراءتها:

```asterisk
UserEvent(DinstarIncomingCall,
  CallerIDNum:${CALLERID(num)},
  Channel:${CHANNEL(name)},
  Exten:${EXTEN},
  Context:from-dinstar)
```

يشمل ذلك مسار الامتداد `s` للمكالمات التي لا تحمل امتداداً محدداً. هذه الحقول تتطابق مع إصلاح `DinstarEventListener` الذي يبني سياق المكالمة من بيانات AMI القياسية ويربط channel بـ `callId`.

### Android: غلاف PSTN وسير قبول المكالمة

تم تثبيت استدعاء `decodeFromJsonElement` في `PstnEventEnvelopeCodec` عبر استيراد الامتداد الصحيح وتحديد النوع `PstnWsEnvelope` صراحة. بذلك يقرأ التطبيق الغلاف القياسي للخادم `{type, data}`، مع الاحتفاظ بالتوافق المؤقت للرسائل المسطحة أثناء التدرج. أثبتت اختبارات الوحدة الثلاثة أن مكالمة واردة وSMS واردة تمران عبر الغلاف بنجاح.

تشمل التغييرات السابقة المستمرة في هذه الجولة أيضاً التخزين المؤقت لأوامر WebSocket قبل فتح القناة، ومنسق استقبال مركزي يبدأ بعد المصادقة، وتحضير SIP قبل إرسال `PSTN_ACCEPT`، وإرسال `200 OK` مع SDP لقبول SIP الوارد بدلاً من ACK غير الصحيح.

### تصحيحات بناء Android خارج مسار PSTN المباشر

ظهر أثناء إعادة البناء أن تغييرات محلية في واجهة المجموعات كانت تمنع Kotlin من الوصول إلى مرحلة الاختبار. تمت معالجة هذه العوائق بأقل تعديل ممكن:

| الملف | التصحيح |
|---|---|
| `GroupViewModel.kt` | إعادة كتابة خصائص الدعوة المفوضة بصيغة Kotlin القياسية، بما يعرّف `latestInviteGroupId` فعلياً |
| `SovereignGroupSystem.kt` | الحفاظ على زر تحديث طلبات الانضمام كـ `confirmButton` صالح لـ `AlertDialog` |
| `RedDashboard.kt` | التقاط `groupConversationId` في `val` محلي قبل حفظ المسودة في coroutine، لتفادي smart cast على حالة Compose مفوضة |

لم يُعد ضبط أو حذف التغيير المحلي الكبير في `RedDashboard.kt`. التصحيحات أعلاه كانت ضرورية لعودة بناء Android وللاختبارات، وليست إعادة تصميم للواجهة.

### نظافة `PstnManager.kt`

كان ملف `PstnManager.kt` يحتوي خليطاً من نهايات CRLF وLF، فكان `git diff --check` يفسر `CR` كمسافات لاحقة في أسطر صحيحة. أُخذت نسخة احتياطية ثم وُحّدت نهايات الملف إلى LF بترميز UTF-8 بلا BOM. فحص الملف الموجّه انتهى الآن بـ `EXIT_CODE=0`.

## التحقق الحي للخدمات

### Asterisk وDINSTAR

التحقق الحي بعد استقرار الحاويات أثبت وجود سياق `from-dinstar` بالـ UserEvent الجديد. كما أظهر `pjsip show contacts` الاستجابات التالية:

| هدف SIP | النتيجة |
|---|---|
| مستخدمو منافذ DINSTAR `0` إلى `7` | `Avail` |
| `dinstar-gateway` عند `192.168.11.2:5062` | `Avail` |
| `dinstar-gw-192-168-11-2` | `Avail` |
| `red-webrtc-client` | لا contact نشط؛ متوقع حتى يسجل التطبيق |

خادم HTTP المدمج في Asterisk مفعّل على `0.0.0.0:8088` ويعرض `/ws`. مولد الإعدادات وقت التشغيل يعرّف نقل WebSocket في `pjsip.conf`، ويقبل تسجيل `red-webrtc-client` ديناميكياً. لذلك يشير غياب contact للتطبيق إلى أن التطبيق لم يفتح/يسجل جلسة SIP بعد، وليس إلى فقدان تعريف endpoint.

### استعادة خدمات Docker

أثناء الفحص لوحظ أن `red-backend` يعيد التشغيل لأن `db-postgres` لم يكن موجوداً على شبكة مشروع `red-sovereign_red-net`. سبب الفشل الموثق كان `UnknownHostException: db-postgres`. كذلك كانت حاوية `red-cache` متوقفة، ما كان يجعل `/health` يرجع `DEGRADED` مع `REDIS_UNAVAILABLE`.

جرى تشغيل خدمتي `db-postgres` و`cache-redis` فقط عبر ملف Compose الرسمي والـ LAN overlay، من دون حذف volumes أو إعادة تهيئة البيانات. عقب ذلك أصبحت `red-db-sql` و`red-cache` بحالة healthy، واستقر `red-backend` بلا إعادة تشغيل، وعاد `/health` إلى `status: UP` مع PostgreSQL وMongoDB وRedis وMinIO في حالة UP.

### ملاحظة API الجهاز

نجح الوصول الشبكي إلى HTTPS الخاص بالجهاز عند `192.168.11.2:443`. لكن طلب قراءة يدوي إلى `/api/get_port_info` باستخدام بيانات `.env` أعاد HTTP 401. هذا لا يتعارض مع إثبات SIP: قناة الإدارة HTTP وSIP آليتان مختلفتان. لا تُغيّر كلمة المرور أو إعداد trunk آلياً؛ راجع في واجهة UC2000 حساب HTTP API وامتيازاته وفعّل فقط البيانات الصحيحة المعتمدة لدى الفريق.

توفر UC2000-VE وفق DINSTAR بوابة GSM/3G/4G متعددة القنوات، مع SIP وSMS وHTTP API وإدارة عبر HTTP/HTTPS وSIP trunking، لذلك يظل مسار المشروع الصحيح هو **Backend → Asterisk → DINSTAR** للصوت وHTTP API للـ SMS، لا إنشاء endpoint صوتي غير موثق على الجهاز.[1]

## النسخ الاحتياطية والتراجع

قبل استبدال dialplan الحي أُنشئت نسخة محلية مؤرخة ضمن:

```text
pstn-asterisk/backups/20260821-060848/extensions.conf.pre-reload
pstn-asterisk/backups/20260821-060848/extensions.conf.project-candidate
pstn-asterisk/backups/20260821-060848/PstnManager.pre-line-ending-normalization.kt
```

هذه الملفات مستثناة من Git حسب `.gitignore`. إذا ظهر خلل مباشر بسبب dialplan، يكون التراجع المحدود هو نسخ `extensions.conf.pre-reload` إلى `/etc/asterisk/extensions.conf` داخل `red-pstn-gateway` ثم تنفيذ `asterisk -rx "dialplan reload"`. لم يلزم تنفيذ التراجع لأن السياق أعيد تحميله بنجاح وأصبحت contacts DINSTAR متاحة.

## ما بقي للاختبار الحي

لا ينبغي اختراع نجاح لمكالمة أو SMS مدفوعة. الاختبار التالي يجب أن يتم على تطبيق Android يعمل في المقدمة وبرقم اختبار مُصرح به:

1. سجّل الدخول إلى التطبيق مع تشغيله في المقدمة، ثم راقب `pjsip show endpoint red-webrtc-client` حتى يظهر contact. هذا يثبت أن مسار Android → WS → SIP registration يعمل.
2. نفّذ مكالمة PSTN صادرة إلى رقم اختبار مملوك أو موافق عليه. تحقق من إنشاء `callId` ومن اختيار المنفذ وظهور channel في Asterisk، ثم من الصوت والإنهاء وسجل المكالمة.
3. اتصل من شريحة اختبار واردة إلى أحد منافذ UC2000. تحقق من `DinstarIncomingCall` ثم WebSocket المغلف ووصول شاشة المكالمة. اقبل المكالمة للتحقق من إعداد SIP و`200 OK + SDP`، ثم ارفض/أنه للتأكد من مسار hangup.
4. بعد تصحيح 401 الخاص بواجهة API، أرسل SMS واحداً إلى رقم اختبار فقط. تحقق من حالة المهمة، ثم أرسل رد SMS وارد وتأكد من ظهوره في محادثة SMS.
5. لا يختبر الرنين عندما يكون التطبيق مقتولاً بالكامل في هذه الجولة؛ إشعارات FCM ما زالت مؤجلة، لذلك اختبار الاستقبال هنا يتطلب أن يكون التطبيق في المقدمة أو قابلاً للتشغيل.

## المراجع

[1]: https://www.dinstar.com/GSM-3G-LTE-voip-gateway/4-8-ports/ "DINSTAR UC2000-VE: GSM/3G/4G VoIP Gateway"
[2]: https://www.dinstar.com/blog/technical-guide/configuration-video-of-UC2000-with-Elastix/ "DINSTAR technical guide: UC2000 configuration with an Asterisk-class PBX"

> تشير DINSTAR إلى أن نظرية إعداد UC2000 مع Elastix تنطبق كذلك على Asterisk وFreeSWITCH و3CX ومنصات SIP المماثلة.[2]

## الملفات الداعمة

توجد سجلات التحقق المحلية في `build-logs/`، ومنها `pstn-runtime-validation-20260821.txt` و`asterisk-post-reload-20260821.txt` ونتائج Gradle. هذه سجلات تشغيل محلية وليست جزءاً من تسليم Git.
