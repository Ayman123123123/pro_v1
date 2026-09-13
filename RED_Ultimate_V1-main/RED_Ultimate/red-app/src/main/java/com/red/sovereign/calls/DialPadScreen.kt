package com.red.sovereign.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.red.sovereign.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val YEMENI_NUMBER_PATTERN = Regex("""^\+967(70|71|73|77|78)\d{7}$""")

private fun formatYemeniNumber(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        raw.startsWith("+967") -> raw
        digits.startsWith("967") -> "+$digits"
        digits.startsWith("00967") -> "+${digits.drop(2)}"
        else -> "+967$digits"
    }
}

private fun isValidYemeniNumber(number: String): Boolean =
    YEMENI_NUMBER_PATTERN.matches(number)

@Composable
fun DialPadScreen(
    onPstnCall: (String) -> Unit,
    onVoipCall: (String) -> Unit,
    pstnEnabled: Boolean,
    usedToday: Int,
    dailyLimit: Int,
) {
    var dialedNumber by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // كشف المشغل اليمني بشكل حي
    val operatorInfo = remember(dialedNumber) {
        if (dialedNumber.isNotBlank()) YemeniOperatorDetector.getOperatorInfo(dialedNumber) else null
    }

    val errEmpty = stringResource(R.string.dial_error_empty)
    val errPstnRequired = stringResource(R.string.dial_error_pstn_required)
    val errInvalidYemeni = stringResource(R.string.dial_error_invalid_yemeni)
    val errDailyLimit = stringResource(R.string.dial_error_daily_limit, usedToday, dailyLimit)
    val txtUsed = stringResource(R.string.dial_used, usedToday, dailyLimit)
    val cdVoip = stringResource(R.string.dial_voip_cd)
    val cdPstn = stringResource(R.string.dial_pstn_cd)
    val labelNumber = stringResource(R.string.label_number)
    val cdDelete = stringResource(R.string.dial_delete)

    var pendingPstnNumber by remember { mutableStateOf<String?>(null) }
    val pstnAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingPstnNumber
        pendingPstnNumber = null
        if (granted && pending != null) {
            onPstnCall(pending)
            dialedNumber = ""
            errorMessage = null
        } else if (!granted) {
            errorMessage = "إذن الميكروفون مطلوب لمكالمة PSTN"
            android.widget.Toast.makeText(context, "الميكروفون مطلوب للمكالمة الهاتفية", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun appendDigit(digit: String) {
        dialedNumber += digit
        errorMessage = null
        if (digit.length == 1) {
            EmergencyCallManager.playDtmfTone(context, digit[0])
        }
    }

    fun deleteLastDigit() {
        if (dialedNumber.isNotEmpty()) {
            dialedNumber = dialedNumber.dropLast(1)
            errorMessage = null
        }
    }

    fun clearNumber() {
        dialedNumber = ""
        errorMessage = null
    }

    fun handlePstnCall() {
        val number = dialedNumber.trim()
        if (number.isEmpty()) {
            errorMessage = errEmpty
            return
        }
        if (!pstnEnabled) {
            errorMessage = errPstnRequired
            return
        }
        val formatted = formatYemeniNumber(number)
        if (!isValidYemeniNumber(formatted)) {
            errorMessage = errInvalidYemeni
            return
        }
        if (dailyLimit > 0 && usedToday >= dailyLimit) {
            errorMessage = errDailyLimit
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingPstnNumber = formatted
            errorMessage = null
            val activity = context as? android.app.Activity
            if (activity != null && androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)) {
                android.widget.Toast.makeText(context, "الميكروفون مطلوب لمكالمة PSTN — يرجى السماح", android.widget.Toast.LENGTH_LONG).show()
            }
            pstnAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        onPstnCall(formatted)
        dialedNumber = ""
        errorMessage = null
    }

    fun handleVoipCall() {
        val number = dialedNumber.trim()
        if (number.isEmpty()) {
            errorMessage = errEmpty
            return
        }
        onVoipCall(number)
        dialedNumber = ""
        errorMessage = null
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = { handleVoipCall() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Wifi, contentDescription = cdVoip)
                }
                SmallFloatingActionButton(
                    onClick = { if (pstnEnabled) handlePstnCall() },
                    containerColor = if (pstnEnabled) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (pstnEnabled) MaterialTheme.colorScheme.onTertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = cdPstn)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── شارة كشف المشغل اليمني الحي ──
            if (operatorInfo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(operatorInfo.brandColor)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = operatorInfo.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = operatorInfo.brandColor
                        )
                    }
                    Text(
                        text = "${operatorInfo.technology} • ${if (operatorInfo.isMobile) "محمول" else "ثابت"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Display / number field ──
            OutlinedTextField(
                value = dialedNumber,
                onValueChange = { },
                readOnly = true,
                label = { Text(labelNumber) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                ),
                trailingIcon = {
                    if (dialedNumber.isNotEmpty()) {
                        IconButton(onClick = { deleteLastDigit() }) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = cdDelete)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Error message ──
            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Dialpad grid ──
            val padRows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("*", "0", "#"),
            )

            padRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { key ->
                        OutlinedButton(
                            onClick = { appendDigit(key) },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(text = key, fontSize = 22.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ── FAB labels ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (pstnEnabled) txtUsed else errPstnRequired,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pstnEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "مكالمة RED",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = "هاتف يمني",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (pstnEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
