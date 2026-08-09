# أرشيف تطبيق Android القديم (مرجع تاريخي)

هذا المجلد يحفظ **نسخة أقدم من تطبيق Android** (حزم `com.red.core` و `com.red.features`)
خارج البناء الحالي. التطبيق الرسمي الحديث هو `red-app/` (حزمة `com.red.sovereign`).

## ما تم دمجه في red-app الحديث ✅
| الملف القديم | المكان الجديد في red-app |
|---|---|
| `core/network/MinioUploader.kt` | `red-app/.../core/network/MinioUploader.kt` |
| `core/utils/MediaCompressor.kt` | موجود في `red-app/.../core/utils/` |
| `core/utils/VideoTrimmer.kt` | موجود في `red-app/.../core/utils/` |
| `core/utils/VoiceRecorder.kt` | `red-app/.../core/utils/VoiceRecorder.kt` |
| `core/delivery/BurnManager.kt` | `red-app/.../core/delivery/BurnManager.kt` (معاد ربطه بـ MessageStore) |

## ما يغطيه red-app الحديث (بدائل أحدث)
- `features/calls/VoipEngine.kt` ← `red-app/.../calls/WebRtcEngine.kt`
- `features/calls/WebRtcSignaler.kt` ← `red-app/.../calls/CallSignalingClient.kt`
- `features/calls/RedVoipMaster.kt` ← `red-app/.../calls/WebRtcEngine.kt`
- `features/calls/LiveBroadcastManager.kt` ← `red-app/.../calls/LiveStreamService.kt`
- `features/pstn/PstnEngine.kt` ← `red-app/.../calls/TelecomBridge.kt`

## الملفات الفريدة المتبقية (أرشيف فقط — لا تُدمج لأن حزمها قديمة)
الملفات المتبقية هنا تمثل ميزات كاملة من نسخة سابقة؛ إعادة دمجها تتطلب
إعادة تسمية حزم شاملة. تُحفظ كمرجع تاريخي ولا تُبنى.
