package com.red.sovereign.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.R
import com.red.sovereign.auth.AuthState
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.auth.ServerState

// Telegram / WhatsApp High-Contrast Dark Color System
private val TelegramDarkBg = Color(0xFF0E1621)
private val TelegramDarkCard = Color(0xFF17212B)
private val TelegramDarkInput = Color(0xFF242F3D)
private val TelegramBorder = Color(0xFF2A394A)
private val CleanAccentGreen = Color(0xFF00A884)
private val CleanAccentBlue = Color(0xFF2AABEE)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSilver = Color(0xFF8E9DAE)
private val TextMuted = Color(0xFF6C788A)
private val ErrorRed = Color(0xFFE53935)

@Composable
fun AuthFlow(viewModel: AuthViewModel) {
    when (val state = viewModel.state) {
        AuthState.Loading, AuthState.Submitting -> LoadingScreen()
        AuthState.Welcome -> WelcomeScreen(viewModel.serverState, viewModel::discoverServer, viewModel::showRegister, viewModel::showLogin)
        AuthState.Register -> RegisterTabScreen(viewModel::register, viewModel::showLogin, viewModel::showWelcome)
        AuthState.Login -> LoginTabScreen(viewModel::login, viewModel::showRegister, viewModel::showRecovery, viewModel::showWelcome)
        AuthState.Recovery -> RecoveryScreen(viewModel::recover, viewModel::showLogin)
        AuthState.RecoveryComplete -> StatusScreen("تم تغيير كلمة المرور 🔐", "أُلغيت كل الجلسات القديمة بنجاح. يمكنك الآن تسجيل الدخول بكلمة المرور الجديدة.", viewModel::showLogin)
        is AuthState.Pending -> PendingScreen(state, viewModel::checkApproval, viewModel::showLogin)
        is AuthState.Rejected -> StatusScreen("تم رفض طلب الحساب ❌", state.reason ?: "يرجى مراجعة مسؤول منظومة يونس المحلية.", viewModel::showLogin)
        AuthState.Suspended -> StatusScreen("الحساب موقوف مؤقتاً ⚠️", "تواصل مع المسؤول المحلي لتفعيل الجلسة.", viewModel::showLogin)
        AuthState.Banned -> StatusScreen("الحساب محظور 🚫", "تم إلغاء صلاحية هذا الحساب والأجهزة المرتبطة به.", viewModel::showLogin)
        is AuthState.Error -> ErrorStatusScreen("تعذر إكمال العملية", state.message, viewModel::showWelcome)
        is AuthState.Authenticated -> Unit
    }
}

