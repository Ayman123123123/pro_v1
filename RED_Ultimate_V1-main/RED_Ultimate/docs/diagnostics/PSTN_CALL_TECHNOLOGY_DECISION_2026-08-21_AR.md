# قرار تقني لمسار مكالمات PSTN في RED Ultimate

**التاريخ:** 2026-08-21  
**النطاق:** Android WebRTC/SIP، Asterisk PJSIP/AMI، DINSTAR UC2000-VE، وتجربة اتصال Android.  
**خارج النطاق:** SMS، واستبدال DINSTAR أو نقل PBX كامل من دون دليل تشغيلي.

## القرار المختصر

يبقى المسار الأنسب لهذه الجولة هو **Android WebRTC مع SIP عبر WebSocket، ثم Asterisk PJSIP، ثم SIP trunk إلى DINSTAR UC2000-VE، ثم GSM**. فهو يحقق فصل السياسة والتفويض عن البوابة، ويستعمل Asterisk في الدور الذي تدعمه وثائقه رسمياً لعملاء WebRTC، ويستعمل UC2000-VE في دوره المصمم له كبوابة SIP بين VoIP والشبكات الخلوية.[1] [2]

لا يوجد دليل حتى الآن يبرر استبدال Asterisk بـ FreeSWITCH أو Janus، أو جعل Android يتصل بالبوابة مباشرة. العطل الحالي لم يثبت بعد على أنه قصور في Asterisk أو DINSTAR؛ بل لم يُثبت تسجيل Android الحي، كما كشفت قراءة العميل المخصص فجوات بروتوكولية محددة يمكن إصلاحها باختبار محكوم. ولذلك يكون الخيار الأقوى والأقل مخاطرة هو **تثبيت التسجيل، ثم إصلاح عميل SIP والترابط التشغيلي بالمكالمات، ثم اختبار المكالمة** قبل أي ترحيل كبير.

> لا تعني «تقنية أحدث» حلاً أفضل تلقائياً. في هذا المشروع، أفضلية الحل تقاس بسلامة تفويض الحساب والحدود اليومية، توافق WebRTC وSIP، قدرة التشخيص، جودة الصوت، وإمكانية استعادة التشغيل عند الفشل.

## الأساس التقني المؤكد

تصف DINSTAR جهاز UC2000-VE كبوابة GSM/WCDMA/LTE ذات 4 أو 8 قنوات تعبر بين الشبكة الخلوية وVoIP، مع SIP v2 وSIP trunk ودعم G.711 A-law وμ-law وDTMF عبر RFC2833 أو SIP INFO.[1] وتذكر الشركة أن نموذج التكامل مع Elastix ينطبق من حيث النظرية على SIP servers مثل Asterisk وFreeSWITCH و3CX.[2]

من جهة Asterisk، يتطلب عميل WebRTC خادم HTTP مفعلاً للمصافحة عبر WebSocket، وtransport PJSIP مناسباً، وكائنات endpoint وAOR وauth. كما توصي وثائقه بـ Opus حيث يتوفر، وتوضح أن `webrtc=yes` يضبط AVPF وDTLS وICE وRTCP multiplex و`media_use_received_transport`.[3] وهذه المتطلبات تتطابق في جوهرها مع endpoint المشروع الحالي، الذي يعلن `webrtc=yes` وDTLS وICE و`allow=opus,alaw,ulaw`.

