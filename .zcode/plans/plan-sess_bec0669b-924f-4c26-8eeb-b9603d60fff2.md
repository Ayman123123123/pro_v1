خطة تنفيذ شاملة — YOUNES RED Ultimate: المنفذ 7 + SMS + صوت المكالمات عبر Dinstar + الاستقرار

═══ خلاصة التشخيص (ما وجدته) ═══

1) الجهاز: Dinstar UC2000-VE-8T (8 منافذ GSM) على 192.168.11.1. البنية الصحيحة مؤكدة من التوثيق الرسمي: SIP + H.248، HTTP API رسمي (/api/send_sms، /api/query_incoming_sms، /api/query_sms_status) — نفس ما ينفذه DinstarMasterClient في الخادم. الجهاز يدعم Push Events (دفع الوارد فوراً للخادم) وهو أفضل من الاستقصاء الدوري الحالي.

2) سبب عدم عمل صوت المكالمات عبر Dinstar — تم تحديده بدقة:
   - التطبيق يستدعي المسار القديم POST /api/pstn/calls → dialGsm ينشئ قناة Local مع Application=Wait(1): يرنّ الهاتف المقابل، وعند الرد ينتظر ثانية ويغلق. لا ساق صوتية للتطبيق إطلاقاً (توثيق ذلك مكتوب في الكود نفسه EnhancedPstnManager.kt).
   - الخادم يوفر المسار الصحيح الجاهز: POST /api/pstn/bridge يعيد sipServer (WS :8089) + sipUsername=red-webrtc-client + كلمة السر + ICE/TURN — لكن التطبيق لا ينفذ أي عميل SIP/WebSocket إطلاقاً (صفر مراجع لـ red-webrtc-client/8089 في red-app).
   - جانب Asterisk جاهز 100%: transport-ws 8089، نظير red-webrtc-client (webrtc=yes)، سياق from-red-client-webrtc يمرر عبر ترويسة X-Red-Gw إلى المنفذ الدقيق (dinstar-gw-...-port-7 مثلاً).

3) SMS: الخادم كامل (إرسال/محادثات/مقروء/حذف/تحديث + وارد عبر ingest + تقارير تسليم بـ ref_id). التطبيق: لا توجد أي شاشات SMS. حقول الـ DTO مؤكدة (ConversationDto: number/operator/lastText/lastTime/direction/status/unreadCount — MessageDto: id/number/content/direction/status/createdAt/isRead).

4) التعليق وسرعة الوصول: فحص حلقات إعادة الاتصال في RedWebSocketClient/RedConnectionService + تنبيهات المكالمات الواردة، مع تفعيل دفع أسرع للأحداث.

5) القرار التقني (سؤالك: هل المكتبات صحيحة؟ هل نحتاج أجهزة وسيطة؟): المكدس الحالي صحيح ومهني (Kotlin/Compose + OkHttp WS + WebRTC + Spring Boot + Asterisk PJSIP + Dinstar HTTP API). لا حاجة لأي جهاز وسيط أو خارجي — Asterisk هو الجسر الصوتي القياسي (نفس نمط Twilio/Vonage). لن نضيف Linphone SDK (حجم كبير وتعارض مع WebRTC الحالي)؛ سنكتب عميل SIP-over-WS مصغّر (~400 سطر) يعيد استخدام WebRTC الموجود.

═══ المرحلة 0 — فحص حي للجهاز والمنافذ + تفعيل المنفذ 7 (الأولوية العاجلة) ═══

1. فحص كل المنافذ 0-7 عبر السكربتات الموجودة (test_dinstar_port7.py / diagnose_and_fix_dinstar.ps1): حالة الشريحة، التسجيل، قوة الإشارة، لكل منفذ — تقرير مكتوب.
2. فحص /api/pstn/ports/status من الخادم (يعيد status/signal/operator/simNumber لكل منفذ).
3. ربط المنفذ 7 بالمستخدم الحالي (الهاتف المتصل به): تحديث user_account (pstn_gateway_id, pstn_port_index=7, pstn_enabled=true, pstn_daily_limit>0) عبر SQL مباشر على red-db-sql + تسجيل telecom_gateways إذا لزم، أو عبر API الإدارة إن توفر.
4. إصلاح مشكلة CDR الموثقة في تقرير اليوم (فشل INSERT INTO dinstar_cdr — إضافة عمود call_id أو إنشاء الجدول بترحيل Flyway جديد V__).
5. فحص نطاق RTP في rtp.conf (10000-10100 صغير جداً للمكالمات المتوازية — توسيع آمن إلى 10000-10900 داخل نفس قواعد الجدار).

═══ المرحلة 1 — واجهات SMS في التطبيق (red-app) ═══

ملفات جديدة في red-app/src/main/java/com/red/sovereign/sms/:
1. SmsApi.kt — استدعاء المسارات الستة الموجودة في الخادم عبر AuthorizedApiClient الموجود.
2. SmsViewModel.kt — حالة المحادثات/المحادثة المفتوحة/الإرسال + تحديث دوري خفيف (30 ث) + تحديث فوري عند الفتح.
3. SmsScreens.kt — شاشتان بهوية التطبيق الذهبية:
   - قائمة المحادثات: شارة غير المقروء، كشف المشغل اليمني (YemeniOperatorDetector)، حذف بالسحب/الضغط الطويل، زر تحديث (refresh يجلب الوارد من الجهاز فوراً).
   - المحادثة: فقاعات IN/OUT بألوان الحالة (PENDING/SENT/DELIVERED/FAILED)، شريط إرسال مع عدّاد المقاطع، تعليم كمقروء تلقائياً عند الفتح.
