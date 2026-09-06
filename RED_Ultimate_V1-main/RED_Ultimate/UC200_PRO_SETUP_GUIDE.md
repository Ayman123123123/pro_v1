# 🛠️ دليل إعداد UC200 Pro + UC2000-VE-8G لتطبيق RED (مكالمات SIP عبر Linphone)

> هذا الدليل يغطي **إعداد الأجهزة** (لا كود) لربط:
> `تطبيق RED (Linphone SIP) ⇄ UC200 Pro (IP-PBX) ⇄ UC2000-VE-8G (GSM gateway) ⇄ شبكات الاتصالات`
>
> يفترض أن الجهازين موصولان بـ **شبكة الإدارة نفسها** (مثلاً `192.168.11.0/24`):
> - UC200 Pro على `192.168.11.3`
> - UC2000-VE-8G على `192.168.11.2`

---

## 1) على UC200 Pro — إنشاء Extension للتطبيق

ادخل `http://192.168.11.3` من المتصفح.

1. **PBX → Extensions → Add Extension**
   - **Extension Number**: `112` (سيكون هذا `PstnLinphoneConfig.extension`)
   - **Display Name**: `RED-App` (اختياري)
   - **Password / SIP Secret**: سر قوي (سيكون `PstnLinphoneConfig.password`)
   - احفظ.
2. كرّر لكل مستخدم تطبيق تريد منحه خطاً (كل واحد Extension مختلف).

> هذه بيانات `PstnLinphoneConfig` التي يحتاجها التطبيق:
> `extension=112`, `password=<secret>`, `pbxHost=192.168.11.3`, `pbxPort=5060` (UDP) أو `5061` (TLS).

---

## 2) على UC2000-VE-8G — تسجيله كـ Trunk على UC200 Pro

### 2.1 إعداد UC2000 ليُسجّل على UC200 Pro
ادخل `http://192.168.11.2`.

1. **Call Configuration → SIP Configuration**
   - **SIP Server Address**: `192.168.11.3` (عنوان UC200 Pro)
   - **SIP Server Port**: `5060` (أو `5061` إن فعّلت TLS)
   - **Is Register**: **Yes**
   - **Transport**: UDP (أو TLS إن استخدمته)
   - احفظ (الجهاز يُعيد التشغيل عادةً).

2. **Call Configuration → Port Configuration**
   - لكل منفذ (SIM) عيّن **Username / Password** للحساب الذي سيُسجَّل على UC200 Pro.
   - الطريقة الأسهل: اجعل UC2000 يولّد 8 حسابات (Port01..Port08) ثم أضفها على UC200 Pro كـ **SIP Trunk** (الخطوة 3).
   - تأكد أن كل منفذ `Status = REGISTERED` وله إشارة (`signalUsable=true`).

### 2.2 (بديل) ترك UC2000 يسجّل ذاتياً
إن كان UC2000 بوضع "Auto SIP Account"، فإنه يُرسل الـ REGISTER تلقائياً لـ UC200 Pro ويظهر هناك كـ trunk/peer. في هذه الحالة تخطَّ الخطوة 3 وأنشئ فقط **Outbound Route** (الخطوة 4).

---

## 3) على UC200 Pro — إنشاء Trunk لـ UC2000

1. **PBX → Trunk → Add (SIP Trunk)**
   - **Trunk Name**: `UC2000-GSM`
   - **Hostname/IP**: `192.168.11.2`
   - **Port**: `5060`
   - **Transport**: UDP (أو TLS)
   - **Authentication**: None من جهة UC200 Pro (لأن UC2000 هو الذي يُسجّل نفسه). إن طلب UC2000 بيانات، أدخلها المطابقة لإعدادات Port Configuration.
   - احفظ.

> إن أردت توجيه كل شريحة لوجهة محددة (لموازنة أحمال ذكية)، أنشئ **Trunk منفصل لكل منفذ SIM** (Port01..Port08) بدل trunk واحد — عندها يمكن للخادم اختيار الـ trunk.

---

## 4) على UC200 Pro — مسار صادر للموبايل

1. **PBX → Outbound Routes → Add**
   - **Name**: `Yemen-Mobile-Out`
   - **Dial Pattern** (بادئات اليمن):
     - `77.` , `78.` (يمن موباile)
     - `71.` (سبأفون)
     - `73.` (يو)
     - `70.` (واي)
   - **Trunk Sequence**: `UC2000-GSM` (أو الـ trunk المحدد للشريحة)
   - احفظ.

> هكذا أي رقم يمني من التطبيق → UC200 Pro يطابق البادئة → يوجّه لـ trunk UC2000 → الشريحة المناسبة → GSM.

---

## 5) (اختياري) مسار وارد — مكالمة من الموبايل إلى التطبيق

على UC2000:
1. **Routing Configuration → Tel→IP Routing**
   - لكل منفذ SIM: **Destination** = عنوان IP (اختر `IP`)، والوجهة = `sip:112@192.168.11.3` (extension التطبيق على UC200 Pro).
   - أو اجعل UC2000 يرسل الوارد إلى مجموعة رنين (Ring Group) في UC200 Pro.

> عندها: مكالمة واردة على شريحة → UC2000 يُرسل INVITE إلى UC200 Pro → UC200 Pro يرنّ extension `112` (التطبيق).

---

## 6) (موصى به) تشفير التطبيق ⇄ UC200 Pro

- على UC200 Pro فعّل **TLS (المنفذ 5061)** و**SRTP** للـ Extension.
- في `PstnLinphoneConfig` اجعل `pbxPort=5061` و`transport=Tls` (هذا ما فعلناه افتراضياً في الكود).
- ⚠️ شهادة UC200 Pro ذاتية التوقيع على LAN → الكود عطّل تحقّق الجذر (`core.isSslRootCaVerificationEnabled=false`) للاختبار. **للإنتاج** ثبّت شهادة الـ PBX في `core.rootCa`.

---

## 7) تزويد التطبيق ببيانات التسجيل (الفجوة المتبقية)

التطبيق يحتاج `extension/password/pbxHost` ليُسجّل. الخيارات:
- **(أ) إعدادات أدمن**: شاشة في التطبيق يدخل فيها المسؤول بيانات الـ Extension لكل مستخدم.
- **(ب) تزويد خلفي**: نقطة نهاية في الخادم ترجع حساب SIP الخاص بالمستخدم من UC200 Pro (مشابه لما كان `api.bridge` يرجعه لأستريسك).
- **(ج) مؤقت للاختبار**: تعبئة `PstnLinphoneConfig` مباشرةً بقيم ثابتة في الكود.

حتى تُملأ، المكالمات لن تعمل (العميل لا يملك بيانات التسجيل).

---

## 8) التحقق (في بيئتك)

1. شغّل `./gradlew :app:assembleDebug` للتأكد من تجميع التكامل.
2. جرّب **تطبيق Linphone الرسمي** أولاً بنفس الـ Extension على UC200 Pro (UDP/TCP/TLS) — إن نجح التسجيل فإعدادات UC200 Pro صحيحة.
3. شغّل تطبيق RED، املأ `PstnLinphoneConfig`، وتأكد أن `onConnected()` يُستدعى (مسجّل ✅).
4. اتصل على extension داخلي آخر (مكالمة داخلية).
5. اتصل على رقم موبايل (77xxxx) — يجب أن تخرج عبر شريحة UC2000.
6. (اختياري) اتصل من موبايل على أحد الشرائح — يجب أن يرنّ التطبيق (وارد).
