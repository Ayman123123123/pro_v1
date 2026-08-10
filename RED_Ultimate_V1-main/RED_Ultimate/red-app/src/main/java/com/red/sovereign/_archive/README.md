# 📦 أرشيف الملفات المهملة (Deprecated Archive)

> هذا المجلد يحتوي على ملفات قديمة/مكررة تم نقلها من الكود الإنتاجي.
> **لا تستورد من هذا المجلد في الكود الجديد.**

## الملفات

### `VoiceRecorder-core.kt.archived`
- **المسار الأصلي:** `com.red.sovereign.core.utils.VoiceRecorder`
- **التاريخ:** 2026-08-09
- **السبب:** أُعيد تفعيل نسخة محسّنة في `core/utils/VoiceRecorder.kt`، وبقيت هذه النسخة كأرشيف غير مترجم.
- **البديل النشط:** `com.red.sovereign.core.utils.VoiceRecorder`

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
- ملفات الأرشيف التي قد تسبب تكرار فئات يجب أن تبقى بامتداد غير `.kt` حتى لا تدخل build.
