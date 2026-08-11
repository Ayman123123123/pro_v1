# 05 — تكامل DINSTAR UC2000-VE-8T مع يونس

## الجهاز الفعلي

`UC2000-VE-8T` بوابة 8 قنوات GSM/3G/4G/LTE/VoLTE (حسب نسخة المودم الإقليمية)، وليست FXS/FXO تقليدية. تحتوي 8 SIM hot-swappable و8 موصلات SMA وواجهتي Ethernet وRS232 USB-B. وظيفتها تحويل SIP/RTP إلى الشبكة الخلوية والعكس.

المراجع الرسمية: صفحة [UC2000-VE](https://www.dinstar.com/GSM-3G-LTE-voip-gateway/4-8-ports/)، [datasheet](https://www.dinstar.com/WEB/files/15277/2018-09-06/UC2000-VE%20GSM&LTE%20VoIP%20Gateway%20Datasheet.pdf)، [manual](https://www.dinstar.com/WEB/files/15278/2018-09-06/UC2000-VE&VF&VG_GSM%20_LTE_VoIP_Gateway_User_Manual.pdf)، و[HTTP API](https://www.dinstar.com/WEB/files/13151/2018-06-05/Dinstar%20GSM%20Gateway%20HTTP%20API-v202011.pdf).

## ما تدعمه الوثائق

- SIP v2 UDP/TCP/TLS وRTP/SRTP وفق firmware/license.
- mobile↔VoIP، trunk/group/routing/manipulation.
- G.711A/U وG.723.1 وG.729A/B، وخصائص أخرى حسب firmware.
- SMS/USSD/PIN وHTTP JSON API/SMPP.
- `GET /api/get_port_info` لبيانات port/type/IMEI/IMSI/ICCID/number/reg/slot/callstate/signal/gprs.
- `POST /api/set_port_info` لأفعال موثقة مثل module reset.
- `POST /api/send_ussd` و`GET /api/query_ussd_reply`.
- CDR محلي حتى 10000 سجل حسب الإعداد/firmware.
- backup/restore، firmware upgrade، capture وlogs عبر واجهة الجهاز.

لا توجد واجهة firmware-independent موثقة في المصادر المستخدمة لتغيير كل SIP/network/firewall/firmware أو إجراء full reboot من يونس. لذلك لا نخترع endpoints.

## المعمارية القانونية

```text
Android يونس
  → Backend: approved user + PSTN permission + Yemen validation + daily limit
  → Asterisk AMI Originate + restricted Local dialplan
  → PJSIP trunk
  → DINSTAR UC2000-VE-8T
  → SIM / الشبكة اليمنية
```

المكالمات لا تستخدم `/api/dial` على DINSTAR. HTTP API مخصص للمراقبة وعمليات SMS/USSD/port الموثقة؛ الصوت يمر عبر SIP/Asterisk.

## تقسيم الشبكة المقترح

- شبكة إدارة منفصلة/VLAN، مثال `192.168.11.0/24`.
- DINSTAR ثابت `192.168.11.1` مؤقتًا أو عنوان محجوز موثق.
- backend/Asterisk فقط لهما وصول إلى Web/API/SIP/AMI بحسب الحاجة.
- Android والمتصفح لا يصلان مباشرة إلى DINSTAR.
- لا port-forward من الإنترنت.
- الإدارة البعيدة عبر WireGuard إلى خادم يونس، لا إلى الجهاز.
- تعطيل Telnet بعد الإعداد إن لم يلزم؛ استخدام HTTPS/TLS إن كان firmware يدعمه بصورة صحيحة.

## خطوات أول تشغيل آمن

1. افصل الجهاز عن الإنترنت العام وضع SIM اختبار محدودة الرصيد.
2. صِل جهاز إدارة فقط على الشبكة المحلية واضبط IP مناسبًا.
3. ادخل ببيانات المصنع ثم غيّر كلمة المرور فورًا إلى قيمة طويلة فريدة؛ لا تستخدمها في المحادثة أو Git.
4. حدّث firmware فقط من ملف رسمي مطابق للـ exact hardware/region وبعد backup واختبار rollback يدوي.
5. أنشئ مستخدم API محدود إن سمح firmware؛ وإلا خزّن admin في `.env` محمي إلى حين دعم حساب محدود.
6. اضبط الوقت/NTP والمنطقة، SIP trunk إلى Asterisk، codecs المسموحة، inbound/outbound routes، whitelist وcall limits.
7. اختبر منفذًا وشريحة واحدة قبل تفعيل البقية.
8. لا تفعل factory reset أو firmware من لوحة يونس في Alpha.

## ما تعرضه لوحة يونس

- اكتشاف موثق عبر `get_port_info`، لا مجرد ping.
- المنافذ (8 في ‑8G/‑8T و4 في المتغيّرات الرباعية): radio type، registration،
  call state، **الإشارة بالـ dBm** مع علامة صلاحية، GPRS، أرقام/IMSI/ICCID masked.
  الإشارة تُفسَّر حسب 3GPP TS 27.007 §8.5: القراءة **99 تعني «غير قابلة للكشف»**
  فتُعرض «لا يوجد قياس» ولا تُحوَّل إلى نسبة. تفصيل العطل السابق وإصلاحه في
  [`DINSTAR_UC2000_GUIDE_AR.md`](DINSTAR_UC2000_GUIDE_AR.md).
- module reset لمنفذ بعد تأكيد.
- USSD مع عدم تسجيل النص الحساس في audit.
- CDR عند دعم endpoint في firmware.
- capability matrix توضح العمليات الموثقة والمعطلة.
- **عدة أجهزة معًا**: سجل أسطول، تعرّف تلقائي بفحص نطاق الإدارة،
  توجيه يختار الجهاز والمنفذ ويستبعد الشرائح بلا إشارة صالحة.
- inventory/snapshots/operations في PostgreSQL V12، دون تخزين password.

## ما يزال يحتاج اختبار الجهاز

- نوع firmware وإصداره وHTTPS certificate/auth scheme (Basic/Digest).
- bands الخاصة بنسخة T ومدى توافقها مع Yemen Mobile/Sabafon/YOU.
- SIP registration وoutbound/inbound calls لكل SIM.
- busy/no-answer/cancel/answer/hangup وAMI event correlation.
- codec/DTMF/echo/voice quality وASR/ACD/PDD.
- SMS/USSD/CDR response schemas الفعلية.
- balance commands الخاصة بكل مشغل.
- SIM PIN/blocked/no-SIM/weak-signal scenarios.
- power loss، reboot، backup/restore drill، وtoll-fraud tests.

لا تُعد أي نتيجة من هذه ناجحة قبل سجل حقيقي من العتاد.
