package com.red.sovereign.calls

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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

    fun triggerEmergencySos(context: Context, customLocationText: String? = null) {
        val contacts = getEmergencyContacts(context)
        val primary = contacts.firstOrNull()
        if (primary == null) {
            Toast.makeText(context, "لم يتم ضبط جهات اتصال الطوارئ", Toast.LENGTH_SHORT).show()
            return
        }

        if (primary.isGsm) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${primary.numberOrRedId}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching {
                context.startActivity(intent)
            }.onFailure {
                // Fallback to DIAL
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${primary.numberOrRedId}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            }
        } else {
            // E2EE Sovereign Call
            YounesCallService.start(context, primary.numberOrRedId, video = false)
        }

        Toast.makeText(context, "⚠️ تم إطلاق نداء الطوارئ والـ SOS السيادي إلى: ${primary.name}", Toast.LENGTH_LONG).show()
    }
}
