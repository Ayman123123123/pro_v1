package com.red.sovereign.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SmsReceiver - Handles incoming SMS messages from the carrier/SIM card.
 *
 * Receives android.provider.Telephony.SMS_RECEIVED intents and can:
 * - Extract SMS message content
 * - Pass to ViewModel or EventBus for UI display
 * - Auto-respond if configured
 *
 * Requires permissions:
 * - android.permission.RECEIVE_SMS
 * - android.permission.READ_SMS
 * - android.permission.SEND_SMS (for sending)
 */
class SmsReceiver : BroadcastReceiver() {

    private val TAG = "SmsReceiver"

    // SMS action string
    private val SMS_ACTION = "android.provider.Telephony.SMS_RECEIVED"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return

        // Check action
        if (intent.action != SMS_ACTION) return

        // Extract messages using the proper API
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        messages?.forEach { sms ->
            processIncomingSms(sms, context, intent)
        }
    }

    private fun processIncomingSms(sms: SmsMessage, context: Context, intent: Intent?) {
        val sender = sms.originatingAddress
        val body = sms.messageBody
        val subId = intent?.getIntExtra("subscription", -1)
            ?: intent?.getIntExtra(android.telephony.SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)
            ?: -1

        // Extract OTP if present
        val otp = extractOtp(body)

        // لا تُسجَّل محتويات الرسائل في Logcat — كانت تسرّب نصوص SMS حساسة
        log("Incoming SMS from: $sender (subId=$subId, otp=${otp != null}, ${body?.length ?: 0} chars)")

        // Create intent to deliver to activity/ViewModel
        val resultIntent = android.content.Intent("com.red.sovereign.SMS_RECEIVED").apply {
            putExtra("sender", sender)
            putExtra("body", body)
            putExtra("subscriptionId", subId)
            if (otp != null) {
                putExtra("otp", otp)
            }
            putExtra("context_package", context.packageName)
        }

        // Send local broadcast
        try {
            context.sendBroadcast(resultIntent)
        } catch (e: SecurityException) {
            log("Security exception sending SMS broadcast: ${e.message}")
        }
    }

    private fun extractOtp(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val otpRegex = Regex("""\b(\d{4,6})\b""")
        val match = otpRegex.find(body) ?: return null
        val lower = body.lowercase()
        val hasContext = lower.contains("otp") || lower.contains("code") ||
            lower.contains("verification") || lower.contains("رمز") ||
            lower.contains("تحقق") || lower.contains("تأكيد") ||
            lower.contains("activation") || lower.contains("PIN")
        return if (hasContext || match.value.length in 4..6) match.value else null
    }

    /**
     * Send SMS message via SmsManager.
     * @param phoneNumber Recipient phone number (e.g., "+967777123456")
     * @param message SMS message body
     * @return true if send intent was sent, false otherwise
     */
    suspend fun sendSms(phoneNumber: String, message: String): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                val smsManager = SmsManager.getDefault()
                // Split message if longer than 160 chars (for Unicode)
                if (message.length > 160) {
                    val parts = smsManager.divideMessage(message)
                    smsManager.sendMultipartTextMessage(
                        phoneNumber,
                        null,
                        parts,
                        null,
                        null
                    )
                } else {
                    smsManager.sendTextMessage(
                        phoneNumber,
                        null, // scCentroAddress
                        message,
                        null, // sentPendingIntent
                        null  // deliveryPendingIntent
                    )
                }
                log("SMS send intent to $phoneNumber: ${message.length} chars")
                true
            } catch (e: Exception) {
                log("Failed to send SMS to $phoneNumber: ${e.message}")
                false
            }
        }
    }

    private fun log(message: String) {
        Log.d(TAG, message)
    }
}
