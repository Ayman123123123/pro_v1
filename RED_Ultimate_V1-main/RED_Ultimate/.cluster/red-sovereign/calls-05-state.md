# calls-05-state.md — وكيل منطق حالة المكالمة (تشخيص قراءة فقط)
- التاريخ: 2026-09-12 | النطاق: `red-app/src/main/java/com/red/sovereign/calls/`
- المنهج: قراءة فقط (CallRuntime، CallSignal، CallSessionGuard، CallCleanupGuard، CallReconnectManager، OutgoingOfferStartPolicy، CallEndUiReturnPolicy، NetworkChangeWatcher، PendingCallSignalQueue، CallRingPolicy، YounesCallService كاملاً + فحوصات WebRtcEngine/CallSignalingClient/CallDeliveryEngine/CallBootReceiver). لا تعديل ولا build ولا git.
- المسارات أدناه نسبية إلى مجلد `calls/` ما لم يُذكر خلاف ذلك.

---

## 1) آلة الحالات الفعلية IDLE→RINGING/CONNECTING→ACTIVE→ENDED

**التعريف:** `CallUiState` sealed interface — CallRuntime.kt:14-96: `Idle, Incoming, Connecting(+presenceState), Active, ActiveWithIncoming, Error, Busy, Declined, NoAnswer, CallEnded, Reconnecting`؛ الحالات النهائية `isTerminal` سطر 91-94؛ عرض النهائي 4 ثوانٍ `TERMINAL_DISPLAY_MS` سطر 83.

**المخزن:** كائن مفرد قابل للكتابة من أي مكان: `var state: CallUiState by mutableStateOf(CallUiState.Idle)` — CallRuntime.kt:99. لا يوجد FSM مركزي ولا جدول انتقالات ولا mutex؛ الحماية تعتمد على حراس متناثرين (`sessionGuard` YounesCallService.kt:95، `cleanupGuard` :96، وفحوصات `is` داخل كل معالج).

**مواقع الكتابة (YounesCallService.kt):**
- صادر: `ACTION_START` → Connecting :144؛ وصف محلي OFFER → Connecting :570؛ RINGING وارد → Connecting.withPresence(RINGING) :408؛ تحديثات presence :724-727؛ ICE CONNECTED → **Active** :609.
- وارد: OFFER → **Incoming** :365؛ قبول → Connecting :527.
- انتظار مكالمة: ActiveWithIncoming :354؛ رفض الثانية → Active :239؛ قبول الثانية → Connecting :935؛ استئناف الموقوفة → Active :960.
- انقطاع WS → **Reconnecting** :693؛ عودة WS → Active :311.
- نهائية: Busy :1120، Declined :1131، NoAnswer :1142/:425 (CANCELLED)، Error :973/:1151/:139، CallEnded :1167؛ التنظيف → **Idle** :1191 (بعد 4 ثوانٍ)، :1314، :283 (ACTION_STOP).

**مواقع القراءة:** UnifiedCallOverlays.kt:19-40 (تحكيم أي overlay يظهر)، YounesCallOverlay CallRuntime.kt:186-190، `isActiveCall()` :755-757، `isRenegotiation()` :759-763، وحرارس الحالة داخل كل معالج (:1115، :1126، :1137، :301، :614، :634...). إشارات البروتوكول: CallSignal.kt:36-68 (OFFER/ANSWER/ICE/END/REJECT/CANCELLED/BUSY/RINGING/HOLD/RESUME...).

## 2) السباقات المحتملة — ما المُعالج وما غير المُعالج

- **دعوة أثناء مكالمة:**
  - ✅ معالج لـ Active غير معلّقة: فرع call-waiting :352-358 (`ActiveWithIncoming` + `pendingSecondOffer`).
  - ❌ غير معالج لـ **Active معلّقة (isHeld=true)**: شرط :353 `!currentActive.isHeld` يجعل OFFER الجديدة تسقط إلى الاستبدال :360-365 — المعلّقة تُهجر بدون END ولا يمكن استئنافها (`parkedCall` لا يُضبط إلا في `acceptSecondIncoming` :912-947).
  - ❌ غير معالج لـ **ActiveWithIncoming قائمة**: OFFER ثالثة تستبدل `incomingOffer/target/callId` :360-365 وترفع الجلسة :344 — المكالمة المنتظرة الأولى تُسقط بدون REJECT.
  - ❌ لا يُرسل **BUSY** أبداً على الحالات المشغولة غير Active (`createBusy` معرّفة CallSignal.kt:97 ولا يستدعيها أحد — الفحص grep يؤكد)؛ المتصل ينتظر مهلة الرنين كاملة.
