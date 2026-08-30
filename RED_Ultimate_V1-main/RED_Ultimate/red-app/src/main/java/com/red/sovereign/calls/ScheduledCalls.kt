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
import com.red.sovereign.auth.TokenStore
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
