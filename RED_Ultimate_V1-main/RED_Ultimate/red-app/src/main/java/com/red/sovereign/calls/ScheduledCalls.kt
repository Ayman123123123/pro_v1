package com.red.sovereign.calls

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.red.sovereign.MainActivity
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** ⏰ مكالمة مجدولة — ميزة على غرار الجدولة في تلجرام/واتساب. */
data class ScheduledCall(
    val id: String,
    val title: String,
    val roomId: String,
    val video: Boolean,
    val invitees: List<String>,
    val timeMillis: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("roomId", roomId)
        .put("video", video)
        .put("invitees", JSONArray(invitees))
        .put("timeMillis", timeMillis)

    companion object {
        fun fromJson(json: JSONObject): ScheduledCall = ScheduledCall(
            id = json.optString("id"),
            title = json.optString("title"),
            roomId = json.optString("roomId"),
            video = json.optBoolean("video"),
            invitees = runCatching {
                val arr = json.optJSONArray("invitees") ?: JSONArray()
                (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
            }.getOrDefault(emptyList()),
            timeMillis = json.optLong("timeMillis")
        )
    }
}

/** تخزين المكالمات المجدولة محلياً (SharedPreferences مع JSON). */
object ScheduledCallStore {
    private const val PREFS = "scheduled_calls"
    private const val KEY = "list"

    fun list(context: Context): List<ScheduledCall> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return listOf()
        val arr = JSONArray(raw)
        (0 until arr.length()).map { ScheduledCall.fromJson(arr.getJSONObject(it)) }
            .sortedBy { it.timeMillis }
            .filter { it.timeMillis > System.currentTimeMillis() }
    }.getOrDefault(emptyList())

    fun add(context: Context, call: ScheduledCall) {
        val current = list(context).filter { it.id != call.id } + call
        save(context, current)
    }

    fun remove(context: Context, id: String) {
        save(context, list(context).filter { it.id != id })
    }

    private fun save(context: Context, calls: List<ScheduledCall>) {
        val arr = JSONArray()
        calls.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}

/** ⏰ مستقبل الإشعار: يُشغَّل عبر AlarmManager عند موعد المكالمة المجدولة. */
class ScheduledCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("scheduled_call_id") ?: return
        val call = ScheduledCallStore.list(context).firstOrNull { it.id == id } ?: return
        ScheduledCallStore.remove(context, id)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(NotificationChannel("red_scheduled", "مكالمات مجدولة", NotificationManager.IMPORTANCE_HIGH))

        val joinPi = CallNotificationActionReceiver.receiverIntent(context, CallNotificationActionReceiver.ACTION_CONFERENCE_ACCEPT_PENDING, CallNotificationActionReceiver.CALL_TYPE_CONFERENCE, id.hashCode(), callId = call.roomId, myUserId = TokenStore(context).redId.orEmpty(), hostId = "", isVideo = call.video)
        val openPending = PendingIntent.getActivity(context, id.hashCode(), Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(context, "red_scheduled")
            .setSmallIcon(if (call.video) android.R.drawable.sym_call_incoming else android.R.drawable.sym_action_call)
            .setContentTitle("⏰ مكالمتك المجدولة تبدأ الآن")
            .setContentText(call.title.ifBlank { if (call.video) "مؤتمر فيديو" else "مساحة صوتية" })
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "انضمام الآن", joinPi)
            .build()
        runCatching { manager?.notify(id.hashCode(), notif) }
    }
}

/** جدولة مكالمة عبر AlarmManager (غير دقيق — لا يتطلب إذن EXACT_ALARM). */
object ScheduledCallScheduler {
    fun schedule(context: Context, call: ScheduledCall) {
        ScheduledCallStore.add(context, call)
        scheduleAlarmOnly(context, call)
    }

    /** إعادة تسجيل كل المنبهات الباقية (تُستدعى بعد reboot — المنبهات لا تنجو منه). */
    fun rescheduleAll(context: Context) {
        ScheduledCallStore.list(context).forEach { scheduleAlarmOnly(context, it) }
    }

    private fun scheduleAlarmOnly(context: Context, call: ScheduledCall) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            call.id.hashCode(),
            Intent(context, ScheduledCallReceiver::class.java).putExtra("scheduled_call_id", call.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am?.set(AlarmManager.RTC_WAKEUP, call.timeMillis, pending)
    }

