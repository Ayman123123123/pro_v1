package com.red.features.pstn

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * RED Sovereign PSTN & VoIP Dialer Screen
 * Features Dual Engine: VoIP App (Blue) vs Dinstar Yemeni Line (Gold).
 */
@Composable
fun PstnDialerScreen() {
    var phoneNumber by remember { mutableStateOf("") }
    var isVoipMode by remember { mutableStateOf(true) } // true = VoIP App (Blue), false = Dinstar Yemeni Line (Gold)

    val activeColor by animateColorAsState(
        targetValue = if (isVoipMode) Color(0xFF1E88E5) else Color(0xFFF4B400),
        label = "ModeColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // DINSTAR SIM Gateway Status Banner (When Gold Mode is active)
        if (!isVoipMode) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF4B400).copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.SimCard, contentDescription = null, tint = Color(0xFFF4B400))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("خطي اليمني (DINSTAR Gateway)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFF4B400))
                        Text("شبكة سبأفون • متصل • الإشارة: 85%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    }
                    Badge(containerColor = Color(0xFF4CAF50)) { Text("جاهز", color = Color.White, fontSize = 10.sp) }
                }
            }
        }

        // Mode Toggle Switch (Zangi VoIP vs Dinstar PSTN)
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabButton(
                title = "اتصال التطبيق (VoIP)",
                icon = Icons.Default.Cloud,
                isSelected = isVoipMode,
                activeColor = Color(0xFF1E88E5)
            ) { isVoipMode = true }

            TabButton(
                title = "خطي اليمني",
                icon = Icons.Default.PhoneInTalk,
                isSelected = !isVoipMode,
                activeColor = Color(0xFFF4B400)
            ) { isVoipMode = false }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Phone Number Display Field
        Text(
            text = phoneNumber.ifEmpty { "أدخل الرقم..." },
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            color = if (phoneNumber.isEmpty()) Color.Gray else MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Dialpad Grid (3x4)
        val buttons = listOf(
            listOf("1", ""), listOf("2", "ABC"), listOf("3", "DEF"),
            listOf("4", "GHI"), listOf("5", "JKL"), listOf("6", "MNO"),
            listOf("7", "PQRS"), listOf("8", "TUV"), listOf("9", "WXYZ"),
            listOf("*", ""), listOf("0", "+"), listOf("#", "")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            buttons.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (num, sub) ->
                        RedDialButton(number = num, sub = sub) {
                            phoneNumber += num
                        }
                    }
                }
            }
        }

        // Action Buttons: Call & Delete
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spacer to balance layout
            Spacer(modifier.size(64.dp))

            // Call Button (Colored based on mode)
            FloatingActionButton(
                onClick = {
                    if (phoneNumber.isNotEmpty()) {
                        // Trigger call via VoIP or Dinstar
                    }
                },
                containerColor = activeColor,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (isVoipMode) Icons.Default.Call else Icons.Default.PhoneForwarded,
                    contentDescription = "اتصال",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Backspace / Delete Button
            IconButton(
                onClick = {
                    if (phoneNumber.isNotEmpty()) {
                        phoneNumber = phoneNumber.dropLast(1)
                    }
                },
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "حذف",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun RowScope.TabButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else Color.Transparent,
        label = "TabBg"
    )
    val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, color = contentColor, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
    }
}
