# 05 — تكامل DINSTAR UC2000-VE-8G مع يونس

## الجهاز الفعلي

`UC2000-VE-8G` بوابة 8 قنوات GSM (850/900/1800/1900 MHz)، وليست FXS/FXO تقليدية. تحتوي 8 SIM hot-swappable و8 موصلات SMA وواجهتي Ethernet وRS232 USB-B. وظيفتها تحويل SIP/RTP إلى الشبكة الخلوية (GSM فقط) والعكس.

> **ملاحظة**: جهاز المستخدم يحمل ملصق 8G لكن الـ Userboard يظهر لاحقة L2 وVoLTE مفعّل — مما قد يشير إلى أن firmware مخصص لنسخة 8T (LTE) ومثبت على 8G بالخطأ. يُرجى التأكد مع دعم Dinstar بأن firmware 04240302 متوافق مع HWID 7036-cf4b-3125.

المراجع الرسمية: صفحة [UC2000-VE](https://www.dinstar.com/GSM-3G-LTE-voip-gateway/4-8-ports/)، [datasheet](https://www.dinstar.com/WEB/files/15277/2018-09-06/UC2000-VE%20GSM&LTE%20VoIP%20Gateway%20Datasheet.pdf)، [manual](https://www.dinstar.com/WEB/files/15278/2018-09-06/UC2000-VE&VF&VG_GSM%20_LTE_VoIP_Gateway_User_Manual.pdf)، و[HTTP API](https://www.dinstar.com/WEB/files/13151/2018-06-05/Dinstar%20GSM%20Gateway%20HTTP%20API-v202011.pdf).

## ما تدعمه الوثائق

- SIP v2 UDP/TCP/TLS وRTP/SRTP وفق firmware/license.
- mobile↔VoIP، trunk/group/routing/manipulation.
- G.711A/U وG.723.1 وG.729A/B، وخصائص أخرى حسب firmware.
- SMS/USSD/PIN وHTTP JSON API/SMPP.
- `GET /api/get_port_info` لبيانات port/type/IMEI/IMSI/ICCID/number/reg/slot/callstate/signal/gprs.
- `GET /api/set_port_info` لأفعال موثقة مثل module reset (GET مع query params، ليس POST).
- `POST /api/send_ussd` و`GET /api/query_ussd_reply`.
- `POST /api/get_cdr` لاسترجاع سجلات المكالمات (POST مع JSON body، ليس GET).
- CDR محلي حتى 10000 سجل حسب الإعداد/firmware.
- backup/restore، firmware upgrade، capture وlogs عبر واجهة الجهاز.

### المصادقة (Authentication)

- **New API (firmware ≥1102)**: HTTP **Digest** auth — يستخدم `WWW-Authenticate: Digest` challenge.
- **Old API**: HTTP **Basic** auth.
- كود يونس يستخدم `DispatchingAuthenticator` يدعم كلا النظامين تلقائياً مع caching.
- بيانات الدخول الافتراضية: `admin:admin` (نفس واجهة الويب).
- يجب تفعيل "New Version API" من: Mobile Configuration → Basic Configuration.

لا توجد واجهة firmware-independent موثقة في المصادر المستخدمة لتغيير كل SIP/network/firewall/firmware أو إجراء full reboot من يونس. لذلك لا نخترع endpoints.

## المعمارية القانونية

```text
Android يونس
  → Backend: approved user + PSTN permission + Yemen validation + daily limit
  → Asterisk AMI Originate + restricted Local dialplan
  → PJSIP trunk
  → DINSTAR UC2000-VE-8G
  → SIM / الشبكة اليمنية (GSM)
```

المكالمات لا تستخدم `/api/dial` على DINSTAR. HTTP API مخصص للمراقبة وعمليات SMS/USSD/port الموثقة؛ الصوت يمر عبر SIP/Asterisk.

## تقسيم الشبكة المقترح

- شبكة إدارة منفصلة/VLAN، مثال `192.168.11.0/24`.
- DINSTAR ثابت `192.168.11.1` مؤقتًا أو عنوان محجوز موثق.
- backend/Asterisk فقط لهما وصول إلى Web/API/SIP/AMI بحسب الحاجة.
- Android والمتصفح لا يصلان مباشرة إلى DINSTAR.
- لا port-forward من الإنترنت.
- الإدارة البعيدة عبر WireGuard إلى خادم يونس، لا إلى الجهاز.
- تعطيل Telnet بعد الإعداد إن لم يلزم؛ استخدام HTTPS/TLS مع trust للشهادة الموقعة ذاتياً.

## خطوات أول تشغيل آمن

1. افصل الجهاز عن الإنترنت العام وضع SIM اختبار محدودة الرصيد.
2. صِل جهاز إدارة فقط على الشبكة المحلية واضبط IP مناسبًا.
3. ادخل ببيانات المصنع ثم غيّر كلمة المرور فورًا إلى قيمة طويلة فريدة؛ لا تستخدمها في المحادثة أو Git.
4. حدّث firmware فقط من ملف رسمي مطابق للـ exact hardware/region وبعد backup واختبار rollback يدوي.
5. أنشئ مستخدم API محدود إن سمح firmware؛ وإلا خزّن admin في `.env` محمي إلى حين دعم حساب محدود.
6. فعّل "New Version API" من Mobile Configuration → Basic Configuration.
7. اضبط الوقت/NTP والمنطقة، SIP trunk إلى Asterisk، codecs المسموحة، inbound/outbound routes، whitelist وcall limits.
8. اختبر منفذًا وشريحة واحدة قبل تفعيل البقية.
9. لا تفعل factory reset أو firmware من لوحة يونس في Alpha.

## ما تعرضه لوحة يونس

- اكتشاف موثق عبر `get_port_info`، لا مجرد ping.
- 8 ports: radio type، registration، call state، signal 0–31/percent، GPRS، أرقام/IMSI/ICCID masked.
- module reset لمنفذ بعد تأكيد.
- USSD مع عدم تسجيل النص الحساس في audit.
- CDR عند دعم endpoint في firmware.
- capability matrix توضح العمليات الموثقة والمعطلة.
- inventory/snapshots/operations في PostgreSQL V12، دون تخزين password.

## ما يزال يحتاج اختبار الجهاز

- نوع firmware وإصداره وHTTPS certificate/auth scheme (Digest هو المتوقع مع New API).
- bands الخاصة بنسخة G ومدى توافقها مع Yemen Mobile/Sabafon/YOU (GSM 900/1800 MHz).
- SIP registration وoutbound/inbound calls لكل SIM.
- busy/no-answer/cancel/answer/hangup وAMI event correlation.
- codec/DTMF/echo/voice quality وASR/ACD/PDD.
- SMS/USSD/CDR response schemas الفعلية.
- balance commands الخاصة بكل مشغل.
- SIM PIN/blocked/no-SIM/weak-signal scenarios.
- power loss، reboot، backup/restore drill، وtoll-fraud tests.
- **تأكيد توافق firmware 04240302 مع HWID 7036-cf4b-3125 على جهاز 8G**.

لا تُعد أي نتيجة من هذه ناجحة قبل سجل حقيقي من العتاد.