    fun cancel(context: Context, call: ScheduledCall) {
        ScheduledCallStore.remove(context, call.id)
        val am = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            call.id.hashCode(),
            Intent(context, ScheduledCallReceiver::class.java).putExtra("scheduled_call_id", call.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am?.cancel(pending)
    }
}

/**
 * مزامنة خادمية للمكالمات المجدولة (P1-F) — لا تغيّر المسار المحلي.
 *
 * - المحلي (AlarmManager) يبقى المصدر الفوري offline-first.
 * - الخادم (`POST /api/calls/scheduled`) للنسخ متعدد الأجهزة + تذكير FCM.
 * - كل الدوال آمنة offline: الفشل الشبكي يُرجع false/empty دون رمي.
 */
object ScheduledCallServerSync {
    const val PATH = "/api/calls/scheduled"

    /** جدولة محلية + دفع خادمي (best-effort). المحلية تنجح حتى لو فشل الشبكي. */
    suspend fun scheduleAndPush(context: Context, call: ScheduledCall): Boolean {
        ScheduledCallScheduler.schedule(context, call)
        return push(context, call)
    }

    /** إلغاء محلي + حذف خادمي (best-effort). */
    suspend fun cancelAndDelete(context: Context, call: ScheduledCall): Boolean {
        ScheduledCallScheduler.cancel(context, call)
        return delete(context, call.id)
    }

    /** POST /api/calls/scheduled — يرسل title/roomId/video/invitees/timeMillis. */
    suspend fun push(context: Context, call: ScheduledCall): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tokens = TokenStore(context)
            val body = JSONObject()
                .put("id", call.id)
                .put("title", call.title)
                .put("roomId", call.roomId)
                .put("video", call.video)
                .put("invitees", JSONArray(call.invitees))
                .put("timeMillis", call.timeMillis)
                .toString()
            when (val r = AuthorizedApiClient(tokens).request("POST", PATH, body)) {
                is ApiResult.Success -> r.code in 200..299
                is ApiResult.Error -> false
            }
        }.getOrDefault(false)
    }

    /** GET /api/calls/scheduled — يجلب مجدولاتي المستقبلية فقط. */
    suspend fun fetch(context: Context): List<ScheduledCall> = withContext(Dispatchers.IO) {
        runCatching {
            val tokens = TokenStore(context)
            when (val r = AuthorizedApiClient(tokens).request("GET", PATH)) {
                is ApiResult.Success -> {
                    val arr = JSONArray(r.value.ifBlank { "[]" })
                    (0 until arr.length()).mapNotNull { i ->
                        runCatching { ScheduledCall.fromJson(arr.getJSONObject(i)) }.getOrNull()
                    }.filter { it.timeMillis > System.currentTimeMillis() }
                        .sortedBy { it.timeMillis }
                }
                is ApiResult.Error -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    /** DELETE /api/calls/scheduled/{id}. */
    suspend fun delete(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        if (id.isBlank()) return@withContext false
        runCatching {
            val tokens = TokenStore(context)
            when (val r = AuthorizedApiClient(tokens).request("DELETE", "$PATH/$id")) {
                is ApiResult.Success -> r.code in 200..299
                is ApiResult.Error -> false
            }
        }.getOrDefault(false)
    }
}

/**
 * تبليغ خادمي عن التسجيل (P1-F) — بوابة {callId, consent} فوق metadata.
 *
 * - الخادم: `POST /api/recordings` موجود في `CallRecordingController` ويخزن metadata
 *   (callId, sha256, size, duration) في Mongo `call_recordings`.
 * - العميل: لا يُرسل شيئاً دون موافقة صريحة ([RecordingConsentStore.grant] +
 *   [CallRecordingManager.start](consentGranted=true)). الـ consent بوابة محلية،
 *   والـ metadata تُرسل فقط عند consent=true — يحقق عقد {callId, consent}.
 */
object RecordingServerSync {
    const val PATH = "/api/recordings"

    suspend fun report(
        context: Context,
        callId: String,
        peerId: String,
        sha256: String,
        sizeBytes: Long,
        durationMs: Long,
        consentGranted: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!consentGranted || callId.isBlank()) return@withContext false
        runCatching {
            val tokens = TokenStore(context)
            val body = JSONObject()
                .put("callId", callId)
                .put("peerId", peerId)
                .put("sha256", sha256)
                .put("sizeBytes", sizeBytes)
                .put("durationMs", durationMs)
                .toString()
            when (val r = AuthorizedApiClient(tokens).request("POST", PATH, body)) {
                is ApiResult.Success -> r.code in 200..299
                is ApiResult.Error -> false
            }
        }.getOrDefault(false)
    }
}