@Composable
private fun LoadingScreen() = Centered {
    CircularProgressIndicator(color = CleanAccentGreen, modifier = Modifier.size(42.dp))
    Spacer(Modifier.height(16.dp))
    Text("جارٍ الاتصال بالسيرفر…", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text("ثوانٍ معدودة — لن يبقى التطبيق معلّقًا إذا كان الخادم بعيدًا", color = TextSilver, fontSize = 12.sp, textAlign = TextAlign.Center)
}

@Composable
private fun WelcomeScreen(server: ServerState, discover: () -> Unit, register: () -> Unit, login: () -> Unit) = Centered {
    BrandMark(120)
    Spacer(Modifier.height(18.dp))
    Text("يونس", style = MaterialTheme.typography.headlineLarge, color = TextWhite, fontWeight = FontWeight.Black)
    Text("منظومة اتصالات سيادية مشفرة • بدون رقم هاتف", color = CleanAccentBlue, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    
    Spacer(Modifier.height(20.dp))
    
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = TelegramDarkCard), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (server) {
                ServerState.Discovering -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = CleanAccentBlue, strokeWidth = 2.dp)
                        Text("جارٍ التحقق الذكي من شبكة يونس…", color = TextSilver, fontSize = 13.sp)
                    }
                }
                is ServerState.Ready -> Text("الخادم الآمن: ${server.url} 🟢", color = CleanAccentGreen, fontSize = 12.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                is ServerState.Error -> {
                    Text(server.message, color = ErrorRed, fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                    Text("الافتراضي: ${server.fallbackUrl}", color = TextMuted, fontSize = 11.sp)
                }
            }
            TextButton(discover, enabled = server !is ServerState.Discovering) {
                Icon(Icons.Default.Wifi, null, tint = CleanAccentBlue, modifier = Modifier.size(18.dp))
                Text(" إعادة اكتشاف الخادم الآمن", color = CleanAccentBlue, fontSize = 12.sp)
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    
    // Top Action Buttons
    Button(
        onClick = register,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CleanAccentGreen),
        shape = RoundedCornerShape(25.dp)
    ) {
        Text("إنشاء حساب جديد", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = login,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(25.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CleanAccentBlue)
    ) {
        Text("لدي حساب بالفعل", color = CleanAccentBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * شاشة تبويب إنشاء حساب جديد مع شريط تنقل علوي سلس بين (إنشاء حساب / تسجيل الدخول)
 */
@Composable
private fun RegisterTabScreen(
    submitRegister: (String, String, String) -> Unit,
    switchToLogin: () -> Unit,
    back: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val validUsername = username.matches(Regex("^[A-Za-z][A-Za-z0-9_.]{2,31}$"))
    val passwordsMatch = password.isNotEmpty() && password == confirm
    val validPassword = password.length in 12..128 && !password.contains(username, ignoreCase = true)
    val valid = name.trim().length in 2..100 && validUsername && validPassword && passwordsMatch

    FormColumn("إنشاء حساب سيادي جديد") {
        // Tab Segmented Selector (إنشاء حساب | لدي حساب)
        AuthTabSelector(selectedTab = 0, onSelectTab = { if (it == 1) switchToLogin() })

        Spacer(Modifier.height(14.dp))

        Text(
            text = "لا نطلب رقم هاتف أو شريحة. يتم توليد مفاتيح التشفير الخاصة بك محلياً بداخل هاتفك.",
            color = TextSilver,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )

        Spacer(Modifier.height(12.dp))

        Field(name, { name = it }, "الاسم الظاهر (مثال: يونس أحمد)", leading = { Icon(Icons.Default.Badge, null, tint = CleanAccentBlue) })
        
        Field(
            value = username,
            change = { username = it.trim().take(32) },
            label = "اسم المستخدم (الاسم المعرّف)",
            keyboard = KeyboardOptions(imeAction = ImeAction.Next),
            leading = { Icon(Icons.Default.Person, null, tint = CleanAccentBlue) }
        )
        if (username.isNotEmpty() && !validUsername) {
            Text("يجب أن يكون 3-32 حرفاً إنكليزياً ويبدأ بحرف دون مسافات.", color = ErrorRed, fontSize = 11.sp)
        }

        PasswordField(password, { password = it.take(128) }, "كلمة المرور (12 حرفاً على الأقل)")
        if (password.isNotEmpty()) {
            PasswordStrengthBar(password, username)
        }

        PasswordField(confirm, { confirm = it.take(128) }, "تأكيد كلمة المرور")
        if (confirm.isNotEmpty()) {
            if (passwordsMatch) {
                Text("كلمتا المرور متطابقتان ✓", color = CleanAccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("كلمتا المرور غير متطابقتين ❌", color = ErrorRed, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { submitRegister(name, username, password) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = valid,
            colors = ButtonDefaults.buttonColors(containerColor = CleanAccentGreen, disabledContainerColor = CleanAccentGreen.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("إنشاء المفاتيح وإرسال الطلب", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = back,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TelegramBorder)
        ) {
            Text("رجوع للشاشة الرئيسية", color = TextSilver, fontSize = 14.sp)
        }
    }
}

/**
 * شاشة تبويب تسجيل الدخول للحسابات القائمة
 */
@Composable
private fun LoginTabScreen(
    submitLogin: (String, String) -> Unit,
    switchToRegister: () -> Unit,
    showRecovery: () -> Unit,
    back: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    FormColumn("تسجيل الدخول للحساب") {
        // Tab Segmented Selector (إنشاء حساب | لدي حساب)
        AuthTabSelector(selectedTab = 1, onSelectTab = { if (it == 0) switchToRegister() })

        Spacer(Modifier.height(14.dp))

        Field(username, { username = it }, "اسم المستخدم المعرّف", leading = { Icon(Icons.Default.Person, null, tint = CleanAccentBlue) })
        
        PasswordField(password, { password = it }, "كلمة المرور")

        Spacer(Modifier.height(6.dp))

        Button(
            onClick = { submitLogin(username, password) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = username.isNotBlank() && password.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = CleanAccentGreen),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("تسجيل الدخول", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        TextButton(onClick = showRecovery, modifier = Modifier.fillMaxWidth()) {
            Text("نسيت كلمة المرور؟ استخدم رمز الاستعادة المحفوظ 🔐", color = CleanAccentBlue, fontSize = 13.sp)
        }

        OutlinedButton(
            onClick = back,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TelegramBorder)
        ) {
            Text("رجوع للشاشة الرئيسية", color = TextSilver, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AuthTabSelector(selectedTab: Int, onSelectTab: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(TelegramDarkInput, RoundedCornerShape(22.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(if (selectedTab == 0) CleanAccentGreen else Color.Transparent)
                .clickable { onSelectTab(0) },
            contentAlignment = Alignment.Center
        ) {
            Text("إنشاء حساب جديد", color = if (selectedTab == 0) TextWhite else TextSilver, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(if (selectedTab == 1) CleanAccentBlue else Color.Transparent)
                .clickable { onSelectTab(1) },
            contentAlignment = Alignment.Center
        ) {
            Text("لدي حساب بالفعل", color = if (selectedTab == 1) TextWhite else TextSilver, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RecoveryScreen(submit: (String, String, String) -> Unit, back: () -> Unit) {
    var redId by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    FormColumn("استعادة كلمة المرور بدون سيرفر") {
        Text("أدخل معرّف يونس وأحد رموز الاستعادة التي قمت بنسخها عند إنشاء الحساب.", color = TextSilver, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Field(redId, { redId = it.uppercase() }, "معرّف يونس (RED ID)")
        Field(code, { code = it.uppercase() }, "رمز الاستعادة (Recovery Code)")
        PasswordField(password, { password = it.take(128) }, "كلمة المرور الجديدة")
        PasswordField(confirm, { confirm = it.take(128) }, "تأكيد كلمة المرور الجديدة")
        
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { submit(redId, code, password) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = redId.isNotBlank() && code.isNotBlank() && password.length >= 12 && password == confirm,
            colors = ButtonDefaults.buttonColors(containerColor = CleanAccentGreen),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("تغيير كلمة المرور وإلغاء الجلسات", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = back, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp)) {
            Text("إلغاء والعودة", color = TextSilver)
        }
    }
}

@Composable
private fun PendingScreen(state: AuthState.Pending, check: () -> Unit, login: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Centered {
        Icon(Icons.Default.AdminPanelSettings, null, tint = CleanAccentBlue, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(10.dp))
        Text("طلب الحساب بانتظار الاعتماد ⏳", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite)
        Text("تم إنشاء مفاتيح هويتك المشفرة بنجاح داخل هاتفك ولن تغادر جهازك.", color = TextSilver, textAlign = TextAlign.Center, fontSize = 13.sp)
        
        Spacer(Modifier.height(14.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = TelegramDarkInput)) {
            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("معرّف يونس الخاص بك:", color = TextMuted, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.redId, color = CleanAccentBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { clipboard.setText(AnnotatedString(state.redId)) }) {
                        Icon(Icons.Default.ContentCopy, "نسخ", tint = CleanAccentBlue, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (state.recoveryCodes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1618))) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("⚠️ رموز الاستعادة — احفظها في مكان آمن خارج الهاتف", color = ErrorRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(state.recoveryCodes.joinToString("  •  "), fontSize = 12.sp, color = TextWhite, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(state.recoveryCodes.joinToString("\n"))) },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                        Text(" نسخ كافة الرموز الحافظة", color = ErrorRed, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = check, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = CleanAccentGreen), shape = RoundedCornerShape(24.dp)) {
            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
            Text(" التحقق من اعتماد الحساب", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = login, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp)) {
            Text("تسجيل الدخول لاحقاً", color = TextSilver)
        }
    }
}

@Composable
private fun StatusScreen(title: String, description: String, action: () -> Unit) = Centered {
    Icon(Icons.Default.Lock, null, tint = CleanAccentBlue, modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(12.dp))
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite)
    Spacer(Modifier.height(6.dp))
    Text(description, textAlign = TextAlign.Center, color = TextSilver, fontSize = 14.sp)
    Spacer(Modifier.height(20.dp))
    Button(onClick = action, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = CleanAccentGreen), shape = RoundedCornerShape(24.dp)) {
        Text("متابعة", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ErrorStatusScreen(title: String, description: String, action: () -> Unit) = Centered {
    Icon(Icons.Default.Error, null, tint = ErrorRed, modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(12.dp))
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1618))) {
        Text(description, textAlign = TextAlign.Center, color = TextWhite, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = action, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = CleanAccentBlue), shape = RoundedCornerShape(24.dp)) {
        Text("محاولة أخرى", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BrandMark(size: Int) = Box(
    Modifier.size(size.dp)
        .background(Brush.radialGradient(listOf(CleanAccentGreen.copy(alpha = 0.3f), Color.Transparent)), CircleShape)
        .border(1.dp, CleanAccentGreen.copy(alpha = 0.6f), CircleShape)
        .padding(6.dp),
    contentAlignment = Alignment.Center
) {
    Image(
        painterResource(R.drawable.younes_icon_master),
        contentDescription = "يونس",
        modifier = Modifier.fillMaxSize().clip(CircleShape),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun Centered(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().background(TelegramDarkBg).padding(horizontal = 24.dp, vertical = 18.dp).widthIn(max = 520.dp).animateContentSize(),
    Arrangement.Center,
    Alignment.CenterHorizontally,
    content = content
)

@Composable
private fun FormColumn(title: String, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().background(TelegramDarkBg).padding(horizontal = 16.dp, vertical = 16.dp).widthIn(max = 520.dp).verticalScroll(rememberScrollState()),
    Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
    Alignment.CenterHorizontally
) {
    BrandMark(64)
    Spacer(Modifier.height(10.dp))
    Text("منظومة يونس السيادية 🛡️", style = MaterialTheme.typography.titleLarge, color = TextWhite, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = TelegramDarkCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun Field(
    value: String,
    change: (String) -> Unit,
    label: String,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    leading: (@Composable () -> Unit)? = null
) = OutlinedTextField(
    value = value,
    onValueChange = change,
    modifier = Modifier.fillMaxWidth(),
    label = { Text(label, color = TextSilver, fontSize = 13.sp) },
    leadingIcon = leading,
    singleLine = true,
    keyboardOptions = keyboard,
    colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = TelegramDarkInput,
        unfocusedContainerColor = TelegramDarkInput,
        focusedBorderColor = CleanAccentGreen,
        unfocusedBorderColor = TelegramBorder,
        focusedTextColor = TextWhite,
        unfocusedTextColor = TextWhite
    ),
    shape = RoundedCornerShape(12.dp)
)

@Composable
private fun PasswordField(value: String, change: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = change,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = TextSilver, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Lock, null, tint = CleanAccentBlue) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "إخفاء" else "إظهار", tint = TextSilver)
            }
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = TelegramDarkInput,
            unfocusedContainerColor = TelegramDarkInput,
            focusedBorderColor = CleanAccentGreen,
            unfocusedBorderColor = TelegramBorder,
            focusedTextColor = TextWhite,
            unfocusedTextColor = TextWhite
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun PasswordStrengthBar(password: String, username: String) {
    var score = 0
    if (password.length >= 12) score++
    if (password.length >= 16) score++
    if (password.any(Char::isUpperCase) && password.any(Char::isLowerCase)) score++
    if (password.any(Char::isDigit) || password.any { !it.isLetterOrDigit() }) score++
    if (username.isNotBlank() && password.contains(username, ignoreCase = true)) score = 0

    val label = when (score) { 0, 1 -> "ضعيفة ⚠️"; 2 -> "مقبولة 🟡"; 3 -> "قوية 🟢"; else -> "قوية جدًا 🛡️" }
    val color = when (score) { 0, 1 -> ErrorRed; 2 -> Color(0xFFFF9800); else -> CleanAccentGreen }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        LinearProgressIndicator(progress = { score / 4f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = color)
        Text("قوة كلمة المرور: $label", color = color, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