- **إنهاء مزدوج:** ✅ محمي جزئياً — `endCall()` يفحص الحالة :1310-1316، دمج التنظيف بإلغاء `cleanupJob` :1184، توكنات الجلسة :1185-1189 و :976-979. ❌ لكن معالجات END/REJECT/BUSY/UNAVAILABLE/CANCELLED :430-433 لا تطابق `signal.callId` مع `callId` الحالي (فحص grep: لا مطابقة إلا في مسار OFFER/المجموعات) — END قديمة من مكالمة سابقة (نفس الطرف) تقتل المكالمة الحالية.
- **العودة من الخلفية:** ❌ غير معالجة كلياً — لا `ProcessLifecycleOwner`/`onStop`/`onResume` في مجلد calls (فقط `isMinimized` للـ UI: CallOverlay.kt:211، ChatCallBar.kt:64). الحالة في ذاكرة العملية فقط (CallRuntime.kt:99)؛ موت العملية في الخلفية يفقد المكالمة ولا يُبلَّغ الطرف الآخر — `CallBootReceiver` :20-37 يستعيد وضع الاستماع فقط ولا يرسل END لأي مكالمة نشطة سابقة.
- **تغيّر الشبكة (NetworkChangeWatcher):** ✅ مُسجَّل داخل `onConnected` فقط :299-307 (onLost→onAvailable→fire بـ debounce ثانيتين NetworkChangeWatcher.kt:41-54/98-107، handover عبر onCapabilitiesChanged :56-65) ويشغّل `scheduleIceRestart` عند Active/ActiveWithIncoming :301-302. ❌ فجوات: حالة Reconnecting مستثناة من الرد على التعافي؛ أثناء Connecting (قبل اتصال WS) لا يوجد watcher — الحماية مهلة 15 ثانية فقط :172-189.

## 3) سياسات المهلة (كلها صريحة ومرمّزة)

| المهلة | القيمة | الدليل |
|---|---|---|
| رنّن صادر (لا إجابة) | **45s** → إرسال END + NoAnswer | CallRingPolicy.kt:9؛ YounesCallService.kt:161/:1100-1106 |
| رنّن وارد (لا إجابة) | **45s** → رفض تلقائي (MISSED) | :367/:1107-1110 |
| اتصال signaling أولي | **15s** → fail (Toast + Error) | :172-189 (يُلغى في onConnected :296-297) |
| تأكيد تسليم الرنّن | **4s** ACK، 3 محاولات، HTTP 5s، backoff 1s·2^n | CallDeliveryEngine.kt:47-49/99-112؛ TTL = مهلة الرنين :176 |
| إعادة اتصال WS | backoff 1→30s، **5 محاولات** ثم fail | CallReconnectManager.kt:33-34/59؛ onFailure :121 |
| ICE | restart بعد debounce **1.5s**؛ مهلة رحمة بعد FAILED **45s** → endCall | :714-715؛ :619-627 + :651 `ICE_RESTART_GRACE_MS=45_000` |
| عرض الشاشة النهائية | Error 3s (:977-978)، CallEnded 4s (:1169)، ثابت 4000ms | CallRuntime.kt:83 |
| تسوية Telecom | 850ms | CallEndUiReturnPolicy.kt:5 |

❌ ملاحظة: لا توجد مهلة صريحة على الجانب المقبوع بعد ACCEPT (لا deadline لاتصال ICE بعد القبول) — الاعتماد كلياً على أحداث FAILED/DISCONNECTED.

## 4) مسار الفشل (ICE/شبكة) — إعادة محاولة أم جمود؟

