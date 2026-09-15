package com.red.sovereign.calls

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * P0-D — ساحب صندوق بريد المكالمات (mailbox poller) — رنين محلي 100% بلا دفع خارجي.
 *
 * يسحب العروض المخزنة عبر POST /api/calls/pending عند:
 * - اتصال سوكت المكالمات ([CallSignalingClient.onOpen])
 * - اتصال سوكت الرسائل ([com.red.sovereign.core.RedConnectionService.onState] CONNECTED)
 * - إقلاع الجهاز/تحديث التطبيق ([CallBootReceiver])
 * - جدولة WorkManager مقيدة بـ CONNECTED.
 *
 * الفلترة عبر [CallRingPolicy.shouldRingNow]/[CallRingPolicy.isOfferExpired]
 * ثم التوجيه حسب النوع: 1:1 عبر [CallRingRegistry.showIncoming] + إحماء
 * [YounesCallService.listen] (لا مدخل عرض مباشر في HEAD — المسار الموحد فقط)،
 * و GROUP عبر [GroupCallService.notifyIncoming]، و CONF/CONFERENCE/SPACE عبر
 * [ConferenceService.invite]، و LIVE عبر [LiveStreamService.invite].
 * يقبل كائناً واحداً أو قائمة أو مغلفاً. TTL دائماً 120 ([CallRingPolicy.MAILBOX_TTL_SECONDS]).
 * بلا createdAt لا يرن إطلاقاً (منع الرنين الوهمي).
 */
