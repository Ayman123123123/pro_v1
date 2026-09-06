# 📞 دليل دمج Linphone SDK في تطبيق RED (كمحرك PSTN عبر UC200 Pro)

> الهدف: استبدال طبقة WebRTC→Asterisk بعميل **Linphone (liblinphone)** يسجّل مباشرةً على
> **UC200 Pro (IP-PBX)** كـ Extension، ويتصل داخلياً (extension↔extension) وخارجياً
> (أي رقم شبكة) عبر مسارات UC200 Pro الصادرة → trunk بوابة **UC2000-VE-8G** → GSM.

> **الترخيص:** التطبيق مفتوح المصدر، لذا `linphone-sdk` متاح تحت **GPLv3/AGPL-3.0** مجاناً
> (مزدوج الترخيص مع نسخة تجارية بأجر من Belledonne للتطبيقات المغلقة) [1](https://github.com/BelledonneCommunications/linphone-sdk).

---

## 1) التبعية (Maven)

عنوان المستودع الصحيح (مهم — العنوان القديم `linphone.org/maven_repository/` توقّف):

```
https://download.linphone.org/maven_repository/
```

الإحداثيات (تأكّد من أحدث إصدار مستقر عند البناء — 5.3.106 صدر 2025-03):

| النكهة | الإحداثي | حجم AAR تقريبي | ملاحظة |
|---|---|---|---|
| **minimal** (موصى به) | `org.linphone.minimal:linphone-sdk-android:5.3.106` | ~19 MB | بدون فيديو/GPL ثالث — أخف |
| كامل | `org.linphone:linphone-sdk-android:5.3.106` | ~53 MB | فيديو + كل الميزات |

### أين تُضاف

**أ)** في `settings.gradle.kts` (أو `red-app/build.gradle.kts` ضمن `repositories`):

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://download.linphone.org/maven_repository/") }
    }
}
```

**ب)** في `red-app/build.gradle.kts` ضمن `dependencies`:

```kotlin
dependencies {
    implementation("org.linphone.minimal:linphone-sdk-android:5.3.106")
    // باقي التبعيات...
}
```

> تأكّد أن `minSdk` ≥ 21 (liblinphone 5.3 يتطلب ذلك؛ مشروعك غالباً أعلى).
> لا حاجة لـ NDK لأن الـ AAR مبني مسبقاً.

---

## 2) الوحدة الجديدة

أُنشئت: `red-app/src/main/java/com/red/sovereign/calls/LinphoneSipClient.kt`

- تطبّق **نفس** `PstnWebRtcManager.PstnCallState` (IDLE/BRIDGING/REGISTERING/INVITING/RINGING/ACTIVE/ENDED/ERROR).
- تكشف واجهة `Events` مطابقة لـ `PstnWebRtcManager.Events` (onConnected/onRinging/onAnswered/onIncoming/onHangup/onError).
- `start(Credentials, Events)` → يُنشئ النواة ويسجّل على UC200 Pro (TLS + SRTP).
- `call(number)` / `answer()` / `hangup()` / `destroy()`.

مثال استخدام:

```kotlin
val client = LinphoneSipClient(context)
client.start(
    LinphoneSipClient.Credentials(
        extension = "112",                 // رقم الـ Extension على UC200 Pro
        password = "SECRET",               // سر الـ Extension
        pbxHost  = "192.168.11.3",         // عنوان UC200 Pro على شبكة الإدارة (مؤكَّد من المستخدم)
        pbxPort  = 5061,                    // TLS (استخدم 5060 إن اخترت Udp/Tcp)
        transport = TransportType.Tls
    ),
    events = object : LinphoneSipClient.Events {
        override fun onConnected() { /* مسجّل ✅ */ }
        override fun onRinging() { /* يرن الطرف الآخر */ }
        override fun onAnswered(u: Int, d: Int) { /* متصل ✅ */ }
        override fun onIncoming(sdp: String, from: String) { /* مكالمة واردة */ }
        override fun onHangup(cause: String?) { /* انتهت */ }
        override fun onError(msg: String) { /* خطأ */ }
    }
)

// مكالمة خارجية (مثلاً يمن موبايل):
client.call("771234567")
// أو داخلية:
client.call("105")
```

---

## 3) إعداد UC200 Pro (الواجهة عبر الـ IP)

ادخل `http://<UC200-Pro-IP>` من المتصفح على شبكة الإدارة.

### 3.1 إنشاء Extension للتطبيق
`PBX → Extensions → Add` وأنشئ مستخدماً (مثلاً `112`) مع **سر قوي**؛ هذا هو ما يُمرَّر لـ `LinphoneSipClient.Credentials`.

