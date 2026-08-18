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
            processIncomingSms(sms, context)
        }
    }

    private fun processIncomingSms(sms: SmsMessage, context: Context) {
        val sender = sms.originatingAddress
        val body = sms.messageBody

        log("Incoming SMS from: $sender - Body: $body")

        // Create intent to deliver to activity/ViewModel
        val resultIntent = android.content.Intent("com.red.sovereign.SMS_RECEIVED")
        resultIntent.putExtra("sender", sender)
        resultIntent.putExtra("body", body)

        // Deliver locally (or could send to MainActivity via Broadcast)
        resultIntent.putExtra("context_package", context.packageName)

        // Send local broadcast
        try {
            context.sendBroadcast(resultIntent)
        } catch (e: SecurityException) {
            log("Security exception sending SMS broadcast: ${e.message}")
        }
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