| طبقة القرار | الوضع في المشروع | المعيار أو الدعم الخارجي | القرار |
|---|---|---|---|
| بوابة GSM | UC2000-VE كـ SIP trunk إلى GSM | الجهاز يدعم SIP trunk وG.711A/U وDTMF وMobile-to-VoIP وVoIP-to-Mobile.[1] | الإبقاء عليه؛ لا حاجة لتغييره في غياب دليل خلل عتادي أو مسار خاطئ |
| SIP/PBX | Asterisk PJSIP وAMI | Asterisk يدعم endpoint/AOR/auth وWebSocket وWebRTC رسمياً.[3] | الإبقاء عليه؛ هو طبقة السياسة والتحويل والتشخيص المناسبة |
| وسائط Android | PeerConnection صوتي وDTLS-SRTP | تكوين `webrtc=yes` في Asterisk يهيئ متطلبات وسائط WebRTC الأساسية.[3] | الإبقاء على WebRTC؛ تدقيق SDP/ICE قبل تغيير مكتبة الوسائط |
| الإشارات Android | عميل SIP مخصص فوق OkHttp WebSocket | SIP معيار مستقل يفرض قواعد دقيقة للمعاملات وACK والمصادقة.[4] | إصلاحات مركزية واختبارات؛ تقييم مكتبة ناضجة لاحقاً فقط إن بقيت أعطال التوافق |
| تجربة النظام | شاشة Compose وخدمة foreground وبعض بنى Telecom | Android يوصي بتكامل Telecom/Core-Telecom لإدارة توجيه الصوت والتركيز عند بناء تطبيق اتصال.[5] [6] | تحسين مرحلة لاحقة بعد ثبات التسجيل والوسائط؛ ليس علاجاً لمسار SIP/DINSTAR |

## المقارنة بين الخيارات

| الخيار | الاعتمادية والأمان | كلفة التغيير | الموقف |
|---|---|---|---|
| **استكمال المسار الحالي: WebRTC/SIP/WSS → Asterisk → DINSTAR** | يحافظ على التفويض وحجز Redis والسجل وAMI ووساطة Asterisk بين SRTP وRTP/G.711 | منخفضة إلى متوسطة؛ إصلاحات موجهة واختبار حي | **معتمد لهذه الجولة** |
| Android يتصل بـ DINSTAR مباشرة | يبسط عدد العقد فقط، لكنه يتجاوز تفويض الخادم والحد اليومي وتتبّع `callId`، ويعرّض اعتماد البوابة | متوسط؛ لكنه يضعف التصميم الأمني | **مرفوض** |
| استبدال Asterisk بـ FreeSWITCH | صالح من حيث الإمكان، لكن لا يحل تلقائياً أخطاء عميل SIP أو Android أو سياسة الحساب | مرتفع؛ dialplan وAMI/ESL ورصد وترحيل | **مؤجل** |
| إضافة Janus بجانب Asterisk | مفيد لسيناريوهات WebRTC متقدمة أو مؤتمرات، لكنه لا يغني عن PBX/SIP trunk للـ PSTN | مرتفع ومعقد | **مؤجل** |
| اعتماد مكتبة SIP ناضجة في Android | قد تقدم RFC compliance وDigest وdialog state أفضل | متوسط إلى مرتفع؛ مراجعة الترخيص والتكامل والأثر على WebRTC | **خيار احتياطي مشروط** |
| Core-Telecom أو ConnectionService في Android | يحسن audio focus وBluetooth وواجهة النظام وإدارة الحياة | متوسط؛ لا يصلح التسجيل أو routing بمفرده | **تحسين لاحق** |

## الفجوات ذات الأولوية في المسار الحالي

### 1. ربط مكالمة الجسر بقناة Asterisk

يحجز الخادم مكالمة باسم `callId` في `/api/pstn/bridge`، لكن عميل SIP في Android يولد `Call-ID` مستقلاً، وdialplan الخاص بمكالمة WebRTC لا يستقبل متغيراً موثقاً يربطه بـ `callId` الخاص بالخادم. يتطلب ذلك إضافة correlation صريح لا يعتمد على التخمين من القناة أو على وجود حجز وحيد. هذه ليست مشكلة SIP معيارية بحد ذاتها، بل فجوة تشغيلية في ربط السجل والحجز وAMI.

**القرار:** يضاف معرف جسر موثق وآمن إلى إشارات SIP أو إلى مسار خادمي موازٍ مع تحققه في Asterisk/AMI، ثم يربط `DinstarEventListener` القناة الصحيحة بذلك المعرف. لا يُقبل تمرير قيمة غير محمية تسمح للمستخدم بانتحال `callId` لمستخدم آخر.

### 2. ACK للمكالمة الصادرة

ينص SIP على أن ACK الذي يقرّ بـ 2xx الخاص بـ INVITE يستخدم **نفس قيمة CSeq العددية للـ INVITE**، مع تغيير اسم الطريقة إلى `ACK`.[4] عميل المشروع يزيد `cseq` قبل إنشاء ACK، لذلك قد ينتج ACK بقيمة مختلفة عن INVITE. هذا خلل محدد وقابل للإصلاح، ولا يجب انتظار مشكلة متقطعة من خادم أكثر تشدداً حتى يعالج.

