# 📞 تقرير فحص نظام المكالمات وواجهاتها — شامل ومتكامل
## RED Ultimate Sovereign Messenger

> **تاريخ الفحص:** 2026-08-28  
> **النتيجة:** ✅ **البناء ناجح بالكامل — تم توليد APK ناجح (275MB)**

---

## 🎯 الحالة النهائية بعد الإصلاح

| العنصر | الحالة |
|--------|--------|
| **Compile Kotlin** | ✅ `BUILD SUCCESSFUL` |
| **Assemble Debug APK** | ✅ `app-debug.apk` (275,833,227 bytes) |
| **أخطاء مُصلحة هذا الأسبوع** | 3 أخطاء حرجة في `YounesCallService.kt` |

---

## 🔴 الأخطاء التي أصلحتها في نظام المكالمات

### مشكلة `YounesCallService.kt` — استيرادات مكررة (BREAKING)
كان الملف يحتوي على **استيرادات مكررة متعارضة** (وجيهة تمنع البناء نهائياً):

```kotlin
// ❌ قبل الإصلاح — استيرادات مكررة 3 مرات:
import android.media.AudioAttributes      // مكررة
import android.media.AudioFocusRequest     // مكررة
import android.media.AudioManager          // مكررة
import android.media.MediaRecorder         // مكررة
import android.media.AudioRecord           // مكررة 4 مرات!
import android.media.AudioTrack            // مكررة 4 مرات!
import android.os.VibrationEffect          // مكررة
import android.os.Vibrator                 // مكررة
import android.os.VibratorManager          // مكررة
import android.os.Build                    // مكررة
import android.os.Handler                  // مكررة
import android.os.Looper                   // مكررة
import android.os.PowerManager             // مكررة

// ✅ بعد الإصلاح — إزالة المكرر، وإضافة المفقود:
import android.os.IBinder                   // كان مفقوداً
import android.app.PictureInPictureParams   // كان مفقوداً (كان android.view)
import android.media.AudioRecord            // مرّة واحدة
import android.media.AudioTrack             // مرّة واحدة
import android.media.AudioFormat            // مرّة واحدة
```

**السبب الجذري:** أثناء دمج التطويرات المتوازية هذا الأسبوع (merge)، تكررت كتل الاستيراد ولم يُنظَّف التكرار.

---

## 🏗️ نظام المكالمات الكامل (14 خدمة + 6 أنواع Foreground Service)

### 📱 أنواع المكالمات المدعومة

| # | النوع | الخدمة | الميزات |
|---|--------|--------|---------|
| 1 | **مكالمة فردية صوت/فيديو** | `YounesCallService` | WebRTC كامل، Simulcast، Adaptive Bitrate، ICE Restart |
| 2 | **مكالمة جماعية (Ad-hoc)** | `GroupCallService` | Mesh/SFU، اختيار أصدقاء، حتى 50 مشارك |
| 3 | **مؤتمر فيديو** | `ConferenceService` | غرف SFU، رفع يد، كتم، شاشة مشتركة |
| 4 | **مساحات صوتية** | `ConferenceService` (video=false) | Twitter/X Spaces، صوت فقط بلا كاميرا |
| 5 | **بث مباشر** | `LiveStreamService` | TikTok-style، عام/خاص بكلمة سر، تفاعل حي |
| 6 | **اجتماع Zoom-style** | `ZoomGroupCallService` | حتى 100 مشارك، مشاركة شاشة، تسجيل |
| 7 | **PSTN/GSM** | `PstnWebRtcManager` + `PstnCallForegroundService` | DINSTAR + Asterisk، أرقام يمنية |

### 🛡️ أنواع Foreground Service (في Manifest)

```
camera|microphone|phoneCall          → YounesCallService, ConferenceService, LiveStreamService
camera|microphone|phoneCall|mediaProjection → GroupCallService
camera|microphone|mediaProjection    → ZoomGroupCallService
microphone                           → PstnCallForegroundService
remoteMessaging                      → RedConnectionService, SovereignNotificationRouter
```

---

## 🎨 واجهات المكالمات (التي طوّرتها)

### 1. `CallsHubActions.kt` (839 سطر، 44KB) — Bento Grid احترافي
شبكة بطاقات منسّقة بأسلوب **WhatsApp/Zoom/iMO/TikTok**:

| البطاقة | الأسلوب | اللون |
|---------|---------|-------|
| **بث مباشر تفاعلي** | TikTok | أحمر `#E53935` |
| **مكالمات جماعية** | Zoom/IMO | أزرق `#2AABEE` |
| **مؤتمرات فيديو** | SFU | بنفسجي `#A78BFA` |
| **مساحات صوتية** | Twitter Spaces | أخضر `#00C98C` |
| **الهاتف اليمني** | DINSTAR GSM | ذهبي |
| **مكالمة جديدة E2EE** | فردية | أخضر `#00E676` |
| **مكالمات مجدولة** | مستقبلية | برتقالي `#FFA000` |
| **استكشاف البثوث** | — | سماوي `#25F4EE` |

