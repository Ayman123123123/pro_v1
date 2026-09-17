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
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import com.red.sovereign.settings.SettingsViewModel
import com.red.sovereign.ui.theme.YounesPrimary
import com.red.sovereign.ui.theme.YounesMuted
import com.red.sovereign.ui.theme.YounesOnSurface
import com.red.sovereign.ui.theme.YounesVoid
import com.red.sovereign.ui.theme.YounesSurface
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
    val initialName = runCatching { TokenStore(context).username?.takeIf { it.isNotBlank() } }.getOrNull()
    val initialRedId = runCatching { TokenStore(context).redId?.takeIf { it.isNotBlank() } }.getOrNull()
    var displayName by remember { mutableStateOf(initialName ?: "مستخدم RED") }
    var redId by remember { mutableStateOf(initialRedId?.let { "RED ID: $it" } ?: "") }
    var loadTick by remember { mutableStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // جودة: المفتاح context بدل Unit — يعيد التحميل عند تغيّر السياق لا مرة واحدة جامدة.
    LaunchedEffect(context, loadTick) {
        try {
            val ts = TokenStore(context)
            val loadedName = ts.username
            val loadedRedId = ts.redId
            if (!loadedName.isNullOrBlank()) displayName = loadedName
            if (!loadedRedId.isNullOrBlank()) redId = "RED ID: $loadedRedId"
            loadError = null
        } catch (e: Exception) {
            Log.w("ProfileSettings", "TokenStore load failed", e)
            loadError = e.message?.take(120) ?: "تعذر التحميل"
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "الإعدادات السيادية",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontFamily = PlexArabicFamily,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "العودة",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)
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
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName.take(1).ifBlank { "R" },
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = displayName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp,
                            fontFamily = PlexArabicFamily,
                            fontWeight = FontWeight.Bold
                        )
                        if (redId.isNotBlank()) {
                            Text(
                                text = redId,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontFamily = PlexArabicFamily
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text("حساب موثق ومؤمن", modifier = Modifier.padding(horizontal = 6.dp))
                        }
                    }
                }
            }

            // Fallback عند فشل تحميل TokenStore — زر يعيد المحاولة عبر tick.
            if (loadError != null) {
                item {
                    Button(
                        onClick = { loadTick++ },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تعذر التحميل - إعادة المحاولة") }
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

            // Theme — مربوط بـ Theme-State حقيقي + حفظ في Prefs (لا Switch وهمي).
            item {
                // الحفظ عبر SettingsViewModel (younes_user_preferences) + تطبيق فوري عبر AppThemeState
                // (يُبدّل MaterialTheme فعلياً في YounesTheme/SovereignBackground ويُقرأ عند الإقلاع).
                val settingsVm: SettingsViewModel = viewModel()
                SettingsSection(title = "المظهر (Theme)") {
                    // الحفظ يعمل: الحالتان تُشتقان من المحفوظ في Prefs (vm.state) لا من
                    // الذاكرة فقط — فالـ Switch يعكس ما سيُقرأ عند الإقلاع فعلاً.
                    // SYSTEM يتبع وضع النظام عبر AppThemeState اللحظي.
                    val isDark = when (settingsVm.state.themeMode) {
                        "LIGHT" -> false
                        "DARK" -> true
                        else -> AppThemeState.themeMode != AppThemeMode.LIGHT
                    }
                    val isOnyx = settingsVm.state.themePreset == AppThemePreset.OLED_BLACK.name
                    SettingsItem(
                        icon = Icons.Rounded.DarkMode,
                        title = "الوضع الملكي",
                        subtitle = if (isDark) "ليلي — مفعّل ومحفوظ" else "فاتح — مفعّل ومحفوظ",
                        action = {
                            Switch(
                                checked = isDark,
                                onCheckedChange = { dark ->
                                    val mode = if (dark) AppThemeMode.DARK else AppThemeMode.LIGHT
                                    AppThemeState.themeMode = mode
                                    // حفظ فعلي: theme_mode + إبقاء preset (Onyx يبقى Onyx)
                                    settingsVm.setThemeMode(mode.name)
                                }
                            )
                        }
                    )
                    // Onyx Black الحقيقي: preset OLED_BLACK (سواد AMOLED تام) — يُبدّل الألوان ويُحفظ.
                    SettingsItem(
                        icon = Icons.Rounded.DarkMode,
                        title = "أسود Onyx",
                        subtitle = if (isOnyx) "AMOLED مفعّل — سواد تام موفّر للبطارية" else "اضغط لتفعيل سواد AMOLED التام",
                        action = {
                            Switch(
                                checked = isOnyx,
                                onCheckedChange = { onyx ->
                                    if (onyx) {
                                        AppThemeState.currentPreset = AppThemePreset.OLED_BLACK
                                        AppThemeState.themeMode = AppThemeMode.DARK
                                        settingsVm.setThemePreset(AppThemePreset.OLED_BLACK.name)
                                        settingsVm.setThemeMode(AppThemeMode.DARK.name)
                                    } else {
                                        AppThemeState.currentPreset = AppThemePreset.SOVEREIGN
                                        settingsVm.setThemePreset(AppThemePreset.SOVEREIGN.name)
                                    }
                                }
                            )
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
                        titleColor = MaterialTheme.colorScheme.error,
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
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontFamily = PlexArabicFamily,
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
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 16.sp,
                fontFamily = PlexArabicFamily,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontFamily = PlexArabicFamily
                )
            }
        }
        if (action != null) {
            action()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
