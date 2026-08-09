package com.red.sovereign.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.AqyalSurfaceNavy
import com.red.sovereign.ui.theme.YounesEmerald

@Composable
fun AuthFlow(viewModel: AuthViewModel) {
    when (val state = viewModel.state) {
        AuthState.Loading, AuthState.Submitting -> LoadingScreen()
        AuthState.Welcome -> WelcomeScreen(viewModel.serverState, viewModel::discoverServer, viewModel::showRegister, viewModel::showLogin)
        AuthState.Register -> RegisterScreen(viewModel::register, viewModel::showWelcome)
        AuthState.Login -> LoginScreen(viewModel::login, viewModel::showRecovery, viewModel::showWelcome)
        AuthState.Recovery -> RecoveryScreen(viewModel::recover, viewModel::showLogin)
        AuthState.RecoveryComplete -> StatusScreen("تم تغيير كلمة المرور", "أُلغيت كل الجلسات القديمة. يمكنك تسجيل الدخول الآن.", viewModel::showLogin)
        is AuthState.Pending -> PendingScreen(state, viewModel::checkApproval, viewModel::showLogin)
        is AuthState.TemporaryPasswordChange -> TemporaryPasswordScreen(state, viewModel::changeTemporaryPassword)
        is AuthState.Rejected -> StatusScreen("تم رفض الطلب", state.reason ?: "راجع مسؤول منظومة يونس المحلية", viewModel::showLogin)
        AuthState.Suspended -> StatusScreen("الحساب موقوف", "تواصل مع المسؤول المحلي", viewModel::showLogin)
        AuthState.Banned -> StatusScreen("الحساب محظور", "تم إلغاء صلاحية الحساب والأجهزة", viewModel::showLogin)
        is AuthState.Error -> StatusScreen("تعذر إكمال العملية", state.message, viewModel::showWelcome)
        is AuthState.Authenticated -> Unit
    }
}

@Composable private fun LoadingScreen() = Centered { CircularProgressIndicator(color = AqyalGold); Spacer(Modifier.height(16.dp)); Text("جارٍ الاتصال بخادم يونس المحلي…") }

@Composable
private fun WelcomeScreen(server: ServerState, discover: () -> Unit, register: () -> Unit, login: () -> Unit) = Centered {
    BrandMark(126)
    Spacer(Modifier.height(18.dp))
    Text("يونس", style = MaterialTheme.typography.headlineLarge, color = Color.White)
    Text("تواصل سيادي. هوية مستقلة. دون رقم هاتف.", color = AqyalCyanGlow, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
    Text("محادثات · نبض محلي · مكالمات يونس", color = AqyalGold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(24.dp))
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AqyalSurfaceNavy.copy(alpha = .82f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (server) {
                ServerState.Discovering -> { CircularProgressIndicator(Modifier.size(22.dp), color = AqyalCyanGlow); Text("جارٍ التحقق من شبكة يونس المحلية…", fontSize = 12.sp) }
                is ServerState.Ready -> Text("الخادم الآمن: ${server.url}", color = AqyalCyanGlow, fontSize = 11.sp, textAlign = TextAlign.Center)
                is ServerState.Error -> { Text(server.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, textAlign = TextAlign.Center); Text("الافتراضي: ${server.fallbackUrl}", color = Color.Gray, fontSize = 10.sp) }
            }
            TextButton(discover, enabled = server !is ServerState.Discovering) { Icon(Icons.Default.Wifi, null); Text(" اكتشاف والتحقق من الخادم") }
        }
    }
    Spacer(Modifier.height(22.dp))
    Button(register, Modifier.fillMaxWidth()) { Text("إنشاء حساب جديد") }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(login, Modifier.fillMaxWidth()) { Text("لدي حساب") }
}