دمج التنقل في RedDashboard.kt:
   - إضافة SMS إلى enum SovereignScreen + حالة العرض + BackHandler.
   - مدخلان: تبويب خامس "الرسائل" في DinstarPhoneScreen + صف في MoreScreen (رسائل الهاتف اليمني).

═══ المرحلة 2 — مسار صوت حقيقي للمكالمات عبر Dinstar ═══

التطبيق (الجزء الناقص الوحيد معملياً):
1. calls/PstnBridgeApi.kt — POST /api/pstn/bridge + POST /api/pstn/bridge/{callId}/hangup (نماذج BridgeResponse المؤكدة من PstnBridgeController.kt).
2. calls/PstnSipClient.kt — عميل SIP فوق WebSocket مصغّر:
   - اتصال WS إلى sipServer من الاستجابة (مع استبدال localhost بمضيف الخادم تلقائياً، ومحاولة ws:// إذا فشل wss://).
   - REGISTER مع Digest Auth (MD5 + دعم qop=auth) ببيانات red-webrtc-client.
   - INVITE إلى sip:<الرقم>@<host> مع ترويسة X-Red-Gw = gateway من الـ bridge (توجيه دقيق للمنفذ 7 مثلاً) + SDP من WebRTC.
   - معالجة 183 (early media/رنين الناقل الحقيقي)، 200 OK → ACK + setRemoteDescription، BYE → إنهاء، 401/407 → إعادة إرسال مصادق.
   - صوت: PeerConnection مستقل (WebRtcBootstrap.ensure الحالي) + مقطع مايك فقط + ICE من الاستجابة (stun/turn جاهزة من الخادم).
3. calls/PstnCallRuntime.kt + PstnCallOverlay — حالة مكالمة PSTN حية (RINGING/ACTIVE/ENDED + مؤقّت + كتم + إنهاء) تُعرض ضمن UnifiedCallOverlays الموجودة.
4. تعديل DialPad/AuthViewModel: dialPstn يستخدم المسار الجسر أولاً (صوت حقيقي)، ويتراجع للمسار القديم عند أخطاء محددة (PSTN_SIM_NOT_BOUND → رسالة واضحة للمستخدم بالعربية).
5. الوارد: عند redirect من from-incoming-bridge يصل INVITE لنفس العميل → شاشة رد/رفض (رفض يستدعي hangupChannel عبر REST الموجود).

الخادم (تعديلات طفيفة):
- /api/pstn/status يبقى للتوافق. إضافة حقل gateway في استجابة /api/pstn/ports/status إن لم يوجد (موجود) — لا تغييرات جذرية.
- تحسين رسالة خطأ PSTN_SIM_NOT_BOUND لتشرح ربط المنفذ من لوحة الإدارة.

Asterisk (كما هو — التحقق فقط): الدخول إلى الحاوية وتأكيد: pjsip show endpoints (ظهور red-webrtc-client + كل منافذ dinstar)، pjsip show contacts (شريحة المنفذ 7 مسجلة)، sip show/htt p conf 8089، واختبار INVITE تجريبي عبر test_sip_ws.py معدّل.

═══ المرحلة 3 — التعليق وسرعة وصول المكالمات والرسائل ═══

1. تدقيق وإصلاح RedWebSocketClient/RedConnectionService: backoff أسّي صحيح، نبضة keepalive، إعادة اتصال فورية عند عودة الشبكة، عدم تجميد مؤشر الترابط الرئيسي (كل الكتابات على حالة Compose من مسار صحيح).
2. تنبيهات SMS الوارد: عند تخزين رسالة واردة جديدة يدفع الخادم حدثاً عبر WebSocket موجود (قناة الإشعارات الحالية) → التطبيق يحدّث شارة SMS فوراً ويظهر إشعار نظام.
3. تفعيل Push Events على الجهاز نفسه (من واجهة 192.168.11.1 عبر السكربتات الموجودة) لدفع الوارد وتقارير التسليم للخادم بدل الاستقصاء الكامل — مع بقاء الاستقصاء كاحتياط (كل 60 ث بدل الأسرع الحالي إن كان يضغط الجهاز).
4. تقليل recomposition في شاشات الدردشة (تثبيت lambdas وقوائم كما فعل النمط الموجود في NavigationBar).

═══ المرحلة 4 — بناء وتحقق نهائي ═══

1. ترجمة الخادم: gradlew compileKotlin في backend-server (أو docker build).
2. ترجمة التطبيق: gradlew :app:assembleDebug في RED_Ultimate (رقم :app = red-app).
3. إعادة تشغيل pstn-gateway + backend (docker compose up -d --build للحاويتين).
4. اختبار شامل موثق: (أ) ports/status يظهر المنفذ 7 IDLE وسيم مسجل، (ب) إرسال SMS من الشاشة الجديدة + وصول وارد، (ج) مكالمة عبر Dinstar بصوت ثنائي الاتجاه عبر المنفذ 7، (د) مكالمة واردة من GSM تظهر على التطبيق وترد، (هـ) لا تعليق بعد 10 دقائق تشغيل.
5. تقرير نهائي بالعربية في جذر المشروع يوثق كل ما نُفذ وما تبقى.

معايير القبول: المنفذ 7 مربوط بالمستخدم ويعمل صوتاً؛ شاشات SMS كاملة الإرسال والوارد؛ المكالمة لا تنقطع بعد ثانية؛ إشعارات الوارد خلال <3 ثوانٍ؛ APK يجمع بنجاح.