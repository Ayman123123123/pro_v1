# ملاحظات البحث الرسمي: DINSTAR UC2000-VE

**التاريخ:** 21 أغسطس 2026

## نتائج موثقة

توضح DINSTAR في دليل تكامل UC2000 مع Elastix أن Elastix خادم SIP من الفئة نفسها التي تضم Asterisk وFreeSWITCH و3CX وIssabel، وأن نظرية الإعداد متشابهة. لذلك فإن ربط UC2000-VE بـ Asterisk عبر SIP trunk وتسجيل منافذ البوابة لدى خادم SIP يظل مساراً صحيحاً ومدعوماً.[1]

تؤكد صفحة أدوات DINSTAR أن **GSM Gateway HTTP API** مخصصة لتمكين خادم SMS من التواصل مع عدد كبير من البوابات لإرسال SMS واستقبالها، كما تذكر SMSBox كأداة عرض تجريبي للإرسال والاستقبال وUSSD عبر بوابات GSM/WCDMA/LTE.[2]

| الاستنتاج العملي | أثره على RED Ultimate |
|---|---|
| الصوت يمر عبر SIP trunk / Asterisk | يجب إبقاء منطق المكالمات في Backend → Asterisk → DINSTAR، والتحقق من التسجيل والـ dialplan قبل محاولة أي API صوتي غير موثق |
| الرسائل وUSSD تعتمد HTTP API | لا نرسل SMS حي قبل قبول مصادقة API وقراءة حالة المنفذ بنجاح |
| السجل والإشارة ليسا اختباراً كافياً وحدهما | يلزم اختبار موجّه إلى رقم اختبار مصرح به لاحقاً للتحقق من المكالمة والصوت والـ SMS end-to-end |

## المصادر

[1]: https://www.dinstar.com/blog/technical-guide/configuration-video-of-UC2000-with-Elastix/ "Configuration Video of DINSTAR UC2000 VoIP GSM Gateway with Elastix"
[2]: https://www.dinstar.com/tools/ "DINSTAR Support Center — Tools"

## تحقق Asterisk الرسمي

يوضح توثيق Asterisk أن الـ endpoint لا يمكن الاتصال به بلا AOR مرتبط، وأن AOR يخزن contact ثابتاً أو contact ديناميكياً يأتي عبر SIP REGISTER. كما يوضح أن قسم `identify` يسمح بمطابقة SIP الوارد عبر عنوان IP بدلاً من user في ترويسة `From`.[3]

يوضح دليل WebRTC الرسمي أن تشغيل عميل WebRTC يتطلب نقل PJSIP عبر WebSocket، وكائنات Endpoint وAOR وAuthentication، إضافة إلى وحدات WebSocket وPJSIP المناسبة. ويوصي عادة باستعمال WebSocket مؤمّن TLS في المتصفح، مع إمكان إنهاء TLS عند الحافة إذا صُمم المسار لذلك.[4]

| النتيجة | المقارنة مع RED Ultimate |
|---|---|
| DINSTAR contacts الثابتة/المسجلة تظهر `Avail` | مطابق: سجلات المنافذ 0–7 وجذع `dinstar-gateway` أصبحت متاحة |
| لا contact لـ `red-webrtc-client` بلا REGISTER | مطابق: لا يعد ذلك عطلاً ما دام التطبيق غير مفتوح أو غير مسجل |
| `identify` بالـ IP مناسب لقبول SIP الوارد من البوابة | مطابق: مولد إعداد Asterisk يربط DINSTAR بعنوان `192.168.11.2` |
| WS غير المشفّر مناسب فقط لمسار محلي مضبوط | يحتاج اختبار Android محلي؛ للإطلاق العام يجب استعمال WSS موثوق عند الحافة |

[3]: https://docs.asterisk.org/Configuration/Channel-Drivers/SIP/Configuring-res_pjsip/PJSIP-Configuration-Sections-and-Relationships/ "Asterisk: PJSIP Configuration Sections and Relationships"
[4]: https://docs.asterisk.org/Configuration/WebRTC/Configuring-Asterisk-for-WebRTC-Clients/ "Asterisk: Configuring Asterisk for WebRTC Clients"
