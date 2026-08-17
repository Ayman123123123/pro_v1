package com.red.sovereign.features.dinstar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.CairoFamily
import com.red.sovereign.ui.theme.SovereignColors

@Composable
fun DinstarSmsScreen(
    viewModel: DinstarViewModel,
    onBack: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var recipientNumber by remember { mutableStateOf("") }

    val messages = viewModel.smsHistory.collectAsState(initial = emptyList()).value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SovereignColors.ObsidianDeep)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SovereignColors.ObsidianDeep)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SovereignColors.SurfaceCard)
            ) {
                Icon(Icons.Rounded.ArrowBack, "رجوع", tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "رسائل GSM النصية (SMS)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = CairoFamily
                )
                Text("عبر بوابة DINSTAR المركزية", color = SovereignColors.GoldNeon, fontSize = 11.sp)
            }
        }

        // SMS List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            reverseLayout = true
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Sms, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            Text("لا توجد رسائل سابقة", color = Color.Gray, fontSize = 14.sp)
                            Text("أدخل الرقم واكتب الرسالة للإرسال عبر شرائح GSM", color = Color.DarkGray, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(messages) { msg ->
                    val isMe = msg.direction == "OUT"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isMe) SovereignColors.Gold else SovereignColors.SurfaceCard)
                                .border(
                                    1.dp,
                                    if (isMe) SovereignColors.GoldNeon else SovereignColors.GlassBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (isMe) "إلى: ${msg.number}" else "من: ${msg.number}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMe) SovereignColors.ObsidianDeep.copy(alpha = 0.8f) else SovereignColors.GoldNeon
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = msg.content,
                                    color = if (isMe) SovereignColors.ObsidianDeep else Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Input Area
        Surface(
            color = SovereignColors.SurfaceCard,
            tonalElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier.border(1.dp, SovereignColors.GlassBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = recipientNumber,
                    onValueChange = { recipientNumber = it },
                    placeholder = { Text("رقم المستلم (مثال: 777123456)", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        unfocusedBorderColor = SovereignColors.GlassBorder,
                        focusedBorderColor = SovereignColors.GoldNeon
                    ),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("اكتب نص الرسالة...", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            unfocusedBorderColor = SovereignColors.GlassBorder,
                            focusedBorderColor = SovereignColors.GoldNeon
                        )
                    )

                    Spacer(Modifier.width(8.dp))

                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank() && recipientNumber.isNotBlank()) {
                                viewModel.sendSms(recipientNumber.trim(), messageText.trim())
                                messageText = ""
                            }
                        },
                        containerColor = SovereignColors.Gold,
                        contentColor = SovereignColors.ObsidianDeep,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Rounded.Send, "إرسال")
                    }
                }
            }
        }
    }
}