**القرار:** حفظ رقم CSeq الخاص بالـ INVITE الناجح وإعادة استخدامه في ACK، مع الاحتفاظ بزيادة CSeq للطلبات اللاحقة داخل الحوار مثل BYE أو INFO.

### 3. SIP Digest والتوافق المستقبلي

يوضح توثيق Asterisk أن Asterisk يرسل `WWW-Authenticate` مع realm وnonce وalgorithm، وأن العميل يعيد المحاولة بـ `Authorization` مناسب.[7] كما يدعم Asterisk الحديث خوارزميات إضافية إلى جانب MD5، تبعاً للإصدار والبناء.[7] عميل المشروع الحالي يفترض MD5 بسيطاً ولا يحلل سوى nonce وrealm.

**القرار:** لا يتغير المسار إلى مكتبة جديدة قبل التقاط challenge الحقيقي. في الاختبار، يُتحقق من algorithm ووجود `qop` وstale وopaque. إذا كانت إعدادات Asterisk الحالية ترسل MD5 بلا qop، يكفي تصحيح ACK والتأكد من صحتها. أما إذا ظهر `qop=auth` أو خوارزمية أحدث، فيجب تنفيذ الحساب المعياري الكامل أو استبدال طبقة Digest وحدها بمكوّن موثوق.

### 4. ICE وSDP وجودة الوسائط

تشير وثائق Asterisk إلى أن `webrtc=yes` يشمل ICE وDTLS-SRTP وRTCP mux.[3] لكن نجاح التسجيل لا يثبت نجاح الوسائط: يعتمد الصوت ثنائي الاتجاه على candidates الفعالة ونوع NAT وخادم TURN وإمكانية توافق الـ SDP بين Android وAsterisk ثم بين Asterisk وDINSTAR. UC2000-VE يدعم G.711A/U بينما endpoint WebRTC يسمح Opus وG.711؛ لذلك يعمل Asterisk كطرف التحويل بين المسارين.[1] [3]

**القرار:** قبل تغيير codecs، تحفظ SDP offer/answer والـ ICE connection state وAsterisk RTP debug أثناء مكالمة محكومة. يكون الترتيب المفضل: Opus بين Android وAsterisk، وalaw أو ulaw مع DINSTAR وفق ما يتفاوض عليه الـ trunk. لا يُفرض codec من دون مشاهدة الـ SDP وسجل dialplan.

### 5. WSS/TLS والنشر خارج الشبكة المحلية

وثائق Asterisk تذكر أن WebRTC قد يعمل تقنياً عبر WebSocket غير آمن، لكن البيئات العملية تتطلب غالباً WebSocket مبنياً على TLS وشهادة موثوقة.[3] الإعداد الحالي في LAN يستخدم `ws://...:8089/ws`، وهو مناسب للتشخيص المحلي فقط وليس مساراً إنتاجياً آمناً.

**القرار:** لا يُعقّد اختبار LAN الحالي بشهادة جديدة. بعد نجاح المكالمة المحلية، ينفذ WSS/TLS عند الحافة بشهادة موثوقة، مع بقاء Asterisk خلف proxy/termination واضح أو استخدام transport WSS مطابق لتوثيق Asterisk. يجب اختبار الشهادة من Android نفسه، لا من المتصفح فقط.

## تحسين تجربة Android بعد ثبات الاتصال

Android Telecom يدير المكالمات الصوتية والمرئية، بما فيها VoIP؛ ويقدم Core-Telecom/ConnectionService لإدارة تجربة الاتصال، توجيه الصوت، والتعايش مع الاتصالات الأخرى.[5] وتشير وثائق Android إلى أن `Connection` ذاتية الإدارة يجب أن تعلن وضع VoIP، وتتيح `ConnectionService` متابعة حالة الصوت وأجهزة Bluetooth وتوجيهها.[6] كما تتطلب إدارة audio focus طلب التركيز قبل التشغيل والتعامل مع فقده ثم تركه عند انتهاء الصوت.[8]

