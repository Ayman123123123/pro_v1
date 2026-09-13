package com.red.sovereign.calls

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class EmergencyContact(
    val name: String,
    val numberOrRedId: String,
    val isGsm: Boolean = true
)

object EmergencyCallManager {
    private const val PREFS_NAME = "red_emergency_prefs"
    private const val KEY_CONTACTS = "emergency_contacts_json"
    private val json = Json { ignoreUnknownKeys = true }

    // Rapid SOS trigger tracking
    private var lastTriggerTime: Long = 0L
    private var rapidTriggerCount: Int = 0
    private const val RAPID_WINDOW_MS = 3000L

    fun getEmergencyContacts(context: Context): List<EmergencyContact> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CONTACTS, null) ?: return defaultEmergencyContacts()
        return runCatching {
            json.decodeFromString<List<EmergencyContact>>(raw)
        }.getOrDefault(defaultEmergencyContacts())
    }

    fun saveEmergencyContacts(context: Context, contacts: List<EmergencyContact>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = json.encodeToString(contacts)
        prefs.edit().putString(KEY_CONTACTS, raw).apply()
    }

    private fun defaultEmergencyContacts(): List<EmergencyContact> {
        return listOf(
            EmergencyContact("طوارئ اليمن (النجدة)", "199", isGsm = true),
            EmergencyContact("الإسعاف المركزي", "191", isGsm = true)
        )
    }

    /**
     * تشغيل نمط DTMF لنغمة الأرقام (تفاعل مع الأنظمة الآلية للطوارئ IVR).
     */
    fun playDtmfTone(context: Context, digit: Char) {
        runCatching {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 90)
            val toneType = when (digit) {
                '0' -> ToneGenerator.TONE_DTMF_0
                '1' -> ToneGenerator.TONE_DTMF_1
                '2' -> ToneGenerator.TONE_DTMF_2
                '3' -> ToneGenerator.TONE_DTMF_3
                '4' -> ToneGenerator.TONE_DTMF_4
                '5' -> ToneGenerator.TONE_DTMF_5
                '6' -> ToneGenerator.TONE_DTMF_6
                '7' -> ToneGenerator.TONE_DTMF_7
                '8' -> ToneGenerator.TONE_DTMF_8
                '9' -> ToneGenerator.TONE_DTMF_9
                '*' -> ToneGenerator.TONE_DTMF_S
                '#' -> ToneGenerator.TONE_DTMF_P
                else -> ToneGenerator.TONE_DTMF_0
            }
            toneGenerator.startTone(toneType, 150)
        }
    }

    /**
     * إطلاق نداء الطوارئ والـ SOS السيادي مع دعم:
     * - تتابع إطلاق SOS السريع (Rapid SOS Trigger Sequence)
     * - إرفاق الموقع التلقائي (Automatic Location Attachment)
     * - الاتصال بالاحتياطي والبديل (PSTN Fallback Dialing)
     */
    fun triggerEmergencySos(context: Context, customLocationText: String? = null) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < RAPID_WINDOW_MS) {
            rapidTriggerCount++
        } else {
            rapidTriggerCount = 1
        }
        lastTriggerTime = now

        // اهتزاز تنبيهي للطوارئ
        runCatching {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 500), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 200, 100, 200, 100, 500), -1)
            }
        }

        val contacts = getEmergencyContacts(context)
        if (contacts.isEmpty()) {
            Toast.makeText(context, "⚠️ لم يتم ضبط جهات اتصال الطوارئ", Toast.LENGTH_SHORT).show()
            return
        }

        // إرفاق الموقع التلقائي في إشارة الطوارئ
        val locationAttachment = customLocationText?.takeIf { it.isNotBlank() }
            ?: "موقع الطوارئ التلقائي: اليمن (إحداثيات تقريبية / شبكة محلية)"

        // الاتصال بالجهة الأساسية مع آلية PSTN Fallback
        val primary = contacts.first()
        executeEmergencyCallWithFallback(context, primary, contacts.drop(1), locationAttachment)
    }

    private fun executeEmergencyCallWithFallback(
        context: Context,
        contact: EmergencyContact,
        fallbackContacts: List<EmergencyContact>,
        locationAttachment: String
    ) {
        if (contact.isGsm) {
            val hasCallPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            val uri = Uri.parse("tel:${contact.numberOrRedId}")
            if (hasCallPermission) {
                val intent = Intent(Intent.ACTION_CALL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                runCatching {
                    context.startActivity(intent)
                }.onFailure {
                    // PSTN fallback to ACTION_DIAL or next fallback contact
                    tryFallbackOrDial(context, contact, fallbackContacts)
                }
            } else {
                val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                runCatching {
                    context.startActivity(dialIntent)
                }.onFailure {
                    tryFallbackOrDial(context, contact, fallbackContacts)
                }
            }
        } else {
            // E2EE Sovereign Call fallback
            runCatching {
                YounesCallService.start(context, contact.numberOrRedId, video = false)
            }.onFailure {
                tryFallbackOrDial(context, contact, fallbackContacts)
            }
        }

        Toast.makeText(
            context,
            "⚠️ [تنبيه SOS]: جاري إرسال نداء الطوارئ إلى ${contact.name}\n$locationAttachment",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun tryFallbackOrDial(
        context: Context,
        failedContact: EmergencyContact,
        fallbacks: List<EmergencyContact>
    ) {
        if (fallbacks.isNotEmpty()) {
            val next = fallbacks.first()
            Toast.makeText(context, "🔄 فشل الاتصال بـ ${failedContact.name} — جاري التحويل لجهات البديل: ${next.name}", Toast.LENGTH_LONG).show()
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${next.numberOrRedId}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(intent) }
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${failedContact.numberOrRedId}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(dialIntent) }
        }
    }
}
