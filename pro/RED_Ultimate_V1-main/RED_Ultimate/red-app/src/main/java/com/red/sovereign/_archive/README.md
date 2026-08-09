# 📦 أرشيف الملفات المهملة (Deprecated Archive)

> هذا المجلد يحتوي على ملفات قديمة/مكررة تم نقلها من الكود الإنتاجي.
> **لا تستورد من هذا المجلد في الكود الجديد.**

## الملفات

### `VoiceRecorder-core.kt`
- **المسار الأصلي:** `com.red.sovereign.core.utils.VoiceRecorder`
- **التاريخ:** 2026-08-09
- **السبب:** Dead code — لم يكن مستخدماً. الـ `VoiceMessageViewModel` يستخدم `MediaRecorder` مباشرة.
- **البديل النشط:** `com.red.sovereign.media.VoiceMessageViewModel`

### `VoiceRecorder-features-chat.kt`
- **المسار الأصلي:** `com.red.sovereign.features.chat.VoiceRecorder`
- **التاريخ:** 2026-08-09
- **السبب:** Dead code مكرر (نفس المنطق في `core/utils/VoiceRecorder` لكن بدون معالجة أخطاء).
- **البديل النشط:** `com.red.sovereign.media.VoiceMessageViewModel`

### `MediaBubble-features-chat.kt`
- **المسار الأصلي:** `com.red.sovereign.features.chat.MediaBubble`
- **التاريخ:** 2026-08-09
- **السبب:** Dead code — كان `Composable` لعرض الفقاعات (IMAGE, VIDEO, FILE) لكن لا أحد يستدعيه. الـ `RedDashboard.kt` يستخدم `AttachmentMessage` و `VoiceMessage` منفصلتين بدلاً منه.
- **البديل النشط:** `VoiceMessage` + `AttachmentMessage` في `RedDashboard.kt`

---

## 📋 السياسة

- **لا تحذف** الملفات من هذا المجلد إلا بعد 6 أشهر من الأرشفة.
- إذا احتجت استعادة ملف، انقله إلى مكانه الأصلي وأزل هذا الـ README.
- الملفات هنا لا تُجمَع في الـ production build (لكن Gradle لا يستثنيها افتراضياً).