class PendingOfferPoller(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (TokenStore(applicationContext).accessToken.isNullOrBlank()) {
            Log.d(TAG, "no token — skip pending poll")
            return Result.success()
        }
        val polled = pollOnce(applicationContext)
        Log.d(TAG, "doWork polled=$polled")
        return Result.success()
    }

    @Serializable
    private data class PendingOfferResponse(
        val callId: String = "",
        val callerId: String = "",
        val mode: String = "VOICE",
        val offerSdp: String = "",
        val ttlSeconds: Int? = null,
        // الطابع الزمني إلزامي للرنين — غيابه يعني الإسقاط (منع الرنين الوهمي)، لا "الآن".
        val createdAt: Long? = null,
        val createdAtMs: Long? = null,
        // GROUP/CONF/LIVE: دعوة غرفة بلا SDP — الحقول الإضافية اختيارية.
        val video: Boolean? = null,
        val isVideo: Boolean? = null,
        val hostName: String? = null,
        val inviter: String? = null,
        val groupCallId: String? = null,
        val roomId: String? = null,
        val streamId: String? = null
    )

    /** مغلف محتمل: {"offers":[...]} / {"pending":[...]} / {"items":[...]}. */
    @Serializable
    private data class PendingOfferListWrapper(
        val offers: List<PendingOfferResponse>? = null,
        val pending: List<PendingOfferResponse>? = null,
        val items: List<PendingOfferResponse>? = null
    )

    companion object {
        private const val TAG = "PendingOfferPoller"
        private const val UNIQUE_WORK = "red-pending-offer-poll"
        /** TTL دائماً 120 — يتجاوز أي قيمة قادمة من الخادم. */
        const val FORCED_TTL_SECONDS = 120

        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * استدعاء فوري (غير حاجب) — يُستخدم في onOpen/onState(CONNECTED)/BootReceiver.
         */
        fun pollNow(context: Context) {
            ioScope.launch {
                runCatching { pollOnce(context.applicationContext) }
                    .onFailure { Log.w(TAG, "pollNow failed: ${it.message}") }
            }
        }

        /**
         * جدولة WorkManager مقيدة بالشبكة (CONNECTED) — للسحب المتأخر/المعاد بعد reboot.
         *
         * FIX (تجويع السحب): كانت ExistingWorkPolicy.REPLACE تُلغي العمل المجدول السابق
         * في كل onOpen/onState(CONNECTED)/BootReceiver، فالاستدعاءات المتتالية السريعة
         * تُجَوِّع السحب ولا يُنفَّذ أبداً. KEEP تُبقي المجدول القائم (لا إلغاء).
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<PendingOfferPoller>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    10,
                    java.util.concurrent.TimeUnit.SECONDS
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            runCatching {
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE_WORK,
                    ExistingWorkPolicy.KEEP,
                    request
                )
            }.onFailure { Log.w(TAG, "schedule failed: ${it.message}") }
        }

        /**
         * سحبة واحدة متزامنة: POST /api/calls/pending → فلترة → رنين.
         *
         * - يقبل كائناً واحداً `{...}` أو قائمة `[...]` أو مغلفاً `{"offers":[...]}`.
         * - يعالج 1:1 (VOICE/VIDEO) و GROUP و CONF/CONFERENCE و LIVE — لا تُسقط أيّاً منها.
         * - بلا `createdAt`/`createdAtMs` لا يرن إطلاقاً (منع الرنين الوهمي) — العمر
         *   المجهول لا يُفترض "الآن".
         * @return true إن رنّ عرض واحد على الأقل.
         */
        suspend fun pollOnce(context: Context): Boolean {
            val app = context.applicationContext
            val tokens = TokenStore(app)
            if (tokens.accessToken.isNullOrBlank()) return false
            val myId = tokens.redId.orEmpty()
            val client = AuthorizedApiClient(tokens)
            // العقد: POST /api/calls/pending بجسم فارغ — الهوية من JWT حصراً.
            val result = runCatching {
                client.request("POST", "/api/calls/pending", "{}")
            }.getOrElse { e ->
                Log.w(TAG, "pending request error: ${e.message}")
                return false
            }
            when (result) {
                is ApiResult.Error -> {
                    // 204 تُترجم Success بجسم فارغ؛ أما الخطأ الحقيقي فيُسكت (poll دوري).
                    Log.d(TAG, "pending poll error code=${result.code} msg=${result.message}")
                    return false
                }
                is ApiResult.Success -> {
                    if (result.code == 204 || result.value.isBlank()) {
                        Log.d(TAG, "no pending offer (204/empty)")
                        return false
                    }
                    val offers = parseOffers(result.value)
                    if (offers.isEmpty()) {
                        Log.d(TAG, "pending decode empty — skip")
                        return false
                    }
                    var rangAny = false
                    // الأحدث أولاً: من لديه طابع يُرن الأجدد قبل الأقدم.
                    val ordered = offers.sortedByDescending { it.createdAtMs ?: it.createdAt ?: Long.MIN_VALUE }
                    for (offer in ordered) {
                        if (handleOneOffer(app, offer, myId)) rangAny = true
                    }
                    return rangAny
                }
            }
        }

        /**
         * يحلل جسم الاستجابة: قائمة `[...]`، أو مغلف `{"offers":[...]}`،
         * أو كائن واحد `{...}` (العقد الحالي). الفشل = قائمة فارغة (بلا رنين وهمي).
         */
        private fun parseOffers(body: String): List<PendingOfferResponse> {
            val trimmed = body.trim()
            if (trimmed.isEmpty() || trimmed == "null") return emptyList()
            if (trimmed.startsWith("[")) {
                val list = runCatching {
                    json.decodeFromString<List<PendingOfferResponse>>(trimmed)
                }.getOrNull()
                if (list != null) return list.filter { it.callId.isNotBlank() }
                Log.w(TAG, "pending list decode failed")
                return emptyList()
            }
            val wrapper = runCatching {
                json.decodeFromString<PendingOfferListWrapper>(trimmed)
            }.getOrNull()
            val wrapped = wrapper?.offers ?: wrapper?.pending ?: wrapper?.items
            if (!wrapped.isNullOrEmpty()) return wrapped.filter { it.callId.isNotBlank() }
            // كائن واحد — لكن إن كان المغلف بلا قائمة معروفة وفشل الكائن، لا ترن.
            val single = runCatching {
                json.decodeFromString<PendingOfferResponse>(trimmed)
            }.getOrNull()
            if (single != null && single.callId.isNotBlank()) return listOf(single)
            Log.w(TAG, "pending decode failed (not list/wrapper/single)")
            return emptyList()
        }

        /** نوع العرض المطبّع: 1:1 + جماعي بأنواعه. */
        private enum class OfferKind { ONE_TO_ONE, GROUP, CONFERENCE, LIVE }

        private fun kindOf(mode: String): OfferKind = when (mode.uppercase()) {
            "GROUP" -> OfferKind.GROUP
            "CONFERENCE", "CONF", "SPACE" -> OfferKind.CONFERENCE
            "LIVE", "LIVESTREAM" -> OfferKind.LIVE
            else -> OfferKind.ONE_TO_ONE
        }

        /**
         * يعالج عرضاً واحداً: فلترة TTL/نافذة الرنين ثم التوجيه حسب النوع.
         * @return true إن رنّ هذا العرض.
         */
        private suspend fun handleOneOffer(app: Context, offer: PendingOfferResponse, myId: String): Boolean {
            if (offer.callId.isBlank() || offer.callerId.isBlank()) {
                Log.d(TAG, "pending offer missing ids — skip")
                return false
            }
            val kind = kindOf(offer.mode)
            // SDP إلزامي لمكالمات 1:1 فقط — دعوات الغرف (GROUP/CONF/LIVE) بلا SDP بطبيعتها.
            if (kind == OfferKind.ONE_TO_ONE && offer.offerSdp.isBlank()) {
                Log.d(TAG, "pending offer without sdp — skip callId=${offer.callId}")
                return false
            }
            // منع الرنين الوهمي: بلا أي طابع زمني لا نرن ولا نسجل فائتة — العمر مجهول.
            val createdAtMs = offer.createdAtMs
                ?: offer.createdAt?.let { if (it < 1_000_000_000_000L) it * 1000 else it }
            if (createdAtMs == null) {
                Log.w(TAG, "pending offer without createdAt — drop without ringing callId=${offer.callId}")
                return false
            }
            // TTL دائماً 120 — نتجاهل ttlSeconds القادم من الخادم.
            val now = System.currentTimeMillis()
            if (CallRingPolicy.isOfferExpired(createdAtMs, now)) {
                Log.i(TAG, "pending offer expired — drop silently callId=${offer.callId}")
                return false
            }
            if (!CallRingPolicy.shouldRingNow(createdAtMs, now)) {
                // نافذة mailbox (45s..120s): انتهى الرنين لكن العرض حي — كانت تُسقَط
                // بصمت بلا أي أثر في السجل. تُسجَّل MISSED واردة صراحةً (idempotent:
                // insertCallLog بـ REPLACE على نفس callId فلا تكرار).
                Log.i(TAG, "pending offer past ring window — log MISSED callId=${offer.callId}")
                runCatching { logMissedCall(app, offer, createdAtMs, kind) }
                    .onFailure { Log.w(TAG, "logMissed failed: ${it.message}") }
                return false
            }
            // فحص واحد ضد الرنين المزدوج: السحبات المتتالية (pollNow + WorkManager +
            // onOpen/onState) كانت تُشغّل showIncoming في كل سحبة،
            // فيرن نفس callId مرتين (إشعاران/شاشتان). Registry هو الحارس الوحيد —
            // فيرن نفس callId مرتين (إشعاران/شاشتان). Registry هو الحارس الوحيد —
            // إن كان يرن فعلاً نكتفي ونعود بلا مسار ثانٍ.
            if (CallRingRegistry.isRinging(offer.callId)) {
                Log.d(TAG, "pending offer already ringing — skip duplicate callId=${offer.callId}")
                return true
            }
            return when (kind) {
                OfferKind.ONE_TO_ONE -> ringOneToOne(app, offer, myId)
                OfferKind.GROUP -> ringGroup(app, offer, myId)
                OfferKind.CONFERENCE -> ringConference(app, offer, myId)
                OfferKind.LIVE -> ringLive(app, offer, myId)
            }
        }

        /** رنين 1:1 (VOICE/VIDEO) — المسار الموحد: إشعار + إحماء الخدمة للمسار الحي. */
        private fun ringOneToOne(app: Context, offer: PendingOfferResponse, myId: String): Boolean {
            val mode = if (offer.mode.equals("VIDEO", ignoreCase = true)) "VIDEO" else "VOICE"
            val isVideo = mode == "VIDEO"
            // 1) إشعار الرنين الموحد (يعمل حتى لو الخدمة مقتولة).
            runCatching {
                CallRingRegistry.showIncoming(
                    app,
                    offer.callId,
                    offer.callerId,
                    isVideo,
                    com.red.sovereign.calls.CallNotificationActionReceiver.CALL_TYPE_1TO1,
                    myId
                )
            }.onFailure { Log.w(TAG, "showIncoming failed: ${it.message}"); return false }
            // 2) إحماء الخدمة ليستقبل العرض الحي عبر المقبس (لا مدخل عرض مباشر
            // في HEAD — onSignal داخل الخدمة هو المدخل الوحيد ولا يُستدعى خارجها).
            runCatching { YounesCallService.listen(app) }
                .onFailure { Log.w(TAG, "listen warm-up failed: ${it.message}") }
            Log.i(TAG, "pending offer ringing callId=${offer.callId} from=${offer.callerId} mode=$mode")
            return true
        }

        /** رنين جماعي GROUP — إشعار موحد + GroupCallService (كانت تُسقط بصمت). */
        private fun ringGroup(app: Context, offer: PendingOfferResponse, myId: String): Boolean {
            val isVideo = offer.video == true || offer.isVideo == true || offer.mode.equals("VIDEO", ignoreCase = true)
            val hostName = offer.hostName?.takeIf { it.isNotBlank() }
                ?: offer.inviter?.takeIf { it.isNotBlank() }
                ?: offer.callerId
            runCatching {
                CallRingRegistry.showIncoming(
                    app,
                    offer.callId,
                    hostName,
                    isVideo,
                    com.red.sovereign.calls.CallNotificationActionReceiver.CALL_TYPE_GROUP,
                    myId
                )
            }.onFailure { Log.w(TAG, "showIncoming group failed: ${it.message}"); return false }
            runCatching {
                GroupCallService.notifyIncoming(
                    app,
                    offer.groupCallId?.takeIf { it.isNotBlank() } ?: offer.callId,
                    myId,
                    offer.callerId,
                    hostName,
                    isVideo
                )
            }.onFailure { Log.w(TAG, "group notifyIncoming failed: ${it.message}") }
            Log.i(TAG, "pending GROUP ringing callId=${offer.callId} from=${offer.callerId}")
            return true
        }

        /** رنين مؤتمر CONF/CONFERENCE/SPACE — عبر ConferenceService (إشعار FSI داخلي). */
        private fun ringConference(app: Context, offer: PendingOfferResponse, myId: String): Boolean {
            val video = offer.video ?: offer.isVideo ?: !offer.mode.equals("SPACE", ignoreCase = true)
            val inviter = offer.inviter?.takeIf { it.isNotBlank() }
                ?: offer.hostName?.takeIf { it.isNotBlank() }
                ?: offer.callerId
            runCatching {
                ConferenceService.invite(
                    app,
                    offer.roomId?.takeIf { it.isNotBlank() } ?: offer.callId,
                    myId,
                    inviter,
                    video
                )
            }.onFailure { Log.w(TAG, "conference invite failed: ${it.message}"); return false }
            Log.i(TAG, "pending CONF ringing callId=${offer.callId} from=${offer.callerId}")
            return true
        }

        /** رنين بث LIVE — عبر LiveStreamService (إشعار FSI داخلي). */
        private fun ringLive(app: Context, offer: PendingOfferResponse, myId: String): Boolean {
            runCatching {
                LiveStreamService.invite(
                    app,
                    offer.streamId?.takeIf { it.isNotBlank() } ?: offer.callId,
                    myId,
                    offer.callerId
                )
            }.onFailure { Log.w(TAG, "live invite failed: ${it.message}"); return false }
            Log.i(TAG, "pending LIVE ringing callId=${offer.callId} from=${offer.callerId}")
            return true
        }

        /**
         * تسجيل MISSED واردة لعرض سقط في نافذة 45s..120s (انتهى الرنين، mailbox حي).
         * محلي 100% — نفس تشفير سجل YounesCallService (CallLogCipher + LocalRepository)،
         * idempotent عبر REPLACE على callId. لا سيرفر خارجي ولا دفع خارجي.
         */
        private suspend fun logMissedCall(
            app: Context,
            offer: PendingOfferResponse,
            createdAtMs: Long,
            kind: OfferKind = OfferKind.ONE_TO_ONE
        ) {
            val cipher = CallLogCipher()
            val peer = offer.callerId
            val type = when (kind) {
                OfferKind.GROUP -> "GROUP"
                OfferKind.CONFERENCE -> if (offer.mode.equals("SPACE", ignoreCase = true)) "SPACE" else "GROUP"
                OfferKind.LIVE -> "LIVE"
                OfferKind.ONE_TO_ONE -> if (offer.mode.equals("VIDEO", ignoreCase = true)) "VIDEO" else "VOICE"
            }
            val log = com.red.sovereign.core.database.CallLogEntity(
                id = offer.callId,
                peerId = cipher.encryptPeerId(peer),
                peerLabel = cipher.encryptLabel(peer),
                type = type,
                direction = "INCOMING",
                route = "RED",
                status = "MISSED",
                timestamp = createdAtMs,
                durationMs = 0L,
                answeredAt = null,
                endedAt = createdAtMs
            )
            com.red.sovereign.core.database.LocalRepository(app).saveCallLog(log)
            Log.i(TAG, "logged MISSED callId=${offer.callId} peer=$peer")
        }
    }
}
