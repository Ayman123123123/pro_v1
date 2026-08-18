package com.red.sovereign.calls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val YEMENI_NUMBER_PATTERN = Regex("""^\+967[71379]\d{6}$""")

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

    fun appendDigit(digit: String) {
        dialedNumber += digit
        errorMessage = null
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
            errorMessage = "Enter a number"
            return
        }
        if (!pstnEnabled) {
            errorMessage = "PSTN plan required"
            return
        }
        val formatted = formatYemeniNumber(number)
        if (!isValidYemeniNumber(formatted)) {
            errorMessage = "Invalid Yemeni number"
            return
        }
        if (dailyLimit > 0 && usedToday >= dailyLimit) {
            errorMessage = "Daily limit reached ($usedToday/$dailyLimit)"
            return
        }
        onPstnCall(formatted)
        dialedNumber = ""
        errorMessage = null
    }

    fun handleVoipCall() {
        val number = dialedNumber.trim()
        if (number.isEmpty()) {
            errorMessage = "Enter a number"
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
                    Icon(Icons.Filled.Wifi, contentDescription = "VoIP call")
                }
                SmallFloatingActionButton(
                    onClick = { if (pstnEnabled) handlePstnCall() },
                    containerColor = if (pstnEnabled) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (pstnEnabled) MaterialTheme.colorScheme.onTertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = "PSTN call")
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
            // ── Display / number field ──
            OutlinedTextField(
                value = dialedNumber,
                onValueChange = { },
                readOnly = true,
                label = { Text("Number") },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                ),
                trailingIcon = {
                    if (dialedNumber.isNotEmpty()) {
                        IconButton(onClick = { deleteLastDigit() }) {
                            Icon(Icons.Filled.Backspace, contentDescription = "Delete")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Error message ──
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
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
                    // PSTN daily-limit text
                    Text(
                        text = if (pstnEnabled) "Used: $usedToday / $dailyLimit"
                            else "PSTN plan required",
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