### 3.2 ربط UC2000-VE-8G كـ SIP Trunk
على UC2000: `Call Configuration → SIP Configuration` ← اجعل `SIP Server` = عنوان UC200 Pro،
`Is Register = Yes`، واربط كل منفذ SIM بحساب SIP [2](https://www.itsupportwale.com/blog/configure-dinstar-gsm-gateway-with-freepbx/).
على UC200 Pro: أنشئ **Trunk** يشير إلى UC2000 (البوابة تسجّل نفسها).

### 3.3 مسار صادر للموبايل
`PBX → Outbound Routes` ← أضف قاعدة: البادئات `77/78/71/73/70` → الـ Trunk الخاص بـ UC2000.
هكذا أي رقم شبكة من التطبيق يخرج عبر الشريحة المناسبة تلقائياً.

### 3.4 (اختياري) توجيه وارد
على UC2000: `Routing → Tel→IP` ← مكالمة واردة على شريحة → تُوجَّه إلى extension التطبيق (أو مجموعة رنين).

---

## 4) ربط الواجهة (PstnCallScreen)

`PstnCallScreen` تراقب `PstnCallState` — فبما أن `LinphoneSipClient` يبعث نفس القيم،
أسهل طريق: أنشئ غلافاً `PstnLinphoneManager` يستخدم `LinphoneSipClient` بنفس واجهة
`PstnWebRtcManager` (نفس `Events` + `PstnCallState` + `stateFlow`)، ثم استبدل مرجع
`PstnWebRtcManager` بـ `PstnLinphoneManager` في `AuthViewModel.dialPstn` وشاشة المكالمة.

التغييرات المطلوبة (نقاط فقط — لم تُطبَّق بعد):
1. `AuthViewModel.dialPstn` ← بدل `PstnWebRtcManager.call(...)` استخدم `PstnLinphoneManager`.
2. إزالة اعتماد `api.bridge(number)` (لم يعد ضرورياً للإشارات؛ UC200 Pro هو الـ PBX).
3. تزويد بيانات `Credentials` (extension/password/pbxHost) من إعدادات الأدمن أو من تزويد الخادم.

---

## 5) ملاحظات أمنية وتشغيلية

- **تشفير:** الإعداد الافتراضي TLS للإشارة + SRTP للوسائط على جانب التطبيق↔UC200 Pro.
  رجل UC2000→الموبايل **غير مشفّر أبداً** (طبيعة PSTN) — بلّغ المستخدمين أن مكالمات
  الشبكة ليست E2E.
- **شهادة UC200 Pro:** ذاتية التوقيع → عطّلتُ تحقّق الجذر في الكود للاختبار. للإنتاج
  ثبّت `core.rootCa` بشهادة الـ PBX بدل تعطيله.
- **المكالمات في الخلفية (Android):** SIP يحتاج اتصالاً مستمرلاً أو Push. أضف
  `PushConfig` (`core.setPushNotificationConfig`) + خدمة FCM لكي تعمل المكالمات الواردة
  خلف قفل الشاشة. هذا غير مُنفَّذ في `LinphoneSipClient` الحالي (يُركَب لاحقاً).
- **NAT:** إن كان التطبيق خارج شبكة الإدارة، فعّل STUN/ICE (`core.stunServer`) أو TURN.

---

## 6) التحقق (في بيئة أندرويد لديك)

لا يمكن تجميع مشروع أندرويد كامل هنا؛ لذا التحقق يتم لديك:
1. أضف التبعية + المستودع (القسم 1) ثم `./gradlew :app:assembleDebug`.
2. أنشئ Extension على UC200 Pro (القسم 3.1) وجرّب التسجيل عبر تطبيق **Linphone الرسمي**
   أولاً (UDP/TCP/TLS) للتأكد أن إعدادات UC200 Pro صحيحة قبل أي كود.
3. شغّل `LinphoneSipClient` بمعاملات الـ Extension، وتأكد أن `onConnected()` يُستدعى،
   ثم جرّب مكالمة داخلية (extension آخر) ثم خارجية (77xxxx).

---

## المصادر
- [1] linphone-sdk (GitHub/Belledonne) — ترخيص مزدوج GPLv3/AGPL + تجاري: https://github.com/BelledonneCommunications/linphone-sdk
- [2] دليل ربط بوابة DINSTAR GSM بـ FreePBX (نفس مبدأ trunk↔UC200 Pro): https://www.itsupportwale.com/blog/configure-dinstar-gsm-gateway-with-freepbx/
- وثيقة UC200 Pro IP PBX الرسمية (تدعم WebRTC/SIP/USSD/IVR): https://www.dinstar.com/WEB/files/15335/2024-09-04/UC200%20Pro%20IP%20PBX%20User%20Manual.pdf
