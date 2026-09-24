# إزالة نطاق الاتصالات (PSTN/Dinstar/SIP) — 2026-09-24

## 1) ماذا أُزيل
- النطاق الهاتفي الملغي من الكود المشحون (`backend-server/src`, `admin_dashboard/src`, `dinstar-config/`, `pstn-asterisk/`, `docker-compose.pstn-*.yml`, `scripts/dinstar-*`) — إجمالي 97 ملفًا محذوفًا حسب `git diff --name-status` بتاريخ التنفيذ.
- أبرز الفئات:
  - `backend-server/.../pstn/` (18 ملفًا: `PstnManager`, `PstnCallService`, `PstnController`, `YemenNumberPlan`, `Dinstar*`...).
  - `backend-server/.../dinstar/` + `controllers/Dinstar*` + `controllers/PstnBindingController` + `services/DinstarHardwareService` + `websocket/PstnEventWebSocketHandler` + `sms/` (6 ملفات).
  - `backend-server/src/test/.../Dinstar*`, `Pstn*`, `Sms*` (16 اختبارًا للعقود والتوجيه).
  - `admin_dashboard/src/pages/` (9: `DinstarControl`, `PstnManagement`, `PortControl`, `SimInventory`, `SmsTemplates`, `CdrAnalysis`, `NumberLearningCard`...) + `components/SmsInbox`, `WebRtcDialer`.
  - `dinstar-config/` (19 سكربت فحص/تهيئة) + `pstn-asterisk/` (13: `pjsip.conf`, `extensions.conf`, `dongle.conf`...) + `docker-compose.pstn-host.yml` + `docker-compose.pstn-only.yml` + `scripts/dinstar-*.ps1` (6).
- ما لم يُزل عمدًا: `android.telecom` وواجهات الرنين (`TelecomBridge.kt`, `TelecomDisconnectGuard`, `AndroidTelecomUtil`) — واجهة نظام أندرويد للرنين، ليست PSTN، ومعفاة نصًا في الحارس.

## 2) لماذا
- قرار سيادة مرحلة 8: صفر `PSTN/Dinstar/SIP/USSD/SMPP/GSM/telecom_gateways` في الكود المشحون — كل شيء محلي، وFirebase/FCM مرفوض أصلًا.
- الحارس `check-sovereign-dod.sh` (قواعد 1/2/3) كان سيفشل على أي بقايا توكنز أو أسماء `dinstar|pstn|yemen|asterisk` خارج الأرشيف.
- الأرشيف نفسه يحمل هذه التوكنز/الأسماء (`Pstn*`, `Dinstar*`)، فوجب إعفاء مساره وإلا الأرشيف نفسه يُفشل الحارس.

## 3) أين أُرشف
- `RED_Ultimate_V1-main/docs/الأرشيف/telecom-removed-2026-09-24/backend-server/src/main/kotlin/com/red/server/...`
  - مرآة بنفس البنية: `pstn/` + `dinstar/` + `controllers/Dinstar*` + `controllers/PstnBindingController` + `services/DinstarHardwareService` + `sms/` + `websocket/PstnEventWebSocketHandler` (32 ملفًا مرصودًا بتاريخ التوثيق).
- ملاحظة أمانة: باقي المحذوفات (اختبارات الباك-إند، صفحات الأدمِن، `dinstar-config/`, `pstn-asterisk/`, `docker-compose.pstn-*`, `scripts/dinstar-*`) مرصودة كحذف في `git diff` ولم تُرصد نسخة مؤرشفة لها داخل هذا المجلد بتاريخ الفحص — لا تعيدها إلى `src` دون مراجعة سيادة.

## 4) كيف يُعاد تفعيل الحارس (تشديد)
- التغيير المطبق 2026-09-24 في النسختين معًا (`RED_Ultimate_V1-main/scripts/check-sovereign-dod.sh` + `scripts/check-sovereign-dod.sh`): أُضيف `| grep -v "docs/الأرشيف/"` إلى فحص Firebase (ق1) وفحص التوكنز الهاتفي (ق2) وفحص الأسماء (ق3)، مع توثيق رأسي. لا شيء آخر ضُعّف (بقيت استثناءات `/_archive/`, `db/migration`, `docs/archive/`, `provenance`, `android.telecom`, `SIP_REALM`, `younes_icon_master.png` كما هي).
- لإعادة التشديد الكامل: احذف سلاسل `grep -v "docs/الأرشيف/"` الثلاث من النسختين، ثم شغّل:
  - `bash RED_Ultimate_V1-main/scripts/check-sovereign-dod.sh`
  - `bash scripts/check-sovereign-dod.sh`
  - وتوقع خروج `1` يشير إلى `docs/الأرشيف/telecom-removed-2026-09-24` — هذا يثبت أن الحارس عاد صارمًا. أعد السطر بعد التحقق.
- CI: وظيفة `sovereign-dod` يجب أن تخرج `0` على الكود المشحون؛ أي إعادة ملف من الأرشيف إلى `src` تُفشلها عمدًا.
