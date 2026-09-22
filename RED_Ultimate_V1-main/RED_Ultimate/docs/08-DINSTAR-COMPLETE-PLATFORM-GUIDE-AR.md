# 🏛️ الدليل الشامل والنهائي لتشغيل وتطوير منصة RED Ultimate مع بوابة DINSTAR UC2000-VE-8G

---

## 📑 الفهرس

1. [نظرة عامة على المنظومة](#1-نظرة-عامة-على-المنظومة)
2. [مشغلو الاتصالات اليمنيون وتصحيح البادئات (Wikipedia + ITU E.164)](#2-مشغلو-الاتصالات-اليمنيون-وتصحيح-البادئات)
3. [بنية بوابة DINSTAR UC2000-VE-8G](#3-بنية-بوابة-dinstar-uc2000-ve-8g)
4. [الباكند (Spring Boot + Kotlin) والميزات المكتملة](#4-الباكند-spring-boot--kotlin)
5. [تطبيق الأندرويد والشاشات المكتملة](#5-تطبيق-الأندرويد-والشاشات-المكتملة)
6. [لوحة التحكم الإدارية (Admin Dashboard)](#6-لوحة-التحكم-الإدارية-admin-dashboard)
7. [الخوارزميات المتقدمة المستخدمة](#7-الخوارزميات-المتقدمة-المستخدمة)
8. [خطوات الربط والتشغيل الفعلي مع الجهاز (192.168.11.1)](#8-خطوات-الربط-والتشغيل-الفعلي)
9. [دليل استكشاف الأخطاء وحلها (Troubleshooting)](#9-دليل-استكشاف-الأخطاء-وحلها)

---

## 1. نظرة عامة على المنظومة

منصة **RED Ultimate** هي منظومة اتصالات سيادية متكاملة، تجمع بين:
- **المحادثات الفورية المشفرة طرفياً (E2EE)**
- **المكالمات الصوتية والمرئية عبر الإنترنت (VoIP 1080p WebRTC)**
- **المكالمات الجماعية وغرف الصوت والبث المباشر (SFU / Live Broadcast)**
- **المكالمات الخطية المباشرة (PSTN/GSM) عبر بوابة DINSTAR UC2000-VE-8G**
- **بوابة الرسائل القصيرة (SMS Gateway)**
- **لوحة إدارة متقدمة مبنية على React 19 + TypeScript + Ant Design**

---

## 2. مشغلو الاتصالات اليمنيون وتصحيح البادئات

### 🔍 التصنيف الرسمي والمعتمد (حسب ITU E.164 و ويكيبيديا)

| البادئة (Prefix) | اسم المشغل بالعربية | اسم المشغل بالإنجليزية | التقنية المستخدمة | الحصة السوقية التقريبية | اللون المعتمد |
|:---:|:---:|:---:|:---:|:---:|:---:|
| **71** | سبأفون | Sabafon | GSM / 4G LTE | ~30% | <span style="color:#E53935">🔴 أحمر `#E53935`</span> |
| **73** | يو (كانت MTN سابقاً) | YOU (Yemeni Omani United) | GSM / 4G LTE | ~25% | <span style="color:#FFB300">🟡 ذهبي `#FFB300`</span> |
| **77, 78** | يمن موبايل | Yemen Mobile | CDMA / 3G / 4G LTE | ~40% | <span style="color:#43A047">🟢 أخضر `#43A047`</span> |
| **70** | واي | Y Telecom | GSM / 4G LTE | صاعدة | <span style="color:#1E88E5">🔵 أزرق `#1E88E5`</span> |
| **10** | يمن 4G | Yemen 4G | LTE Data/Voice | صاعدة | <span style="color:#7C4DFF">🟣 بنفسجي `#7C4DFF`</span> |

> **⚠️ تنبيه تاريخي مهم:**
> شركة **MTN Yemen** تم بيعها وإعادة تسميتها في 2021-2022 إلى **الشركة العمانية اليمنية للاتصالات (YOU)**.
> البادئة **73** تابعة لـ **YOU**.
> شركة **يمن موبايل** تمتلك بادئتين رسميتين: **77** و **78**.
> شركة **واي** بادئتها **70** (وليست HiTel كما ورد خطأً في بعض الأكواد القديمة).

---

## 3. بنية بوابة DINSTAR UC2000-VE-8G

- **طراز الجهاز:** `Dinstar UC2000-VE-8G` (بوابة GSM تدعم 8 شرائح SIM)
- **عنوان الإدارة الافتراضي:** `https://192.168.11.1` (المنفذ `443`)
- **بيانات الدخول الافتراضية:** `admin` / `admin`
- **نوع واجهة البرمجة:** New Version HTTP/JSON API (إصدار البرنامج الثابت ≥ 1102)
- **المصادقة:** HTTP Digest Authentication (مع دعم Basic Auth كبديل تلقائي عبر `DispatchingAuthenticator`)
- **بروتوكول المكالمات الصوتية:** Asterisk AMI → PJSIP → Dinstar SIP Trunk
- **بروتوكول إدارة الشرائح وSMS وUSSD:** Dinstar HTTP/JSON API الموثق

---

## 4. الباكند (Spring Boot + Kotlin)

### 🚀 الخدمات المكتملة في الباكند:

1. **`DinstarHardwareService.kt`**:
   - مصادقة Digest/Basic مع كاش الاتصالات (`AuthenticationCacheInterceptor`)
   - ثقة شهادات SSL الموقعة ذاتياً على الشبكة المحلية
   - استعلام حالة المنافذ الثمانية (`/api/get_port_info`)
   - إعادة تعيين المنفذ (`/api/set_port_info?action=reset`)
   - إرسال واستعلام USSD (`/api/send_ussd`, `/api/query_ussd_reply`)
   - جلب سجل المكالمات CDR (`/api/get_cdr`)
   - إرسال واستعلام SMS (`/api/send_sms`, `/api/query_sms_result`, `/api/query_incoming_sms`)
   - تحويل المكالمات وتغذية الطاقة (`CallForward`, `power on/off`)
   - تصنيف المشغل التلقائي `resolveOperatorName()`

2. **`DinstarLoadBalancer.kt`**:
   - خوارزمية **WFQ (Weighted Fair Queuing)** لاختيار أفضل منفذ:
     - وزن قوة الإشارة ($W_{signal}$)
     - مكافأة مطابقة المشغل للمكالمة داخل الشبكة ($W_{operator} = +35$)
     - عقاب الاستخدام الزائد ($W_{usage} = -5 \times count$)
     - تنويع الدور Round-Robin ($W_{RR} = +8$)
   - تحرير المنفذ عند إنهاء المكالمة `releasePort()`

3. **`PstnCallService.kt`**:
   - توجيه المكالمات عبر Asterisk PJSIP مع التحقق من البادئات اليمنية
   - حد المكالمات اليومي بمؤقت ذري في Redis
   - دعم التنسيقات: `+967XXXXXXXXX`, `00967XXXXXXXXX`, `967XXXXXXXXX`, `07XXXXXXXX`, `7XXXXXXXX`

4. **`PstnCallController.kt`**:
   - بدء اتصال PSTN: `POST /api/pstn/calls`
   - إنهاء اتصال وتحرير المنفذ: `POST /api/pstn/calls/{callId}/hangup`
   - حالة المكالمات: `GET /api/pstn/status`

5. **`DinstarSmsController.kt`**:
   - إرسال SMS فردي/مجمّع: `POST /api/admin/dinstar/sms/send`
   - جلب نتائج الإرسال: `POST /api/admin/dinstar/sms/result`
   - جلب الرسائل الواردة: `GET /api/admin/dinstar/sms/incoming`
   - طابور الرسائل: `GET /api/admin/dinstar/sms/queue`
   - إيقاف مهمة: `POST /api/admin/dinstar/sms/stop`

---

## 5. تطبيق الأندرويد والشاشات المكتملة

### 📱 الشاشات والمكونات المكتملة:

| الشاشة | المسار (Route) | الوصف |
|---|---|---|
| **DinstarTab** | `dinstar_tab` | الشريحة الرئيسية لمراقبة البوابة (6 أقسام + بطاقات المنافذ + CDR) |
| **DinstarAdminScreen** | `dinstar_admin` | لوحة تحكم إدارية كاملة (اختيار ذكي + CallForward + PortPower + إحصائيات) |
| **PstnCallScreen V2** | `pstn_call/{number}` | شاشة المكالمة الخطية الحية المتصلة بـ WebSocket مع إشارة حية وتحكم كامل |
| **IncomingPstnCallScreen** | `incoming_pstn_call/{number}/{port}` | شاشة المكالمة الواردة (قبول/رفض + نبض رنين + رفض تلقائي بعد 30ث) |
| **DinstarSmsScreen** | `dinstar_sms` | بوابة إرسال واستقبال الرسائل القصيرة (GSM7BIT / UCS2) |
| **DevicesScreen** | `devices` | إدارة الأجهزة والجلسات النشطة (هواتف، ويب، بوابة DINSTAR) مع إنهاء الجلسات عن بُعد |
| **PstnDialerScreen** | `pstn_dialer` | لوحة أرقام PSTN ذكية توضح المشغل وأفضل منفذ قبل الاتصال |
| **SovereignCallSystem** | `call_log`, `voip_call` | نظام المكالمات الشامل لجميع الأنواع (صوتي، فيديو، مؤتمر، بث، خطي) |

### 🛠️ البنية التحتية البرمجية للأندرويد:
- **`DinstarViewModel.kt`**: إدارة الحالة المركزية مع Circuit Breaker و Sliding Window و OkHttp ConnectionPool و SharedFlow.
- **`DinstarWebSocketBridge.kt`**: اتصال WebSocket حي مع الباكند لاستقبال أحداث `PORT_STATUS` و `CDR` و `SMS` و `USSD` و `EXCEPTION`.
- **`DinstarModels.kt`**: نماذج البيانات الكاملة مع تصنيف مشغلي اليمن `YemenOperator`.

---

## 6. لوحة التحكم الإدارية (Admin Dashboard)

- **التقنية:** React 19 + TypeScript + Vite 7 + Ant Design 6
- **الصفحات:**
  - `DinstarTab.tsx`: عرض حي للمنافذ الثمانية مع تصنيف المشغل الصحيح (سبأفون، يو، يمن موبايل، واي، يمن 4G)
  - `DinstarControl.tsx`: تحكم مباشر بالمنافذ، إرسال USSD، إعادة تعيين الوحدات، واستعراض CDR
  - `Dashboard.tsx`: لوحة مؤشرات الأداء الحية للمنصة
  - `MasterLayout.tsx`: هيكل اللوحة باللغة العربية مع دعم الثيم المظلم

---

## 7. الخوارزميات المتقدمة المستخدمة

### 1. خوارزمية Weighted Fair Queuing (WFQ) لاختيار الشرائح:
$$\text{Score}(P) = W_{\text{signal}} \cdot \text{Signal} + W_{\text{match}} \cdot \mathbb{I}_{\text{operator}} - W_{\text{usage}} \cdot \text{Usage}(P) + W_{\text{RR}} \cdot \mathbb{I}_{\text{round\_robin}} + W_{\text{success}} \cdot \text{SuccessRate}$$

- تضمن أفضل جودة اتصال (أعلى إشارة).
- توفر تكلفة الاتصال باختيار شريحة من نفس شبكة الرقم الهدف (On-Net).
- تمنع حظر الشرائح بتوزيع الحمل بالتساوي.

### 2. قاطع الدائرة الذكي (Circuit Breaker):
- **CLOSED:** الحالة الطبيعية (كل الطلبات تمر).
- **OPEN:** بعد 5 أعطال متتالية (يوقف الطلبات لمدة 30 ثانية لحماية النظام).
- **HALF-OPEN:** بعد انتهاء المدة (يرسل طلب اختبار للتحقق من عودة البوابة).

### 3. التراجع الأسي (Exponential Backoff):
- إعادة الاتصال بـ WebSocket: $1s \to 2s \to 4s \to 8s \to \dots \to 60s$ كحد أقصى.

---

## 8. خطوات الربط والتشغيل الفعلي

1. **توصيل كابل الشبكة:**
   - اربط منفذ `LAN` في جهاز DINSTAR UC2000-VE-8G بنفس شبكة السيرفر المحلية (VLAN الإدارة).
   - عنوان IP الافتراضي للجهاز: `192.168.11.1`.

2. **تفعيل New Version API في واجهة الجهاز:**
   - افتح المتصفح على: `https://192.168.11.1`
   - سجّل الدخول: `admin` / `admin`
   - اذهب إلى: **Mobile Configuration** $\to$ **Basic Configuration**
   - اختر: **API Configuration** $\to$ فعّل **New-version API**
   - احفظ الإعدادات وأعد تشغيل خدمات API بالجهاز.

3. **إعداد SIP Trunk في Asterisk:**
   - اضبط الـ Trunk في `pjsip.conf` ليشير إلى IP البوابة `192.168.11.1:5060`.
   - اضبط الـ Dialplan في `extensions.conf` لتوجيه مكالمات البادئات `71, 73, 77, 78, 70, 10` إلى DINSTAR.

4. **تشغيل الباكند واللوحة:**
   ```bash
   # تشغيل لوحة التحكم
   cd admin_dashboard && npm run build && npm run preview

   # تشغيل الباكند (Spring Boot)
   cd backend-server && ./gradlew bootRun
   ```

---

## 9. دليل استكشاف الأخطاء وحلها (Troubleshooting)

| العرض | السبب المحتمل | الحل |
|---|---|---|
| البوابة تظهر "غير متصل" | خطأ في الوصول لـ IP أو الشهادة | تحقق من اتصال الشبكة بـ `192.168.11.1` وتأكد أن New API مفعل في الجهاز |
| فشل المصادقة 401 | كلمة المرور غير صحيحة | تأكد من `red.dinstar.username=admin` و `red.dinstar.password=admin` في `application.yml` |
| المشغل يظهر "UNKNOWN" | الشريحة غير مسجلة بالشبكة أو إشارة ضعيفة | افحص الهوائيات ووضع الشريحة في الجهاز وتأكد من التسجيل في الشبكة |
| انقطاع المكالمات فوراً | رصيد الشريحة منتهي أو حظر من المزود | تحقق من رصيد الشريحة عبر إرسال كود USSD من لوحة التحكم |
| Circuit Breaker مفتوح | فشل متكرر في الاتصال بالباكند | انتظر 30 ثانية لإعادة المحاولة التلقائية أو اضغط "تحديث" |

---

**🏛️ تم البناء والتطوير والتوثيق بأعلى معايير الجودة والاحترافية لمنصة RED Ultimate السيادية.**
