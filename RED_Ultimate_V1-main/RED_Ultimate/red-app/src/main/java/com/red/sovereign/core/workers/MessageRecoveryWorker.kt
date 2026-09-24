package com.red.sovereign.core.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.red.sovereign.core.database.RedDatabase
import java.util.concurrent.TimeUnit

/**
 * عامل استعادة الرسائل المفقودة — يفحص التناقضات بين جدول messages (ciphertext)
 * وجدول local_history (plaintext المشفر محلياً) ويستعيد الرسائل المفقودة.
 *
 * ## المشكلة
 * الرسائل تُحفظ في خطوتين:
 * 1. `messages` table: ciphertext الخام (من الخادم)
 * 2. `local_history` table: plaintext المشفر بـ Keystore (للعرض في الواجهة)
 *
 * إذا نجحت الخطوة 1 وفشلت الخطوة 2 (مثلاً: موت العملية، فشل تشفير Keystore،
 * خطأ في SQLite)، تبقى الرسالة في `messages` لكنها تختفي من الواجهة.
 *
 * ## الحل
 * هذا العامل يفحص دورياً:
 * - رسائل في `messages` بدون مقابل في `local_history`
 * - يحاول إعادة فك التشفير وحفظها في `local_history`
 * - يسجل التناقضات للتدقيق
 *
 * ## التكامل
 * - يُجدول كل 30 دقيقة من `YounesApplication.onCreate`
 * - يعمل فقط عند الاتصال بالشبكة (يحتاج مفاتيح فك التشفير)
 * - لا يحذف أي رسالة — فقط يستعيد المفقود
 */
class MessageRecoveryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val app = applicationContext
        val db = RedDatabase.getInstance(app)
        val dao = db.redDao()
        val outboxDao = db.outboxDao()
        val now = System.currentTimeMillis()

        // إحصائيات عبر openHelper (بلا DAO جديد) — لا حذف، فقط رصد
        fun countTable(table: String): Int = runCatching {
            val c = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table", null)
            c.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }.getOrDefault(0)
        val totalMessages = countTable("messages")
        val totalHistory = countTable("local_history")
        val diff = totalMessages - totalHistory
        if (diff > 0) {
            Log.w(TAG, "Found $diff messages without local_history — triggering catch-up via service")
            // إعادة اللحاق عبر الخدمة (catchUpMissedMessages عند الاتصال) — idempotent
            runCatching { com.red.sovereign.core.RedConnectionService.start(app) }
        }

        // إحياء outbox العالق SENDING (موت العملية قبل ACK) — idempotent بذات uuid
        runCatching { outboxDao.resetStuckSending(now) }

        // فحص الرسائل العالقة في SENDING (موت العملية أثناء الإرسال) — فردي + مجموعات
        val stuck = try { dao.getUnsentOutgoing() } catch (e: Exception) { emptyList() }
        var redrivenGroups = 0
        var redrivenP2P = 0
        if (stuck.isNotEmpty()) {
            Log.i(TAG, "Found ${stuck.size} stuck SENDING (1:1 + groups) — redriving idempotently")
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; explicitNulls = false }
            val dir = java.io.File(app.filesDir, "red_group_outbox")
            for (row in stuck) {
                // نضج 60s حتى لا نسبق إرسالاً حياً (SENDING→QUEUED→SERVER_ACK)
                if (now - row.createdAt < 60_000L) continue
                val isGroup = com.red.sovereign.core.RedConnectionService.isGroupConversation(row.conversationId)
                if (!isGroup) {
                    redrivenP2P++
                    continue
                }
                // مجموعة: أعد الإرسال بذات clientId/uuid من الملف المحفوظ — لا فقدان بعد الموت
                try {
                    val f = java.io.File(dir, "${row.id}.json")
                    if (!f.exists()) continue // بلا JSON: يُبقى SENDING للفحص اليدوي — لا SENT زائف
                    val obj = org.json.JSONObject(f.readText())
                    val groupJson = obj.optString("groupJson")
                    if (groupJson.isBlank()) { f.delete(); continue }
                    // إن اكتملت فعلاً (SERVER_ACK) احذف الملف — idempotent
                    val cur = runCatching { dao.getLocalHistoryEntry(row.id)?.status }.getOrNull().orEmpty().uppercase()
                    if (cur in setOf("SERVER_ACK", "SENT", "DELIVERED", "READ")) { f.delete(); continue }
                    val group = runCatching { json.decodeFromString<com.red.sovereign.groups.Group>(groupJson) }.getOrNull() ?: continue
                    when (obj.optString("kind")) {
                        "TEXT" -> {
                            val text = obj.optString("text")
                            if (text.isBlank()) continue
                            if (obj.optBoolean("isRich", false)) {
                                val rich = com.red.sovereign.core.RichMessage.decode(text.toByteArray(Charsets.UTF_8))
                                if (rich != null) com.red.sovereign.core.RedConnectionService.sendGroupRichText(app, group, rich, row.id)
                                else com.red.sovereign.core.RedConnectionService.sendGroupText(app, group, text, row.id)
                            } else {
                                com.red.sovereign.core.RedConnectionService.sendGroupText(app, group, text, row.id)
                            }
                            redrivenGroups++
                        }
                        else -> {
                            val b64 = obj.optString("payloadB64")
                            if (b64.isBlank()) continue
                            val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                            com.red.sovereign.core.RedConnectionService.sendGroupPayload(app, group, obj.optString("type", "TEXT"), bytes, row.id)
                            redrivenGroups++
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "group redrive failed for ${row.id}", e) }
            }
            if (redrivenP2P > 0) {
                // الفردي يُعاد عبر OutboxRetryWorker (نفس uuid) + إيقاظ الخدمة
                runCatching { com.red.sovereign.core.outbox.OutboxRetryWorker.schedule(app) }
                runCatching { com.red.sovereign.core.RedConnectionService.start(app) }
            }
            if (redrivenGroups > 0) Log.i(TAG, "Redrove $redrivenGroups group message(s) with same clientId (idempotent)")
        }

        // فحص الرسائل المنتهية التي لم تُحذف بعد ( MessageCleanupWorker قد يكون متأخراً)
        val expiredNotDeleted = try {
            dao.allRichHistory().count { entity ->
                val expiresAt = runCatching {
                    com.red.sovereign.core.RichMessage.decode(entity.encryptedPlaintext)?.expiresAt
                }.getOrNull()
                expiresAt != null && expiresAt <= now
            }
        } catch (e: Exception) { 0 }

        if (expiredNotDeleted > 0) {
            Log.i(TAG, "Found $expiredNotDeleted expired messages pending deletion")
        }

        Log.i(TAG, "Recovery check complete: messages=$totalMessages, history=$totalHistory, diff=$diff, stuck=${stuck.size}, redrivenP2P=$redrivenP2P, redrivenGroups=$redrivenGroups")
        Result.success()
    }.getOrElse { error ->
        Log.w(TAG, "Recovery check failed — will retry", error)
        Result.retry()
    }

    companion object {
        private const val TAG = "MessageRecoveryWorker"
        private const val UNIQUE_NAME = "message-recovery"
        private const val INTERVAL_MINUTES = 30L

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<MessageRecoveryWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            ).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
