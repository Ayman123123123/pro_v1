# YOUNES LAN Announcer (اعلان السيرفر على الشبكة المحلية)

يجعل الهواتف تجد السيرفر تلقائياً حتى بدون إنترنت وبعد أي تغيير شبكة/IP.

## التشغيل اليدوي
```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-announcer.ps1
```

## التشغيل التلقائي عند إقلاع Windows (نفّذه مرة واحدة كمسؤول)
```powershell
schtasks /Create /TN "YOUNES-Announcer" /TR "powershell -ExecutionPolicy Bypass -WindowStyle Hidden -File D:\pro_new\pro_new\RED_Ultimate_V1-main\RED_Ultimate\scripts\start-announcer.ps1" /SC ONLOGON /RL HIGHEST /F
```

## قاعدة الجدار الناري المطلوبة (كمسؤول، مرة واحدة)
```powershell
netsh advfirewall firewall add rule name="RED Discovery UDP" dir=in action=allow protocol=UDP localport=8095
```

## كيف يكتشفه الهاتف؟
- طبقة 1 (mDNS): يعلن خدمة `_younes._tcp` على 8088 — التطبيق يجدها في وضع الفحص الشامل (THOROUGH).
- طبقة 2 (UDP 8095): يرد على بث اكتشاف التطبيق بعنوانه الحالي دائماً.
- يتطلب مكتبة zeroconf لمرة واحدة (يثبتها السكربت تلقائياً عند أول تشغيل مع إنترنت)؛ بدونها تعمل طبقة UDP فقط.

## اختبار سريع
1. شغّل السكربت على الكمبيوتر.
2. من هاتف على نفس الشبكة: افتح التطبيق → شاشة السيرفر الذكي → "فحص شامل".