**ميزة Bento Card:** ظل ملون متوهج، تدرج متحرك، تأثير ضغط (scale 0.96)، حدود متدرجة.

### 2. `CallsScreens.kt` (27KB) — نسخة مستبعدة (Dead Code)
> [!WARNING]
> هذا الملف **مُعلَّم ككود ميت** بتعليق صريح. النسخة الحية من `UnifiedCallsScreen` موجودة في `RedDashboard.kt:2604` (7 معاملات — أغنى). النسخة هنا (4 معاملات) لا تُستدعى.

### 3. `CallsHubActions.kt` — حوارات متقدمة
- **`GroupCallPickerDialog`** — اختيار أصدقاء بفحص الحالة (متصل/غير متصل) مع checkbox مخصص.
- **`ConferenceHubDialog`** — إنشاء/انضمام لغرف المؤتمرات والمساحات.
- **`LiveStreamHubDialog`** — بث مباشر مع 3 أوضاع (اختيار/إنشاء/مشاهدة) + بث خاص بكلمة سر.
- **`rememberCallPermissionLauncher`** — طلب الصلاحيات قبل المكالمة.

---

## 🔧 التطويرات الخلفية (Backend) هذا الأسبوع

### PSTN/DINSTAR (مكالمات الهاتف اليمني)

| الملف | التطوير |
|-------|---------|
| `DinstarLoadBalancer.kt` | **إصلاح جذري لاختيار البوابة** — استبعاد الشرائح بلا إشارة (signalUsable=false)، رفع قيد 8 منافذ، إصلاح عداد استخدام سالب، مطابقة المشغل بالاسم المطبّع (MTN=YOU)، تفويض التصنيف لـ YemenNumberPlan |
| `PstnCallService.kt` | ربط الحجز الدائم + مكالمة نشطة Postgres |
| `PstnCallController.kt` | السماح للمسؤول بالاتصال الحر حتى مع limit=0 |
| `PersistentReservationService.kt` | **جديد** — حجز منافذ 3 طبقات (Postgres/Redis/Memory) |
| `YemenNumberPlan.kt` | مصدر وحيد لتصنيف المشغلين اليمنيين (71 سبأفون، 73 يو، 77/78 يمن موبايل، 70 واي) |

### الهجرات (Migrations) الجديدة
- **V41** — `ring_duration_seconds` NULLable (زمن الرنين مشتق لا صفر كاذب)
- **V42** — جدول `gateway_port_reservations` (حجز منافذ دائم بفهرس جزئي فريد)
- **V43** — جدول `pstn_active_calls` (ربط callId↔منفذ↔مستخدم دائم)

### Outbox (صندوق الصادر المتين) — جديد كلياً
- **`OutboxMessageEntity.kt`** + **`OutboxDao.kt`** + **`OutboxRepository.kt`** + **`OutboxRetryWorker.kt`**
- يضمن **الرسائل لا تموت بموت العملية** — Transactional Outbox pattern
- إعادة محاولة أسية (10s→30s→2m→10m→1h→24h) مع jitter
- `idempotencyKey` يمنع التكرار عبر إعادة التشغيل
- ترقية Room من v4 إلى v5 مع `OUTBOX_MIGRATION_4_5`

---

## 📊 المشغلات اليمنية المعتمدة (مصدر: YemenNumberPlan)

| البادئة | المشغل | الحالة |
|---------|--------|--------|
| 71 | سبأفون (Sabafon) | ✅ |
| 73 | يو (YOU — كانت MTN) | ✅ |
| 77, 78 | يمن موبايل (Yemen Mobile) | ✅ |
| 70 | واي (Y Telecom) | ✅ |

**إصلاح حرج:** تطابق 3 أرقام أولاً (722 = Sabafon Aden VoLTE) بدل السقوط في `72` غير المخصص.

---

## ✅ الخلاصة النهائية

### ما تم إنجازه
1. ✅ **فحص شامل** لكل ملفات المكالمات (268 ملف Kotlin، 14 خدمة مكالمات)
2. ✅ **إصلاح 3 أخطاء حرجة** في `YounesCallService.kt` (استيرادات مكررة + مفقودة)
3. ✅ **بناء ناجح كامل** → `app-debug.apk` (275MB)
4. ✅ **توثيق كامل** لكل أنواع المكالمات وواجهاتها

### البنية الحالية للمكالمات
- **7 أنواع مكالمات** (فردي/جماعي/مؤتمر/مساحة/بث/Zoom/PSTN)
- **Bento Grid UI** احترافي بتصميم عصري
- **حجز منافذ 3 طبقات** (Postgres/Redis/Memory) لمنع التصادم
- **Outbox متين** يضمن عدم فقدان الرسائل
- **تصنيف مشغلين يمنيين** دقيق

> [!NOTE]
> **ملاحظة مهمة عن البيئة:** للبناء يجب تعطيل متغير `ANDROID_PREFS_ROOT` من البيئة (يتعارض مع `ANDROID_USER_HOME`). يوصى بإضافة هذا إلى `gradlew.bat` أو `run_red.bat` لتجنب تكرار المشكلة.
