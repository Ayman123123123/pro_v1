package com.red.sovereign.features.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.*

// ════════════════════════════════════════════════════════════
//  DATA
// ════════════════════════════════════════════════════════════

data class Contact(
    val id: String,
    val name: String,
    val avatarColor: Color,
    val phone: String = "",
    val isOnline: Boolean = false
)

// ════════════════════════════════════════════════════════════
//  GROUP CREATE SCREEN — إنشاء مجموعة جديدة
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreateScreen(
    onNext: (List<String>) -> Unit
) {
    var searchQuery       by remember { mutableStateOf("") }
    var selectedContacts  by remember { mutableStateOf(setOf<Contact>()) }
    var groupName         by remember { mutableStateOf("") }
    var step              by remember { mutableStateOf(1) } // 1=select, 2=name

    val allContacts = remember {
        listOf(
            Contact("1", "أيمن",          BlueAccent,   "+967 777 111 111", true),
            Contact("2", "علي",           GreenAccent,  "+967 777 222 222", false),
            Contact("3", "سارة",          Color(0xFFE91E63), "+967 777 333 333", true),
            Contact("4", "قائد الفريق",   PurplePrimary, "+967 777 444 444", false),
            Contact("5", "خالد",          AqyalCyan,    "+967 777 555 555", true),
            Contact("6", "فاطمة",         GoldenAccent, "+967 777 666 666", false),
            Contact("7", "محمد",          OrangeAccent, "+967 777 777 777", true),
            Contact("8", "نورة",          RedBrand,     "+967 777 888 888", false),
        )
    }

    val filtered = allContacts.filter {
        searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true)
    }

    AnimatedContent(targetState = step, transitionSpec = {
        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
    }) { currentStep ->
        when (currentStep) {
            1 -> SelectContactsStep(
                searchQuery      = searchQuery,
                selectedContacts = selectedContacts,
                contacts         = filtered,
                onSearchChange   = { searchQuery = it },
                onToggleContact  = { c ->
                    selectedContacts = if (c in selectedContacts) selectedContacts - c else selectedContacts + c
                },
                onNext = {
                    if (selectedContacts.isNotEmpty()) step = 2
                }
            )
            2 -> NameGroupStep(
                selectedContacts = selectedContacts,
                groupName        = groupName,
                onNameChange     = { groupName = it },
                onBack           = { step = 1 },
                onCreate         = { onNext(selectedContacts.map { it.id }) }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
//  STEP 1 — اختيار جهات الاتصال
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectContactsStep(
    searchQuery: String,
    selectedContacts: Set<Contact>,
    contacts: List<Contact>,
    onSearchChange: (String) -> Unit,
    onToggleContact: (Contact) -> Unit,
    onNext: () -> Unit
) {
    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            Column(
                modifier = Modifier
                    .background(SurfaceDark)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("مجموعة جديدة", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${selectedContacts.size} مختار${if (selectedContacts.isEmpty()) "" else ""}",
                            color = if (selectedContacts.isEmpty()) TextTertiary else AqyalCyanGlow,
                            fontSize = 13.sp
                        )
                    }
                    AnimatedVisibility(visible = selectedContacts.isNotEmpty()) {
                        FloatingActionButton(
                            onClick        = onNext,
                            containerColor = RedBrand,
                            contentColor   = TextOnRed,
                            modifier       = Modifier.size(48.dp),
                            shape          = CircleShape
                        ) {
                            Icon(Icons.Rounded.ArrowForward, "التالي", modifier = Modifier.size(22.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = onSearchChange,
                    modifier      = Modifier.fillMaxWidth().height(50.dp),
                    placeholder   = { Text("ابحث عن جهة اتصال...", color = TextTertiary, fontSize = 14.sp) },
                    leadingIcon   = { Icon(Icons.Rounded.Search, null, tint = TextSecondary, modifier = Modifier.size(20.dp)) },
                    trailingIcon  = {
                        if (searchQuery.isNotEmpty())
                            IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Rounded.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                    },
                    singleLine    = true,
                    shape         = RoundedCornerShape(14.dp),
                    colors        = TextFieldDefaults.outlinedTextFieldColors(
                        containerColor       = SurfaceMid,
                        focusedBorderColor   = RedBrand,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor          = RedBrand,
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary
                    )
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Selected contacts horizontal scroll
            if (selectedContacts.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            "المختارون",
                            color    = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Medium
                        )
                        LazyRow(
                            contentPadding        = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(selectedContacts.toList(), key = { it.id }) { contact ->
                                SelectedContactBubble(contact = contact, onRemove = { onToggleContact(contact) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = DividerColor)
                    }
                }
            }

            // All contacts
            item {
                Text(
                    "جهات الاتصال",
                    color    = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(contacts, key = { it.id }) { contact ->
                val isSelected = contact in selectedContacts
                ContactSelectRow(
                    contact    = contact,
                    isSelected = isSelected,
                    onToggle   = { onToggleContact(contact) }
                )
            }
        }
    }
}

@Composable
private fun SelectedContactBubble(contact: Contact, onRemove: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(contact.avatarColor, contact.avatarColor.copy(0.6f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(contact.name.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
                    .border(1.dp, BgPrimary, CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, null, tint = TextSecondary, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(contact.name.take(6), color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun ContactSelectRow(contact: Contact, isSelected: Boolean, onToggle: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) RedBrand.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(200)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(contact.avatarColor, contact.avatarColor.copy(0.6f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(contact.name.take(1).uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (contact.isOnline) {
                Box(modifier = Modifier.size(13.dp).clip(CircleShape).background(OnlineDot).border(2.dp, BgPrimary, CircleShape))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name,  color = TextPrimary,   fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(contact.phone, color = TextTertiary,  fontSize = 13.sp)
        }

        // Checkbox
        AnimatedContent(targetState = isSelected) { selected ->
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(GradientRedPrimary)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(2.dp, SurfaceOverlay, CircleShape)
                )
            }
        }
    }
    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 80.dp))
}

// ════════════════════════════════════════════════════════════
//  STEP 2 — تسمية المجموعة
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameGroupStep(
    selectedContacts: Set<Contact>,
    groupName: String,
    onNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onCreate: () -> Unit
) {
    var isMegaGroup by remember { mutableStateOf(true) }
    var requireApproval by remember { mutableStateOf(true) }
    var disappearingSeconds by remember { mutableStateOf(0) }
    var selectedThemeColor by remember { mutableStateOf(RedBrand) }
    val themes = listOf(RedBrand, YounesEmerald, AqyalGold, AqyalCyan, PurpleAccent, OrangeAccent)

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            TopAppBar(
                title = { Text("تفاصيل المجموعة الفائقة", color = TextBright, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "رجوع", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().background(SurfaceDark).padding(16.dp)) {
                Button(
                    onClick  = onCreate,
                    enabled  = groupName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = selectedThemeColor,
                        contentColor   = TextOnRed,
                        disabledContainerColor = SurfaceLight,
                        disabledContentColor   = TextTertiary
                    )
                ) {
                    Icon(Icons.Rounded.Group, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("إنشاء المجموعة المشفرة (${if (isMegaGroup) "حتى 10,000 عضو" else "عادية"})", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding      = PaddingValues(bottom = 32.dp)
        ) {
            item { Spacer(Modifier.height(12.dp)) }

            // Group avatar picker
            item {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(selectedThemeColor, selectedThemeColor.copy(0.6f))))
                        .clickable { /* pick photo */ },
                    contentAlignment = Alignment.Center
                ) {
                    if (groupName.isBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.CameraAlt, null, tint = Color.White.copy(0.9f), modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("صورة", color = Color.White.copy(0.9f), fontSize = 11.sp)
                        }
                    } else {
                        Text(groupName.take(2).uppercase(), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            // Group name field
            item {
                OutlinedTextField(
                    value         = groupName,
                    onValueChange = onNameChange,
                    modifier      = Modifier.fillMaxWidth(),
                    label         = { Text("اسم المجموعة") },
                    placeholder   = { Text("مثل: مجتمع مطوري RED، قمة الكايبر...", color = TextTertiary, fontSize = 14.sp) },
                    singleLine    = true,
                    shape         = RoundedCornerShape(14.dp),
                    colors        = TextFieldDefaults.outlinedTextFieldColors(
                        containerColor       = SurfaceMid,
                        focusedBorderColor   = selectedThemeColor,
                        unfocusedBorderColor = SurfaceLight,
                        cursorColor          = selectedThemeColor,
                        focusedLabelColor    = selectedThemeColor,
                        unfocusedLabelColor  = TextSecondary,
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary
                    ),
                    trailingIcon = {
                        Text("${groupName.length}/100", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                    }
                )
            }

            // Theme Color Selector
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("سمة المجموعة (Theme)", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        themes.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(if (selectedThemeColor == color) 3.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { selectedThemeColor = color }
                            )
                        }
                    }
                }
            }

            // Advanced Sovereign Settings Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceDark
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("إعدادات السيادة المتقدمة", color = TextBright, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                        // Mega Group Capacity Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("وضع المجموعة الكبرى (Mega Group)", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("دعم حتى 10,000 عضو مع تشفير Post-Quantum", color = TextTertiary, fontSize = 12.sp)
                            }
                            Switch(
                                checked = isMegaGroup,
                                onCheckedChange = { isMegaGroup = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = selectedThemeColor)
                            )
                        }

                        HorizontalDivider(color = DividerColor)

                        // Admin approval toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("موافقة المشرفين على الروابط", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("طلب موافقة الأدمن عند الانضمام عبر رابط الدعوة أو QR", color = TextTertiary, fontSize = 12.sp)
                            }
                            Switch(
                                checked = requireApproval,
                                onCheckedChange = { requireApproval = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = selectedThemeColor)
                            )
                        }

                        HorizontalDivider(color = DividerColor)

                        // Disappearing messages
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("الرسائل المختفية التلقائية", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "معطّل", 86400 to "24 ساعة", 604800 to "7 أيام", 2592000 to "30 يوم").forEach { (sec, label) ->
                                    FilterChip(
                                        selected = disappearingSeconds == sec,
                                        onClick = { disappearingSeconds = sec },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = selectedThemeColor.copy(alpha = 0.2f),
                                            selectedLabelColor = selectedThemeColor
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Members preview
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${selectedContacts.size} عضو مختار",
                        color = TextSecondary, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(selectedContacts.toList(), key = { it.id }) { c ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(c.avatarColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(c.name.take(1).uppercase(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(c.name.take(6), color = TextTertiary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