- **ICE FAILED** → **إعادة محاولة مزدوجة المصدر**: WebRtcEngine.kt:710-713 (onIceConnectionChange FAILED → `restartIce()` فوراً) + الخدمة `scheduleIceRestart` debounce 1.5s :613-628/:712-721؛ ثم مهلة رحمة 45s → `endCall(sendSignal=false)` :619-627 (إن بقيت الحالة Active/Reconnecting). DISCONNECTED → restart فقط :633-638. CLOSED → endCall :640.
- **انقطاع WS** → `onDisconnected` :687-706: Reconnecting + إعادة إرسال ICE مرشّحة (`retransmitIceCandidates` :703) + `CallReconnectManager` (5 محاولات backoff)؛ الفشل الكامل → `fail()` → Error → بعد 3s → Idle وإعادة اتصال signaling :969-985.
- **أخطاء المحرك** `onError` :673-681: أثناء مكالمة نشطة → إعادة اتصال WS فقط؛ أثناء Incoming → تُتجاهل؛ غير ذلك → fail.
- **الخلاصة:** retry موجود (ICE restart + WS reconnect) مع توقف نهائي بعد 45s/5 محاولات. ❌ لا يوجد مسار re-invite/إعادة بناء محرك للمكالمة 1:1 بعد استنفاد المهل (الاستثناء الوحيد `resumeParkedCall` :953-968)، ولا زر إعادة اتصال للمستخدم — بعد 45 ثانية المكالمة تموت.

## 5) أعلى 5 عيوب (مرتبة) + إصلاح من سطر

1. **إشارات نهاية قديمة تقتل مكالمة حية — لا مطابقة callId** في END/REJECT/BUSY/CANCELLED/UNAVAILABLE (YounesCallService.kt:430-433، و:1114-1154 لا تفحص `signal.callId`). *الإصلاح:* أعلى كل معالج: `if (signal.callId != null && signal.callId != callId) return`.
2. **دعوة جديدة أثناء Active معلّقة تهجر المعلّقة بدون END** — شرط call-waiting يستثني isHeld (YounesCallService.kt:353 فتسقط OFFER إلى الاستبدال :360-365). *الإصلاح:* تغيير الشرط إلى `if (currentActive != null)` ليدخل مسار الانتظار/التوقيف.
3. **لا رد BUSY عند الانشغال بحالة غير Active** (Connecting/Reconnecting/Incoming أخرى) — `CallSignal.createBusy` (CallSignal.kt:97) غير مستدعاة في أي مكان؛ المتصل يحترق 45 ثانية. *الإصلاح:* في فرع OFFER عند اكتشاف انشغال: `runCatching { signaling.send(CallSignal.createBusy(signal.callId.orEmpty(), signal.sourceUserId.orEmpty(), signal.mode)) }` قبل أي استبدال حالة.
4. **OFFER ثالثة تستبدل مكالمة منتظرة قائمة** وتُسقط الأولى بدون REJECT (YounesCallService.kt:344-365 مع رفع الجلسة :344 يُبطل تنظيف الأولى). *الإصلاح:* قبل `beginNewSession()`: `if (CallRuntime.state is CallUiState.ActiveWithIncoming) { signaling.send(CallSignal(signal.callId, signal.sourceUserId, type = "REJECT", mode = signal.mode)); return }`.
5. **حالة المكالمة غير محفوظة عند موت العملية/الخلفية** — CallRuntime.kt:99 ذاكرة فقط؛ لا END يُرسل للطرف الآخر بعد موت العملية (CallBootReceiver.kt:20-37 يستعيد الاستماع فقط) فيبقى الطرف معلقاً حتى مهلته. *الإصلاح:* حفظ سجل نشط (callId/peer/mode/startedAt) في ملف عند الدخول Active، وعند `ACTION_LISTEN` التالي: إن وُجد السجل أرسل END له وامسحه.

**ذكر شرفي (6):** restartIce بمصدرين دون عدّاد/حد أقصى (WebRtcEngine.kt:712-713 + YounesCallService.kt:618/637 بـ debounce 1.5s فقط :712-721) قد يتصارعان أثناء gathering ويتسببان بفشل متكرر مع شبكة متقطعة. *الإصلاح:* عدّاد محاولات restart بحد أقصى (مثلاً 3) مشترك بين المسارين قبل تسليم المهلة لـ failedIceJob.