| التحسين | المرحلة الصحيحة | السبب |
|---|---|---|
| تصحيح speaker/mute وطلب audio focus | بعد نجاح مكالمة الصوت الأساسية | لا فائدة من تحسين التوجيه إذا لم يكتمل SIP/ICE |
| Core-Telecom أو ConnectionService | بعد الصادر والوارد المستقرين | يمنح Bluetooth/سماعة/حالات نظام أكثر موثوقية، لكنه ليس PBX ولا SIP stack |
| مؤشرات جودة من WebRTC stats | بعد ظهور صوت ثنائي الاتجاه | تستخدم jitter وpacket loss وRTT الحقيقية بدلاً من قيم واجهة غير موصولة |
| TURN موثوق وWSS/TLS | قبل فتح المسار خارج LAN | مطلوب للحماية واجتياز NAT المتنوع |
| حساب SIP منفصل أو Contact مميز لكل مستخدم | قبل توسيع الوارد لعدة مستخدمين | endpoint المشترك الحالي لا يمنح هوية طرفية مستقلة لكل حساب |

## بروتوكول التحقق قبل الاتصال المدفوع

يجب أن تجتاز البيئة الخطوات التالية قبل استعمال رقم الاختبار المصرح به:

1. إعادة توصيل هاتف الاختبار والتحقق من ظهور تطبيق RED والحساب المفعل.
2. تفعيل سجل PJSIP مؤقتاً، ثم تنفيذ طلب bridge فقط في بيئة اختبار أو من Android مع تعليق INVITE، ومراقبة handshake وREGISTER وchallenge و200 OK.
3. التحقق من ظهور `red-webrtc-client` كـ contact في `pjsip show contacts`، ومن أن transport و`/ws` وHTTP server متاحة.
4. تطبيق إصلاح ACK وربط `callId` بعد حفظ دليل التسجيل وchallenge.
5. إعادة بناء Android والخادم وتشغيل الاختبارات المركزة.
6. تنفيذ مكالمة صادرة واحدة مراقبة إلى الرقم المصرح به، مع تسجيل زمن الرنين والرد، SDP/codec، واتجاهي الصوت، ثم التأكد من إزالة مفاتيح Redis والقنوات.

## المراجع

[1]: https://www.dinstar.com/GSM-3G-LTE-voip-gateway/4-8-ports/ "DINSTAR UC2000-VE: GSM/WCDMA/LTE VoIP Gateway"
[2]: https://www.dinstar.com/blog/technical-guide/configuration-video-of-UC2000-with-Elastix/ "DINSTAR: Configuration Video of UC2000 with Elastix"
[3]: https://docs.asterisk.org/Configuration/WebRTC/Configuring-Asterisk-for-WebRTC-Clients/ "Asterisk Documentation: Configuring Asterisk for WebRTC Clients"
[4]: https://datatracker.ietf.org/doc/html/rfc3261 "IETF RFC 3261: SIP: Session Initiation Protocol"
[5]: https://developer.android.com/develop/connectivity/telecom "Android Developers: Telecom framework overview"
[6]: https://developer.android.com/develop/connectivity/bluetooth/ble-audio/telecom-api-managed-calls "Android Developers: Manage calls using the Telecom API"
[7]: https://docs.asterisk.org/Configuration/Channel-Drivers/SIP/Configuring-res_pjsip/PJSIP-Authentication/ "Asterisk Documentation: PJSIP Authentication"
[8]: https://developer.android.com/media/optimize/audio-focus "Android Developers: Manage audio focus"

الدليل الرسمي لتكوين UC2000 مع 3CX يصف UC2000 كسلسلة بوابات تربط GSM/WCDMA/LTE مع SIP وتتكامل مع منصات PBX القياسية، كما يوضح دليل Elastix أن نظرية التكامل مماثلة لخوادم SIP مثل Asterisk وFreeSWITCH.[11] وبذلك لا يوجد سبب تقني لتبديل DINSTAR أو Asterisk بسبب 503 الحالي؛ موضع العطل مثبت في قرار UC2000 لتوجيه INVITE إلى GSM، لا في قابلية التكامل الأساسية.

[11]: https://www.dinstar.com/blog/technical-guide/config-gsm-gateway-with-3cx/ "DINSTAR: How to Configure GSM VoIP Gateway with 3CX"
