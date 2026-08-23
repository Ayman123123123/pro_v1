# مراجع التقنيات الحديثة للمحادثات والاتصال الفوري

هذه المراجع ستُستخدم كمعايير تصميم وتدقيق فقط. لا تفرض ترقية مكتبة أو تغيير بنية المشروع من دون توافق واختبار عملي على Samsung وMotorola.

| المجال | المرجع | الاستخدام المقترح |
|---|---|---|
| WebRTC / ICE | [WebRTC Peer Connections](https://webrtc.org/getting-started/peer-connections) | تدقيق دورة PeerConnection وجمع وتمرير ICE candidates. |
| تفاوض آمن | [Perfect Negotiation — MDN](https://developer.mozilla.org/en-US/docs/Web/API/WebRTC_API/Perfect_negotiation) | معالجة تصادم OFFER وإعادة التفاوض دون فقد أو إغلاق مكالمة صحيحة. |
| قياس الجودة | [W3C WebRTC Statistics](https://www.w3.org/TR/webrtc-stats/) | اختيار RTT وjitter وpacket loss وbitrate وcandidate-pair كمقاييس تشخيص. |
| إعادة ICE | [RTCPeerConnection — MDN](https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection) | استعمال ICE restart بصورة محدودة عند فشل الاتصال أو تبدل الشبكة. |
| FCM | [Android Message Priority — Firebase](https://firebase.google.com/docs/cloud-messaging/android-message-priority) | مراجعة رسائل الإيقاظ عالية الأولوية للمكالمة الواردة، من دون جعلها بديلاً لمسار WebSocket. |
| الخلفية | [Android Foreground Service Restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) | مراجعة وقت وشروط بدء خدمة المكالمة في الخلفية. |
| البيانات دون اتصال | [Android Offline-first Data Layer](https://developer.android.com/topic/architecture/data-layer/offline-first) | توجيه outbox دائم ومزامنة الرسائل بعد استعادة الشبكة. |
| القوائم الكبيرة | [Android Paging 3](https://developer.android.com/topic/libraries/architecture/paging/v3-overview) | تحسين تحميل المحادثات الطويلة ومنع jank. |
| الأداء | [Android Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview) | تحسين مسارات فتح المحادثة وواجهة الرنين بعد قياسها. |
| التزامن | [Kotlin Coroutines on Android](https://developer.android.com/kotlin/coroutines) | تدقيق structured concurrency ومنع تشغيل أعمال شبكة أو تشفير غير منظمة. |
