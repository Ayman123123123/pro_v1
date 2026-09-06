package com.red.sovereign.calls

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.linphone.core.TransportType

private val Context.pstnDataStore: DataStore<Preferences> by preferencesDataStore(name = "red_pstn_sip")

/**
 * يخزّن إعدادات خط PSTN (حساب SIP على UC200 Pro) محلياً عبر DataStore،
 * بديلاً عن ترميزها في الكود المصدري. القيم تُحمَّل فوق الافتراضات عند الإقلاع
 * وتُحفظ عندما يدخلها المستخدم/الأدمن من شاشة الإعدادات.
 */
object PstnSettingsRepository {
    private val EXT = stringPreferencesKey("pstn_extension")
    private val PWD = stringPreferencesKey("pstn_password")
    private val HOST = stringPreferencesKey("pstn_host")
    private val PORT = intPreferencesKey("pstn_port")
    private val TRANSPORT = stringPreferencesKey("pstn_transport")

    suspend fun load(context: Context): PstnSipData =
        context.pstnDataStore.data.map { p ->
            PstnSipData(
                extension = p[EXT] ?: "",
                password = p[PWD] ?: "",
                host = p[HOST] ?: "",
                port = p[PORT] ?: 5060,
                transport = p[TRANSPORT]?.let { runCatching { TransportType.valueOf(it) }.getOrDefault(TransportType.Udp) }
                    ?: TransportType.Udp
            )
        }.first()

    suspend fun save(context: Context, data: PstnSipData) {
        context.pstnDataStore.edit { p ->
            p[EXT] = data.extension
            p[PWD] = data.password
            p[HOST] = data.host
            p[PORT] = data.port
            p[TRANSPORT] = data.transport.name
        }
    }
}

data class PstnSipData(
    val extension: String,
    val password: String,
    val host: String,
    val port: Int,
    val transport: TransportType
)
