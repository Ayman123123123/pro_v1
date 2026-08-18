package com.red.sovereign.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.red.sovereign.ui.theme.*
import com.red.sovereign.auth.TokenStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    onNavigateBack: () -> Unit,
    onE2eeKeys: () -> Unit = {},
    onAppLock: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    onChatColors: () -> Unit = {},
    onDataUsage: () -> Unit = {},
    onSelfDestruct: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var displayName by remember { mutableStateOf("مستخدم RED") }
    var redId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val ts = TokenStore(context)
            val loadedName = ts.username
            val loadedRedId = ts.redId
            if (!loadedName.isNullOrBlank()) displayName = loadedName
            if (!loadedRedId.isNullOrBlank()) redId = "RED ID: $loadedRedId"
        } catch (_: Exception) { }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = YounesVoid,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "الإعدادات السيادية",
                        color = YounesOnSurface,
                        fontSize = 20.sp,
                        fontFamily = CairoFamily,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "العودة",
                            tint = YounesOnSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = YounesSurface.copy(alpha = 0.85f)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Profile Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(YounesSurface, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(YounesPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName.take(1).ifBlank { "R" },
                            color = YounesPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = displayName,
                            color = YounesOnSurface,
                            fontSize = 20.sp,
                            fontFamily = CairoFamily,
                            fontWeight = FontWeight.Bold
                        )
                        if (redId.isNotBlank()) {
                            Text(
                                text = redId,
                                color = YounesMuted,
                                fontSize = 14.sp,
                                fontFamily = TajawalFamily
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Badge(
                            containerColor = YounesPrimary.copy(alpha = 0.2f),
                            contentColor = YounesPrimary
                        ) {
                            Text("حساب موثق ومؤمن", modifier = Modifier.padding(horizontal = 6.dp))
                        }
                    }
                }
            }

            // Security & Encryption
            item {
                SettingsSection(title = "الأمان والتشفير") {
                    SettingsItem(
                        icon = Icons.Rounded.VpnKey,
                        title = "مفاتيح التشفير (E2EE)",
                        subtitle = "توليد ومشاركة مفاتيح السيادة",
                        onClick = onE2eeKeys
                    )
                    SettingsItem(
                        icon = Icons.Rounded.Fingerprint,
                        title = "قفل التطبيق",
                        subtitle = "البصمة ورمز الدخول",
                        onClick = onAppLock
                    )
                    SettingsItem(
                        icon = Icons.Rounded.VisibilityOff,
                        title = "الخصوصية",
                        subtitle = "من يمكنه رؤية حالتي",
                        onClick = onPrivacy
                    )
                }
            }

            // Theme
            item {
                SettingsSection(title = "المظهر (Theme)") {
                    SettingsItem(
                        icon = Icons.Rounded.DarkMode,
                        title = "الوضع الملكي",
                        subtitle = "Onyx Black (مفعل)",
                        action = {
                            Switch(checked = true, onCheckedChange = {})
                        }
                    )
                    SettingsItem(
                        icon = Icons.Rounded.FormatPaint,
                        title = "ألوان المحادثة",
                        subtitle = "الزمرد السيادي",
                        onClick = onChatColors
                    )
                }
            }

            // Storage & Data
            item {
                SettingsSection(title = "التخزين والبيانات") {
                    SettingsItem(
                        icon = Icons.Rounded.Storage,
                        title = "استخدام البيانات",
                        subtitle = "عرض تفصيلي للاستهلاك",
                        onClick = onDataUsage
                    )
                    SettingsItem(
                        icon = Icons.Rounded.DeleteForever,
                        title = "التدمير الذاتي (Burn)",
                        subtitle = "إتلاف السجلات فوراً",
                        titleColor = Color(0xFFE53935),
                        onClick = onSelfDestruct
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(YounesSurface, RoundedCornerShape(16.dp))
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = YounesPrimary,
            fontSize = 14.sp,
            fontFamily = CairoFamily,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    titleColor: Color = YounesOnSurface,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = YounesMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 16.sp,
                fontFamily = TajawalFamily,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = YounesMuted,
                    fontSize = 13.sp,
                    fontFamily = TajawalFamily
                )
            }
        }
        if (action != null) {
            action()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = YounesMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
