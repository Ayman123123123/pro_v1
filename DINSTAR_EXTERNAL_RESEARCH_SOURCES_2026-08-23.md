# مصادر البحث الخارجية — DINSTAR UC2000-VE وسبأفون

## DINSTAR

| المصدر | الرابط | النقاط المتحقق منها |
|---|---|---|
| صفحة المنتج الرسمية UC2000-VE | https://www.dinstar.com/GSM-3G-LTE-voip-gateway/4-8-ports/ | الجهاز بوابة 4/8 قنوات GSM/3G/4G متوافقة مع SIP؛ يدعم API وSMS وUSSD وSIP/RTP؛ يذكر G.711A/U وG.723.1 وG.729AB وRFC3261. |
| دليل المستخدم الرسمي | https://www.dinstar.com/WEB/files/15278/2018-09-06/UC2000-VE%26VF%26VG_GSM%20_LTE_VoIP_Gateway_User_Manual.pdf | الصفحة 89: Phone Number Learning يدعم USSD/SMS/Call. طريقة SMS: إرسال رسالة للمشغل، ثم مطابقة الرد بالكلمات المفتاحية واستخراج الرقم؛ يدعم كتابة الرقم المتعلم في SIM عند قدرة الشريحة. |
| مرجع HTTP API الرسمي | https://www.dinstar.com/WEB/files/13151/2018-06-05/Dinstar%20GSM%20Gateway%20HTTP%20API-v202011.pdf | API مبني على HTTP/JSON؛ يجب تفعيل New Version API؛ توثيق get_port_info وإدارة SMS/USSD وCDR. |
| دليل تحليل channel في HTTP API | https://www.dinstar.com/blog/technical-guide/how-to-analyze-which-channel-is-included-in-http-api-log/ | عند الحاجة لتحليل port_map: البتات من اليمين لليسار تقابل المنافذ 0–31. |

## سبأفون اليمن

| المصدر | الرابط | النقطة المتحقق منها |
|---|---|---|
| صفحة الخدمات الأساسية الرسمية لسبأفون | https://www.sabafon.com/service/23/main-services/en | خدمة معرفة الرقم: إرسال النص `MMN` إلى الرقم القصير `333`؛ النتيجة في مقتطف البحث تقول إن التكلفة 10 YER. |

## ملاحظة منهجية

هذه المراجع تُستخدم للتحقق من السلوك العام وحقول التكوين. النسخة الثابتة في الجهاز هي المرجع التشغيلي النهائي عند اختلاف الواجهة أو الفيرموير.

## FAQ رسمية إضافية

| المصدر | الرابط | النتيجة المحفوظة |
|---|---|---|
| DINSTAR Support Center — FAQ | https://www.dinstar.com/faq/ | تعرّف DINSTAR Ringback tone بأنها الإشارة الصوتية التي يسمعها المتصل أثناء رنين الطرف المقصود، وهي تحقق للطرف المتصل أن الطرف الآخر يرن. كما تذكر أن بوابة GSM VoIP قد تشغل IVR افتراضيًا للمكالمات الواردة عندما لا يوجد إعداد توجيه. |

هذه النتيجة تدعم اختبار فصل الإشارة عن الصوت: يجب التحقق من SIP 180/183 وearly media ومن توجيه المكالمة الواردة قبل إضافة رنين محلي في التطبيق.

| المصدر | الرابط | النتيجة المحفوظة |
|---|---|---|
| DINSTAR Support Center — FAQ | https://www.dinstar.com/faq/ | تؤكد DINSTAR أن UC2000 يدعم ثلاث طرق لتعلم رقم الشريحة: **USSD** و**SMS** و**Call**. هذا يبرر الانتقال إلى USSD أو Call فقط بعد تشخيص وإثبات سبب فشل SMS، وليس تخمين رقم المنفذ. |
