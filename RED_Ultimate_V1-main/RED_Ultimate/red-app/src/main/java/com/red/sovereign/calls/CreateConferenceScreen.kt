package com.red.sovereign.calls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.YounesEmerald
import java.util.UUID

/**
 * شاشة إنشاء مؤتمر/مساحة صوتية — نمط Twitter/X Spaces.
 *
 * يتيح للمستخدم:
 * • اختيار النوع: مساحة صوتية فقط  أو  مؤتمر فيديو كامل
 * • تعيين عنوان ووصف
 * • ضبط الخصوصية: عام / مقيّد بالمجموعة / خاص
 * • اختيار متحدثين أوليين من قائمة الأصدقاء
 * • إطلاق المؤتمر مباشرةً
 */
@Composable
fun CreateConferenceScreen(
    friendIds: List<String> = emptyList(),
    friendNames: List<String> = emptyList(),
    myUserId: String = "",
    onBack: () -> Unit = {},
    onLaunched: (roomId: String) -> Unit = {}
) {
    val context = LocalContext.current

    // ── State ─────────────────────────────────────────────────────────────
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isVideoMode by remember { mutableStateOf(false) }
    var isPrivate by remember { mutableStateOf(false) }
    val selectedSpeakers = remember { mutableStateListOf<String>() }

    // زوج الأصدقاء (id → name)
    val friends = remember(friendIds, friendNames) {
        friendIds.mapIndexed { i, id -> id to friendNames.getOrElse(i) { id } }
    }

    // ── Layout ────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF020617))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                }
                Text("إنشاء جلسة", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                // Launch button
                Button(
                    onClick = {
                        val roomId = UUID.randomUUID().toString()
                        ConferenceService.join(
                            context = context,
                            roomId = roomId,
                            userId = myUserId,
                            video = isVideoMode,
                            asHost = true
                        )
                        // دعوة المتحدثين المختارين
                        selectedSpeakers.forEach { speakerId ->
                            ConferenceService.invite(context, roomId, speakerId, myUserId, isVideoMode)
                        }
                        onLaunched(roomId)
                    },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YounesEmerald,
                        disabledContainerColor = YounesEmerald.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(38.dp).border(if (title.isNotBlank()) 1.dp else 0.dp, if (title.isNotBlank()) Color.White.copy(0.5f) else Color.Transparent, RoundedCornerShape(20.dp))
                ) {
                    Text("إطلاق الآن", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Divider(color = Color.White.copy(0.08f))

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(vertical = 20.dp)
            ) {
                // ── نوع الجلسة ──────────────────────────────────────────────
                item {
                    SectionLabel("نوع الجلسة")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SessionTypeCard(
                            icon = Icons.Default.Headset,
                            label = "مساحة صوتية",
                            subtitle = "صوت فقط — مثل X Spaces",
                            selected = !isVideoMode,
                            accentColor = Color(0xFFA78BFA),
                            modifier = Modifier.weight(1f),
                            onClick = { isVideoMode = false }
                        )
                        SessionTypeCard(
                            icon = Icons.Default.Videocam,
                            label = "مؤتمر فيديو",
                            subtitle = "فيديو + صوت — مثل Zoom",
                            selected = isVideoMode,
                            accentColor = YounesEmerald,
                            modifier = Modifier.weight(1f),
                            onClick = { isVideoMode = true }
                        )
                    }
                }

                // ── العنوان ──────────────────────────────────────────────────
                item {
                    SectionLabel("عنوان الجلسة *")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 60) title = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("مثال: نقاش حول البث المباشر", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YounesEmerald,
                            unfocusedBorderColor = Color.White.copy(0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = YounesEmerald,
                            focusedContainerColor = Color.White.copy(0.05f),
                            unfocusedContainerColor = Color.White.copy(0.03f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        supportingText = { Text("${title.length}/60", color = Color.Gray, fontSize = 11.sp) }
                    )
                }

                // ── الوصف (اختياري) ────────────────────────────────────────
                item {
                    SectionLabel("وصف (اختياري)")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { if (it.length <= 200) description = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        placeholder = { Text("أخبر الآخرين عن ماذا ستتحدث...", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YounesEmerald,
                            unfocusedBorderColor = Color.White.copy(0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = YounesEmerald,
                            focusedContainerColor = Color.White.copy(0.05f),
                            unfocusedContainerColor = Color.White.copy(0.03f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 4
                    )
                }

                // ── الخصوصية ────────────────────────────────────────────────
                item {
                    SectionLabel("الخصوصية")
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(0.05f))
                            .clickable { isPrivate = !isPrivate }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isPrivate) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isPrivate) Color(0xFFFFC107) else Color(0xFF00C98C),
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(if (isPrivate) "خاصة — بدعوة فقط" else "عامة — مفتوحة للجميع",
                                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(if (isPrivate) "فقط المدعوون يمكنهم الانضمام" else "أي مستخدم يمكنه رؤيتها والانضمام",
                                    color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = isPrivate,
                            onCheckedChange = { isPrivate = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFC107), checkedTrackColor = Color(0xFFFFC107).copy(0.4f))
                        )
                    }
                }

                // ── اختيار المتحدثين ─────────────────────────────────────────
                if (friends.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel("دعوة متحدثين")
                            if (selectedSpeakers.isNotEmpty()) {
                                Text("${selectedSpeakers.size} مختار", color = YounesEmerald, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("سيُدعون كمتحدثين منذ البداية", color = Color.Gray, fontSize = 12.sp)
                    }
                    items(friends) { (id, name) ->
                        val isSelected = id in selectedSpeakers
                        val bgColor by animateColorAsState(
                            if (isSelected) Color(0xFF1B3A2A) else Color.White.copy(0.04f),
                            animationSpec = tween(250), label = "bg"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgColor)
                                .clickable {
                                    if (isSelected) selectedSpeakers.remove(id) else selectedSpeakers.add(id)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier.size(42.dp).clip(CircleShape)
                                    .background(
                                        if (isSelected) YounesEmerald.copy(0.2f)
                                        else Color.White.copy(0.1f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(name.take(2).uppercase(), color = if (isSelected) YounesEmerald else Color.White,
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(id, color = Color.Gray, fontSize = 11.sp)
                            }
                            if (isSelected) {
                                Box(
                                    Modifier.size(22.dp).clip(CircleShape).background(YounesEmerald),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // ── معلومات السعة ────────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(0.04f))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Groups, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        Column {
                            Text(
                                if (isVideoMode) "مؤتمر فيديو: حتى 12 مشاركاً بفيديو، لا حد للمستمعين"
                                else "مساحة صوتية: حتى 20 متحدثاً، لا حد للمستمعين",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color.White.copy(0.5f), fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
}

@Composable
private fun SessionTypeCard(
    icon: ImageVector,
    label: String,
    subtitle: String,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        if (selected) accentColor else Color.White.copy(0.1f),
        animationSpec = tween(300), label = "border"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Brush.verticalGradient(listOf(accentColor.copy(0.2f), Color.Transparent)) else Brush.verticalGradient(listOf(Color.White.copy(0.05f), Color.Transparent)))
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(if (selected) accentColor else Color.White.copy(0.2f), Color.Transparent))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(label, color = if (selected) accentColor else Color.White,
            fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.White.copy(0.5f), fontSize = 11.sp)
    }
}
