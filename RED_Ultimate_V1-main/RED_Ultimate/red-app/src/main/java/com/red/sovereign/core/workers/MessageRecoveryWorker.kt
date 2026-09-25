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

        // القاطع المشترك: إن كان مفتوحاً أجّل (الدوري سيعود بعد المهلة) — لا عاصفة redrive.
        com.red.sovereign.core.outbox.OutboxRepository.loadPersisted(app)
        if (com.red.sovereign.core.outbox.OutboxRepository.globalIsOpen()) {
            val remain = com.red.sovereign.core.outbox.OutboxRepository.globalRemainingSec()
            Log.i(TAG, "Circuit breaker open — deferring recovery (${remain}s remain, half-open probe next)")
            return@runCatching Result.success()
        }

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
        // الإرسال حتى الإقرار: SENDING + QUEUED (بلا SERVER_ACK) — DAO يجلب SENDING فقط
        // فيُدمج مع QUEUED خاماً (نفس نهج الخدمة) وإلا ضاع QUEUED بعد الموت.
        val stuckSending = try { dao.getUnsentOutgoing() } catch (e: Exception) { emptyList() }
        val queuedExtra = runCatching {
            val c = db.openHelper.readableDatabase.query(
                "SELECT id FROM local_history WHERE outgoing = 1 AND status = 'QUEUED' ORDER BY createdAt ASC LIMIT 100", null
            )
            val ids = c.use {
                val out = ArrayList<String>()
                val idx = it.getColumnIndexOrThrow("id")
                while (it.moveToNext()) out.add(it.getString(idx))
                out
            }
            val known = stuckSending.map { it.id }.toSet()
            ids.filterNot { known.contains(it) }.mapNotNull { id ->
                runCatching { dao.getLocalHistoryEntry(id) }.getOrNull()
            }
        }.getOrDefault(emptyList())
        val stuck = (stuckSending + queuedExtra).sortedBy { it.createdAt }.take(100)
        var redrivenGroups = 0
        var redrivenP2P = 0
        if (stuck.isNotEmpty()) {
            Log.i(TAG, "Found ${stuck.size} stuck unacked (SENDING=${stuckSending.size} QUEUED=${queuedExtra.size}) — redriving idempotently")
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; explicitNulls = false }
            val dir = java.io.File(app.filesDir, "red_group_outbox")
            for (row in stuck) {
                // نضج: SENDING دقيقة، QUEUED دقيقة ونصف (دورة ACK الحية أطول للـ QUEUED المبطّن)
                val maturity = if (row.status == "QUEUED") 90_000L else 60_000L
                if (now - row.createdAt < maturity) continue
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
                // الفردي: redrive الخدمة (SENDING+QUEUED بذات uuid) + outbox — بدء الخدمة
                // وحده لا يدفع (redrive فقط في onCreate/ACTION_REDRIVE_STUCK) فاطلبها صراحةً.
                runCatching { com.red.sovereign.core.outbox.OutboxRetryWorker.schedule(app) }
                requestServiceRedrive(app)
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
        private const val ONCE_NAME = "message-recovery-once"
        private const val INTERVAL_MINUTES = 30L

        /** يوقظ الدفع الفعلي (redrive SENDING+QUEUED) — بدء الخدمة وحده لا يدفع. */
        private fun requestServiceRedrive(context: Context) {
            runCatching {
                val intent = android.content.Intent(context, com.red.sovereign.core.RedConnectionService::class.java)
                    .setAction(com.red.sovereign.core.RedConnectionService.ACTION_REDRIVE_STUCK)
                context.startForegroundService(intent)
            }.onFailure {
                runCatching { com.red.sovereign.core.RedConnectionService.start(context) }
            }
        }

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<MessageRecoveryWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            ).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            // تشغيل فوري بعد الإقلاع/الموت (الدوري وحده يتأخر حتى 30د) — idempotent.
            runCatching {
                val once = androidx.work.OneTimeWorkRequestBuilder<MessageRecoveryWorker>()
                    .setConstraints(constraints)
                    .setInitialDelay(30, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONCE_NAME,
                    androidx.work.ExistingWorkPolicy.KEEP,
                    once
                )
            }
        }
    }
}
