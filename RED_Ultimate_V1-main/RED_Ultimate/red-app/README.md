# red-app/ — تطبيق Android القانوني

> **الحالة:** نشط — Gradle module `:app`

## الوظيفة

التطبيق الذي ينتج APK: حساب username، موافقة الإدارة، هوية جهاز محلية، libsignal PQXDH/Double Ratchet، WebSocket، feed/groups/stories وسجل المكالمات الموحد. الميزات غير المكتملة معطلة بصريًا.

## المحتوى

`src/main/java/com/red/sovereign/` المصدر، `AndroidManifest.xml`، `build.gradle.kts`، اختبارات الوحدة.

## العلاقة بباقي المشروع

- راجع [`../settings.gradle.kts`](../settings.gradle.kts) لمعرفة ما يدخل البناء فعلًا؛ وجود المصدر لا يعني أنه مفعّل.
- الحدود القانونية موثقة في [`../W0_MODULE_BOUNDARIES.md`](../W0_MODULE_BOUNDARIES.md).
- خريطة النظام الكاملة في [`../docs/01-PROJECT-OVERVIEW.md`](../docs/01-PROJECT-OVERVIEW.md).

## التحقق

لا تُعلن ميزة هذا المجلد مكتملة إلا إذا دخلت بوابة البناء المناسبة واختبار runtime/جهازها. أسرار `.env` و`secrets/` ومفاتيح Android الخاصة لا تُحفظ في Git.

## الدفع السيادي (إيقاظ المكالمات — UnifiedPush + ntfy)

- لا دفع Google ولا خدماتها في أي مسار: الإيقاظ عبر موزّع UnifiedPush مستضاف ذاتياً (خدمة `ntfy` في `docker-compose.yml`).
- للتشغيل المحلي/الإنتاج:
  1. شغّل الحزمة (`docker compose up -d ntfy`) — يستمع على `NTFY_PORT` (افتراضي 2586).
  2. على الجهاز: ثبّت تطبيق ntfy ووجّهه إلى `NTFY_BASE_URL`، ثم افتح RED مرة واحدة — `VoipPushRegistrar` يسجّل الموزّع ويرفع نقطة النهاية إلى `POST /api/devices/push-token`.
  3. نفّذ مكالمة أثناء قتل التطبيق — `RedPushService.onMessage` يرن فوراً عبر `CallNotificationManager`.
- التشخيص: راقب بـ `adb logcat -s RedPushService:* VoipPushRegistrar:*`، وخادمياً وسوم `notification.push_sent/push_failed` (نقاط 404/410 تُقلَّم ذاتياً من `device_push_tokens`).
- بدون موزّع: البناء سليم والتطبيق يعمل، لكن الإيقاظ معطّل (تبقى مسارات WebSocket أثناء فتح التطبيق وصندوق العروض المعلقة `/api/calls/pending`).

## إسناد الأصوات (res/raw)

ملفات `redphone_busy.opus` و`redphone_outring.opus` و`webrtc_completed.mp3`
و`webrtc_disconnected.mp3` و`notification_simple_01.ogg` مقتبسة من Signal-Android
(© Open Whisper Systems ‏/ Signal Foundation، GPL-3.0) — دُمجت هنا بتوافق GPLv3§13
ضمن العمل الجامع AGPL-3.0. التوصيل المقترح (المرحلة 3): outring أثناء الاتصال،
busy عند BUSY، completed/disconnected عند انتهاء المكالمة.
