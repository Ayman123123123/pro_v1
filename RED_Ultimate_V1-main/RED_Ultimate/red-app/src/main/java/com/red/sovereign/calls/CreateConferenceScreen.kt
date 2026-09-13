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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val selectedSpeakers = remember { mutableStateListOf<String>() }

    // زوج الأصدقاء (id → name)
    val friends = remember(friendIds, friendNames) {
        friendIds.mapIndexed { i, id -> id to friendNames.getOrElse(i) { id } }
    }

    // ── Layout (متوازن: يحترم الفاتح/الداكن عبر MaterialTheme — لا أسود صريح) ──
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = scheme.onBackground)
                }
                Text("إنشاء جلسة", color = scheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                // Launch button — مكالمة واحدة تمرر كل شيء (العنوان/الوصف/الخصوصية/السر/المدعوين)
                Button(
                    onClick = {
                        val roomId = "room_" + UUID.randomUUID().toString().replace("-", "").take(12)
                        ConferenceService.join(
                            context = context,
                            roomId = roomId,
                            userId = myUserId,
                            video = isVideoMode,
                            inviteRedIds = selectedSpeakers.toList(),
                            asHost = true,
                            title = title.trim(),
                            isPrivate = isPrivate,
                            description = description.trim(),
                            password = password.takeIf { isPrivate && it.length >= 4 }
                        )
                        onLaunched(roomId)
                    },
                    enabled = title.isNotBlank() && (!isPrivate || password.isBlank() || password.length >= 4),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        disabledContainerColor = scheme.surfaceVariant,
                        contentColor = scheme.onPrimary,
                        disabledContentColor = scheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(38.dp).border(if (title.isNotBlank()) 1.dp else 0.dp, if (title.isNotBlank()) Color.White.copy(0.5f) else Color.Transparent, RoundedCornerShape(20.dp))
                ) {
                    Text("إطلاق الآن", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Divider(color = scheme.outlineVariant)

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
                        placeholder = { Text("مثال: نقاش حول البث المباشر", color = scheme.onSurfaceVariant) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = scheme.primary,
                            unfocusedBorderColor = scheme.outline,
                            focusedTextColor = scheme.onSurface,
                            unfocusedTextColor = scheme.onSurface,
                            cursorColor = scheme.primary,
                            focusedContainerColor = scheme.surface,
                            unfocusedContainerColor = scheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        supportingText = { Text("${title.length}/60", color = scheme.onSurfaceVariant, fontSize = 11.sp) }
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
                        placeholder = { Text("أخبر الآخرين عن ماذا ستتحدث...", color = scheme.onSurfaceVariant) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = scheme.primary,
                            unfocusedBorderColor = scheme.outline,
                            focusedTextColor = scheme.onSurface,
                            unfocusedTextColor = scheme.onSurface,
                            cursorColor = scheme.primary,
                            focusedContainerColor = scheme.surface,
                            unfocusedContainerColor = scheme.surface
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
                            .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { isPrivate = !isPrivate }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isPrivate) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isPrivate) Color(0xFFB8860B) else scheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(if (isPrivate) "خاصة — بدعوة فقط" else "عامة — مفتوحة للجميع",
                                    color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(if (isPrivate) "فقط المدعوون يمكنهم الانضمام" else "أي مستخدم يمكنه رؤيتها والانضمام",
                                    color = scheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = isPrivate,
                            onCheckedChange = { isPrivate = it }
                        )
                    }
                    // كلمة سر اختيارية للجلسة الخاصة (يفرضها الخادم PBKDF2 — كانت غير موجودة أصلاً).
                    if (isPrivate) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { if (it.length <= 32 && !it.contains(' ')) password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("كلمة سر (اختياري — 4 أحرف فأكثر)") },
                            singleLine = true,
                            visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showPassword) "إخفاء" else "إظهار",
                                        tint = scheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = scheme.primary,
                                unfocusedBorderColor = scheme.outline,
                                focusedTextColor = scheme.onSurface,
                                unfocusedTextColor = scheme.onSurface,
                                cursorColor = scheme.primary,
                                focusedContainerColor = scheme.surface,
                                unfocusedContainerColor = scheme.surface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            supportingText = {
                                if (password.isNotBlank() && password.length < 4) Text("4 أحرف على الأقل", color = scheme.error, fontSize = 11.sp)
                            },
                            isError = password.isNotBlank() && password.length < 4
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
                                Text("${selectedSpeakers.size} مختار", color = scheme.primary, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("سيُدعون كمتحدثين منذ البداية", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    items(friends, key = { it.first }) { (id, name) ->
                        val isSelected = id in selectedSpeakers
                        val bgColor by animateColorAsState(
                            if (isSelected) scheme.primaryContainer else scheme.surface,
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
                                        if (isSelected) scheme.primary.copy(0.2f)
                                        else scheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(name.take(2).uppercase(), color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(name, color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(id, color = scheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            if (isSelected) {
                                Box(
                                    Modifier.size(22.dp).clip(CircleShape).background(scheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // ── معلومات السعة (حدود مُنفذة فعلاً في الخادم) ────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Groups, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Column {
                            Text(
                                if (isVideoMode) "مؤتمر فيديو: حتى 12 مشاركاً بفيديو • 20 متحدثاً • 100 مستمع"
                                else "مساحة صوتية: حتى 20 متحدثاً • مضيفان مشاركان • 100 مستمع",
                                color = scheme.onSurfaceVariant,
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
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
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
    val scheme = MaterialTheme.colorScheme
    val borderColor by animateColorAsState(
        if (selected) accentColor else scheme.outline,
        animationSpec = tween(300), label = "border"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accentColor.copy(0.12f) else scheme.surface)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(if (selected) accentColor else scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (selected) Color.White else scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        Text(label, color = if (selected) accentColor else scheme.onSurface,
            fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = scheme.onSurfaceVariant, fontSize = 11.sp)
    }
}
