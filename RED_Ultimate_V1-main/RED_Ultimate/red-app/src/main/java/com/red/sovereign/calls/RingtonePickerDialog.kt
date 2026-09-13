package com.red.sovereign.calls

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.settings.SettingsViewModel
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors

/**
 * Resolves stored ringtone URI string to actual [Uri], falling back to default ringtone if blank or invalid.
 */
fun resolveCallRingtoneUri(context: Context, stored: String): Uri {
    if (stored.isBlank()) return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    return runCatching { Uri.parse(stored) }.getOrElse {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    }
}

/** Returns human-readable title for the ringtone URI. */
fun callRingtoneTitle(context: Context, uri: Uri?): String {
    if (uri == null) return "الافتراضية"
    return runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }
        .getOrNull()?.takeIf { it.isNotBlank() } ?: "نغمة مخصصة"
}

/**
 * Setting row component for configuring RED call ringtone using system ringtone picker.
 */
@Composable
fun CallRingtoneSettingRow(settings: SettingsViewModel) {
    val context = LocalContext.current
    val stored = settings.state.callRingtoneUri
    val currentUri = remember(stored) { resolveCallRingtoneUri(context, stored) }
    var preview: Ringtone? by remember { mutableStateOf(null) }
    var playingUri: Uri? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { preview?.stop() }
            preview = null
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked: Uri? = if (android.os.Build.VERSION.SDK_INT >= 33) {
                result.data?.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (picked != null) {
                runCatching { preview?.stop() }
                preview = null
                playingUri = null
                settings.setCallRingtoneUri(picked.toString())
            }
        }
    }

    fun launchPicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "نغمة مكالمة RED Sovereign")
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        }
        pickerLauncher.launch(intent)
    }

    fun togglePreview() {
        if (playingUri != null) {
            runCatching { preview?.stop() }
            preview = null
            playingUri = null
        } else {
            runCatching { preview?.stop() }
            preview = RingtoneManager.getRingtone(context, currentUri)?.also { it.play() }
            playingUri = currentUri
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = ::launchPicker),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, "نغمة المكالمة", tint = AqyalGold, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("نغمة المكالمة", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        callRingtoneTitle(context, currentUri),
                        color = AqyalGold, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::togglePreview) {
                    Icon(
                        if (playingUri != null) Icons.Default.Stop else Icons.Default.PlayArrow,
                        if (playingUri != null) "إيقاف المعاينة" else "معاينة النغمة",
                        tint = Color.White
                    )
                }
                TextButton(onClick = ::launchPicker) { Text("اختيار", color = AqyalGold) }
            }
        }
    }
}

/**
 * Ringtone Picker Dialog wrapper providing ringtone selection and vibration toggle.
 */
@Composable
fun RingtonePickerDialog(settings: SettingsViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نغمة المكالمة والرنين") },
        text = {
            Column {
                CallRingtoneSettingRow(settings)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("اهتزاز مع الرنين", fontSize = 14.sp, color = Color.White)
                    Switch(
                        checked = settings.state.callVibration,
                        onCheckedChange = settings::setCallVibration
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم", color = AqyalGold) } }
    )
}
