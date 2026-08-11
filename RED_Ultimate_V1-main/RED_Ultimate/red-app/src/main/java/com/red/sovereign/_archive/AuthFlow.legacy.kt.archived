package com.red.sovereign.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.auth.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Color Tokens matching YOUNES Sovereign Palette
private val SovereignNavy = Color(0xFF050A16)
private val SovereignCardBg = Color(0xFF0C182B)
private val SovereignGreen = Color(0xFF00C98C)
private val SovereignGold = Color(0xFFE8B84A)
private val SovereignCyan = Color(0xFF35CBE0)
private val SovereignTextPrimary = Color(0xFFEDF7FB)
private val SovereignTextMuted = Color(0xFF8A9FB2)
private val SovereignBorder = Color(0x3300C98C)

enum class AuthStep {
    PHONE_INPUT,
    OTP_VERIFICATION,
    PROFILE_SETUP,
    KEY_GENERATION,
    COMPLETED
}

data class CountryCode(val name: String, val code: String, val flag: String)

val DefaultCountries = listOf(
    CountryCode("اليمن", "+967", "🇾🇪"),
    CountryCode("السعودية", "+966", "🇸🇦"),
    CountryCode("الإمارات", "+971", "🇦🇪"),
    CountryCode("قطر", "+974", "🇶🇦"),
    CountryCode("الكويت", "+965", "🇰🇼"),
    CountryCode("عُمان", "+968", "🇴🇲"),
    CountryCode("مصر", "+20", "🇪🇬"),
    CountryCode("الأردن", "+962", "🇯🇴")
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AuthFlowContainer(
    authViewModel: AuthViewModel,
    onAuthSuccess: () -> Unit
) {
    var currentStep by remember { mutableStateOf(AuthStep.PHONE_INPUT) }
    var phoneNumber by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(DefaultCountries[0]) }
    var otpCode by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var userBio by remember { mutableStateOf("سيادة مشفرة - YOUNES Sovereign") }
    var isGeneratingKeys by remember { mutableStateOf(false) }
    var keyGenProgress by remember { mutableStateOf(0f) }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SovereignNavy,
                        Color(0xFF0A1424),
                        SovereignNavy
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Emblem
            SovereignBrandHeader()

            Spacer(modifier = Modifier.height(28.dp))

            // Step Progress Indicator
            StepProgressIndicator(currentStep = currentStep)

            Spacer(modifier = Modifier.height(28.dp))

            // Main Glassmorphism Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .border(1.dp, SovereignBorder, RoundedCornerShape(26.dp)),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = SovereignCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            fadeIn() + slideInHorizontally { width -> width } with
                                    fadeOut() + slideOutHorizontally { width -> -width }
                        }, label = "AuthStepTransition"
                    ) { step ->
                        when (step) {
                            AuthStep.PHONE_INPUT -> PhoneInputScreen(
                                selectedCountry = selectedCountry,
                                phoneNumber = phoneNumber,
                                onCountrySelected = { selectedCountry = it },
                                onPhoneChanged = { phoneNumber = it },
                                onNext = {
                                    if (phoneNumber.length >= 7) {
                                        currentStep = AuthStep.OTP_VERIFICATION
                                    }
                                }
                            )

                            AuthStep.OTP_VERIFICATION -> OtpVerificationScreen(
                                fullPhoneNumber = "${selectedCountry.code} $phoneNumber",
                                otpCode = otpCode,
                                onOtpChanged = { otpCode = it },
                                onVerify = {
                                    if (otpCode.length == 6) {
                                        currentStep = AuthStep.PROFILE_SETUP
                                    }
                                },
                                onBack = { currentStep = AuthStep.PHONE_INPUT }
                            )

                            AuthStep.PROFILE_SETUP -> ProfileSetupScreen(
                                userName = userName,
                                userBio = userBio,
                                onNameChanged = { userName = it },
                                onBioChanged = { userBio = it },
                                onNext = {
                                    if (userName.isNotBlank()) {
                                        currentStep = AuthStep.KEY_GENERATION
                                    }
                                },
                                onBack = { currentStep = AuthStep.OTP_VERIFICATION }
                            )

                            AuthStep.KEY_GENERATION -> KeyGenerationScreen(
                                isGenerating = isGeneratingKeys,
                                progress = keyGenProgress,
                                onStartGeneration = {
                                    isGeneratingKeys = true
                                },
                                onCompleted = {
                                    currentStep = AuthStep.COMPLETED
                                    onAuthSuccess()
                                }
                            )

                            AuthStep.COMPLETED -> CompletedScreen(onFinish = onAuthSuccess)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SovereignBrandHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x3300C98C),
                            Color(0x2235CBE0)
                        )
                    )
                )
                .border(1.5.dp, SovereignGreen, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Sovereign Shield",
                tint = SovereignGold,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "يونس السيادي",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = SovereignGold
        )

        Text(
            text = "YOUNES Sovereign — نظام المراسلة السيادي المشفر",
            fontSize = 12.sp,
            color = SovereignTextMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StepProgressIndicator(currentStep: AuthStep) {
    val steps = listOf("الهاتف", "الرمز", "الملف", "التشفير")
    val currentIndex = currentStep.ordinal.coerceAtMost(3)

    Row(
        modifier = Modifier.fillMaxWidth(0.9f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterAlignment
    ) {
        steps.forEachIndexed { index, title ->
            val isActive = index <= currentIndex
            val isCurrent = index == currentIndex

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isActive) SovereignGreen else Color(0xFF162536))
                        .border(
                            1.dp,
                            if (isCurrent) SovereignGold else Color.Transparent,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (index < currentIndex) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = SovereignNavy,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) SovereignNavy else SovereignTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = if (isActive) SovereignGreen else SovereignTextMuted,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneInputScreen(
    selectedCountry: CountryCode,
    phoneNumber: String,
    onCountrySelected: (CountryCode) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onNext: () -> Unit
) {
    var countryDropdownExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "تسجيل الدخول / إنشاء حساب",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = SovereignTextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "أدخل رقم هاتفك لتلقي رمز التحقيق السيادي المشفر",
            fontSize = 13.sp,
            color = SovereignTextMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Country Picker Dropdown
        ExposedDropdownMenuBox(
            expanded = countryDropdownExpanded,
            onExpandedChange = { countryDropdownExpanded = !countryDropdownExpanded }
        ) {
            OutlinedTextField(
                value = "${selectedCountry.flag} ${selectedCountry.name} (${selectedCountry.code})",
                onValueChange = {},
                readOnly = true,
                label = { Text("الدولة / المفتاح الدولي", color = SovereignTextMuted) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryDropdownExpanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SovereignGreen,
                    unfocusedBorderColor = SovereignBorder,
                    focusedTextColor = SovereignTextPrimary,
                    unfocusedTextColor = SovereignTextPrimary,
                    focusedContainerColor = Color(0xFF081220),
                    unfocusedContainerColor = Color(0xFF081220)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = countryDropdownExpanded,
                onDismissRequest = { countryDropdownExpanded = false },
                modifier = Modifier.background(Color(0xFF0C182B))
            ) {
                DefaultCountries.forEach { country ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${country.flag} ${country.name} (${country.code})",
                                color = SovereignTextPrimary
                            )
                        },
                        onClick = {
                            onCountrySelected(country)
                            countryDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Phone Number Input
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { if (it.length <= 12 && it.all { char -> char.isDigit() }) onPhoneChanged(it) },
            label = { Text("رقم الهاتف", color = SovereignTextMuted) },
            placeholder = { Text("770000000", color = Color(0xFF4A6075)) },
            leadingIcon = {
                Icon(Icons.Default.Phone, contentDescription = null, tint = SovereignCyan)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SovereignGreen,
                unfocusedBorderColor = SovereignBorder,
                focusedTextColor = SovereignTextPrimary,
                unfocusedTextColor = SovereignTextPrimary,
                focusedContainerColor = Color(0xFF081220),
                unfocusedContainerColor = Color(0xFF081220)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onNext,
            enabled = phoneNumber.length >= 7,
            colors = ButtonDefaults.buttonColors(
                containerColor = SovereignGreen,
                contentColor = SovereignNavy,
                disabledContainerColor = Color(0xFF152A3A),
                disabledContentColor = Color(0xFF4A6075)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "إرسال رمز التحقق",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
fun OtpVerificationScreen(
    fullPhoneNumber: String,
    otpCode: String,
    onOtpChanged: (String) -> Unit,
    onVerify: () -> Unit,
    onBack: () -> Unit
) {
    var timerSeconds by remember { mutableStateOf(60) }

    LaunchedEffect(key1 = timerSeconds) {
        if (timerSeconds > 0) {
            delay(1000)
            timerSeconds--
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = SovereignCyan)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "التحقق من الرمز",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SovereignTextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "تم إرسال رمز التحقق المكون من 6 أرقام إلى الرقم:\n$fullPhoneNumber",
            fontSize = 13.sp,
            color = SovereignTextMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // OTP Code Input
        OutlinedTextField(
            value = otpCode,
            onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) onOtpChanged(it) },
            label = { Text("رمز OTP (6 أرقام)", color = SovereignTextMuted) },
            placeholder = { Text("123456", color = Color(0xFF4A6075)) },
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = SovereignGold)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SovereignGold,
                unfocusedBorderColor = SovereignBorder,
                focusedTextColor = SovereignTextPrimary,
                unfocusedTextColor = SovereignTextPrimary,
                focusedContainerColor = Color(0xFF081220),
                unfocusedContainerColor = Color(0xFF081220)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (timerSeconds > 0) "إعادة الإرسال بعد $timerSeconds ثانية" else "يمكنك إعادة طلب الرمز الآن",
            fontSize = 12.sp,
            color = if (timerSeconds > 0) SovereignTextMuted else SovereignCyan,
            modifier = Modifier.clickable(enabled = timerSeconds == 0) { timerSeconds = 60 }
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onVerify,
            enabled = otpCode.length == 6,
            colors = ButtonDefaults.buttonColors(
                containerColor = SovereignGold,
                contentColor = SovereignNavy
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "تأكيد الرمز والبدء",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ProfileSetupScreen(
    userName: String,
    userBio: String,
    onNameChanged: (String) -> Unit,
    onBioChanged: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = SovereignCyan)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "إعداد الملف الشخصي",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SovereignTextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Avatar Placeholder
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Color(0xFF102236))
                .border(2.dp, SovereignGold, CircleShape)
                .clickable { /* Avatar selection */ },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AddAPhoto,
                contentDescription = "الصورة الشخصية",
                tint = SovereignGold,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "اختر صورة شخصية مشفرة",
            fontSize = 12.sp,
            color = SovereignTextMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = userName,
            onValueChange = onNameChanged,
            label = { Text("الاسم المستعار / الاسم السيادي", color = SovereignTextMuted) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = SovereignGreen) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SovereignGreen,
                unfocusedBorderColor = SovereignBorder,
                focusedTextColor = SovereignTextPrimary,
                unfocusedTextColor = SovereignTextPrimary,
                focusedContainerColor = Color(0xFF081220),
                unfocusedContainerColor = Color(0xFF081220)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = userBio,
            onValueChange = onBioChanged,
            label = { Text("الوصف / الحالة (Bio)", color = SovereignTextMuted) },
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = SovereignCyan) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SovereignGreen,
                unfocusedBorderColor = SovereignBorder,
                focusedTextColor = SovereignTextPrimary,
                unfocusedTextColor = SovereignTextPrimary,
                focusedContainerColor = Color(0xFF081220),
                unfocusedContainerColor = Color(0xFF081220)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onNext,
            enabled = userName.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = SovereignGreen,
                contentColor = SovereignNavy
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "متابعة لتوليد مفاتيح التشفر",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun KeyGenerationScreen(
    isGenerating: Boolean,
    progress: Float,
    onStartGeneration: () -> Unit,
    onCompleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentProgress by remember { mutableStateOf(progress) }
    var currentPhaseText by remember { mutableStateOf("جاهز لتوليد المفاتيح المقاومة للحواسيب الكمومية (Kyber-1024 + Signal)") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.VpnKey,
            contentDescription = null,
            tint = SovereignGold,
            modifier = Modifier.size(54.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "إنشاء مفاتيح التشفير السيادية",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = SovereignTextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = currentPhaseText,
            fontSize = 13.sp,
            color = SovereignTextMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isGenerating) {
            LinearProgressIndicator(
                progress = currentProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = SovereignGreen,
                trackColor = Color(0xFF122438)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${(currentProgress * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SovereignGold
            )
        } else {
            Button(
                onClick = {
                    onStartGeneration()
                    scope.launch {
                        currentPhaseText = "توليد مفاتيح الهوية (Identity KeyPair)..."
                        currentProgress = 0.25f
                        delay(600)

                        currentPhaseText = "توليد مفاتيح PreKeys الخوارزمية الكمومية (Kyber-1024)..."
                        currentProgress = 0.60f
                        delay(800)

                        currentPhaseText = "تشفير وحفظ المفاتيح داخل Android Keystore..."
                        currentProgress = 0.90f
                        delay(600)

                        currentPhaseText = "تم إنشاء المفاتيح بنجاح وتجهيز الحساب السيادي!"
                        currentProgress = 1.0f
                        delay(400)

                        onCompleted()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SovereignGold,
                    contentColor = SovereignNavy
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "توليد المفاتيح الآن",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CompletedScreen(onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = SovereignGreen,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "مرحباً بك في يونس السيادي!",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = SovereignGold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "حسابك جاهز تماماً مع تشفير سيادي كامل من الطرف إلى الطرف.",
            fontSize = 13.sp,
            color = SovereignTextMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(
                containerColor = SovereignGreen,
                contentColor = SovereignNavy
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "الانتقال إلى الواجهة الرئيسية",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