@Composable
private fun RegisterScreen(submit: (String, String, String) -> Unit, back: () -> Unit) {
    var name by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
    val validUsername = username.matches(Regex("^[A-Za-z][A-Za-z0-9_.]{2,31}$"))
    val valid = name.trim().length in 2..100 && validUsername && password.length in 12..128 && !password.contains(username, ignoreCase = true) && password == confirm
    FormColumn("طلب حساب يونس") {
        Text("سيبقى الحساب معلقاً حتى موافقة المسؤول. لا نطلب رقم هاتف أو شريحة.", textAlign = TextAlign.Center)
        Field(name, { name = it }, "الاسم الظاهر")
        Field(username, { username = it.trim().take(32) }, "اسم المستخدم", keyboard = KeyboardOptions(imeAction = ImeAction.Next))
        if (username.isNotEmpty() && !validUsername) Text("3–32 محرفًا، يبدأ بحرف ويقبل الأرقام والنقطة والشرطة السفلية", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        PasswordField(password, { password = it.take(128) }, "كلمة المرور — 12 محرفًا على الأقل")
        if (password.isNotEmpty()) PasswordStrength(password, username)
        PasswordField(confirm, { confirm = it.take(128) }, "تأكيد كلمة المرور")
        if (confirm.isNotEmpty() && password != confirm) Text("كلمتا المرور غير متطابقتين", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button({ submit(name, username, password) }, Modifier.fillMaxWidth(), enabled = valid) { Text("إنشاء المفاتيح وإرسال الطلب") }
        OutlinedButton(back, Modifier.fillMaxWidth()) { Text("رجوع") }
    }
}

@Composable
private fun LoginScreen(submit: (String, String) -> Unit, recovery: () -> Unit, back: () -> Unit) {
    var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    FormColumn("تسجيل الدخول") {
        Field(username, { username = it }, "اسم المستخدم", leading = { Icon(Icons.Default.Person, null) })
        PasswordField(password, { password = it }, "كلمة المرور")
        Button({ submit(username, password) }, Modifier.fillMaxWidth(), enabled = username.isNotBlank() && password.isNotBlank()) { Text("دخول") }
        TextButton(recovery, Modifier.fillMaxWidth()) { Text("نسيت كلمة المرور؟ استخدم رمز الاستعادة") }
        OutlinedButton(back, Modifier.fillMaxWidth()) { Text("رجوع") }
    }
}

@Composable
private fun RecoveryScreen(submit: (String, String, String) -> Unit, back: () -> Unit) {
    var redId by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
    FormColumn("استعادة محلية") {
        Text("لا نستخدم SMS أو بريداً. أدخل أحد الرموز التي حفظتها عند إنشاء الحساب.", textAlign = TextAlign.Center)
        Field(redId, { redId = it.uppercase() }, "معرّف يونس")
        Field(code, { code = it.uppercase() }, "رمز الاستعادة")
        PasswordField(password, { password = it.take(128) }, "كلمة المرور الجديدة — 12 محرفًا على الأقل")
        if (password.isNotEmpty()) PasswordStrength(password, "")
        PasswordField(confirm, { confirm = it.take(128) }, "تأكيد كلمة المرور")
        Button({ submit(redId, code, password) }, Modifier.fillMaxWidth(), enabled = redId.isNotBlank() && code.isNotBlank() && password.length in 12..128 && password == confirm) { Text("تغيير وإلغاء الجلسات") }
        OutlinedButton(back, Modifier.fillMaxWidth()) { Text("رجوع") }
    }
}

@Composable
private fun TemporaryPasswordScreen(state: AuthState.TemporaryPasswordChange, submit: (String, String, String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    FormColumn("تغيير كلمة المرور المؤقتة") {
        Text("قام مسؤول يونس بتعيين كلمة مرور مؤقتة. اختر كلمة مرور جديدة قبل دخول الحساب.", textAlign = TextAlign.Center)
        PasswordField(password, { password = it.take(128) }, "كلمة المرور الجديدة — 12 محرفًا على الأقل")
        if (password.isNotEmpty()) PasswordStrength(password, state.username)
        PasswordField(confirm, { confirm = it.take(128) }, "تأكيد كلمة المرور")
        if (confirm.isNotEmpty() && password != confirm) Text("كلمتا المرور غير متطابقتين", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button({ submit(state.username, state.temporaryPassword, password) }, Modifier.fillMaxWidth(), enabled = password.length in 12..128 && password == confirm) { Text("تغيير كلمة المرور والدخول") }
    }
}

@Composable
private fun PendingScreen(state: AuthState.Pending, check: () -> Unit, login: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Centered {
        Icon(Icons.Default.AdminPanelSettings, null, tint = AqyalGold, modifier = Modifier.height(80.dp))
        Text("بانتظار موافقة المسؤول", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("تم إنشاء مفاتيح هويتك داخل الهاتف ولن يغادر المفتاح الخاص جهازك.", textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.redId, color = AqyalGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton({ clipboard.setText(AnnotatedString(state.redId)) }) { Icon(Icons.Default.ContentCopy, "نسخ") }
        }
        if (state.recoveryCodes.isNotEmpty()) {
            Text("رموز الاستعادة — تظهر مرة واحدة", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Text(state.recoveryCodes.joinToString("  "), fontSize = 11.sp, textAlign = TextAlign.Center)
            OutlinedButton({ clipboard.setText(AnnotatedString(state.recoveryCodes.joinToString("\n"))) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ContentCopy, null); Text(" نسخ الرموز وحفظها خارج الهاتف")
            }
        }
        Button(check, Modifier.fillMaxWidth()) { Icon(Icons.Default.CheckCircle, null); Text(" التحقق من الموافقة") }
        OutlinedButton(login, Modifier.fillMaxWidth()) { Text("الدخول لاحقاً") }
    }
}

@Composable private fun StatusScreen(title: String, description: String, action: () -> Unit) = Centered {
    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.height(72.dp))
    Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    Text(description, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp)); Button(action, Modifier.fillMaxWidth()) { Text("عودة") }
}

@Composable
private fun BrandMark(size: Int) = Box(
    Modifier.size(size.dp)
        .background(Brush.radialGradient(listOf(YounesEmerald.copy(alpha = .35f), Color.Transparent)), CircleShape)
        .border(1.dp, AqyalGold.copy(alpha = .72f), RoundedCornerShape((size / 4).dp))
        .padding(7.dp),
    contentAlignment = Alignment.Center
) {
    Image(
        painterResource(R.drawable.younes_icon_master),
        contentDescription = "يونس",
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape((size / 5).dp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun Centered(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 22.dp).widthIn(max = 520.dp).animateContentSize(),
    Arrangement.Center,
    Alignment.CenterHorizontally,
    content = content
)

@Composable
private fun FormColumn(title: String, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp).widthIn(max = 520.dp).verticalScroll(rememberScrollState()),
    Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
    Alignment.CenterHorizontally
) {
    BrandMark(68)
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, color = AqyalGold)
    Spacer(Modifier.height(16.dp))
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = AqyalSurfaceNavy.copy(alpha = .88f)),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable private fun Field(value: String, change: (String) -> Unit, label: String, keyboard: KeyboardOptions = KeyboardOptions.Default, leading: (@Composable () -> Unit)? = null) = OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, leadingIcon = leading, singleLine = true, keyboardOptions = keyboard)
@Composable
private fun PasswordField(value: String, change: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, change, Modifier.fillMaxWidth(), label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, null) },
        trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "إخفاء كلمة المرور" else "إظهار كلمة المرور") } },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    )
}

@Composable
private fun PasswordStrength(password: String, username: String) {
    var score = 0
    if (password.length >= 12) score++
    if (password.length >= 16) score++
    if (password.any(Char::isUpperCase) && password.any(Char::isLowerCase)) score++
    if (password.any(Char::isDigit) || password.any { !it.isLetterOrDigit() }) score++
    if (username.isNotBlank() && password.contains(username, ignoreCase = true)) score = 0
    val label = when (score) { 0, 1 -> "ضعيفة"; 2 -> "مقبولة"; 3 -> "قوية"; else -> "قوية جدًا" }
    val color = when (score) { 0, 1 -> MaterialTheme.colorScheme.error; 2 -> AqyalGold; else -> YounesEmerald }
    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(progress = { score / 4f }, modifier = Modifier.fillMaxWidth(), color = color)
        Text("قوة كلمة المرور: $label", color = color, style = MaterialTheme.typography.bodySmall)
    }
}
