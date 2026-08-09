# 🏛️ RED Ultimate V1 — YOUNES Sovereign Platform

منصة RED محلية أولًا للمراسلة الاجتماعية والمكالمات. المشروع القانوني داخل [`RED_Ultimate/`](RED_Ultimate/README.md).

> **حالة المشروع**: مدمج بالكامل من كل الجلسات السابقة — تطبيق Android + خادم + لوحة إدارة + SFU + PSTN.

---

## 🚀 التشغيل السريع

### الخيار 1: تشغيل كامل عبر Docker (الأسهل)
```bash
cd RED_Ultimate
./scripts/local-first-run.sh 192.168.1.50   # Linux
# أو على Windows:
# .\scripts\local-first-run.ps1 -ServerIp 192.168.1.50
```

### الخيار 2: تطوير سريع (mock backend + لوحة حية)
```bash
./run.sh    # اختر 1 → لوحة + خادم mock
```

### الخيار 3: واحد-كليك من جهازك
```bash
# من مجلد المشروع:
RUN.bat    # Windows
./run.sh   # Linux/macOS
```

---

## 🧩 المكونات

| المكوّن | المسار | التقنية |
|---|---|---|
| 📱 تطبيق Android | `RED_Ultimate/red-app/` (Gradle `:app`) | Kotlin + Compose + WebRTC + libsignal |
| ⚙️ الخادم | `RED_Ultimate/backend-server/` | Spring Boot 3.5 + Kotlin 2.4 |
| 🗄️ البروتوكول | `RED_Ultimate/shared-proto/` | Protobuf (Wire) |
| 🖥️ لوحة الإدارة | `RED_Ultimate/admin_dashboard/` | React 19 + TypeScript + Ant Design |
| 🎥 وسيط الوسائط | `RED_Ultimate/media-sfu/` | mediasoup (WebRTC SFU) |
| 📞 بوابة PSTN | `RED_Ultimate/pstn-asterisk/` | Asterisk + DINSTAR UC2000-VE-8G |
| 🐳 التشغيل | `RED_Ultimate/docker-compose.yml` | 10 خدمات مع healthchecks |

> `app/` و`android/` و`app-android/` مصادر تاريخية خارج البناء (أرشيف موثق).

---

## 📚 الوثائق

| الوثيقة | المحتوى |
|---|---|
| [نظرة المشروع](RED_Ultimate/docs/01-PROJECT-OVERVIEW.md) | المعمارية الكاملة |
| [قواعد البيانات](RED_Ultimate/docs/02-DATABASES.md) | PostgreSQL + MongoDB + Redis + Room |
| [السيرفر واللوحة](RED_Ultimate/docs/03-SERVER-ADMIN-PANEL.md) | تدفق البيانات |
| [التطبيقات](RED_Ultimate/docs/04-APPS.md) | Android والتاريخ |
| [تشغيل Alpha محليًا](RED_Ultimate/LOCAL_FIRST_RUN_AR.md) | دليل خطوة بخطوة |
| [مرجع API الكامل](RED_Ultimate/API_REFERENCE.md) | **127 endpoint موثق** |
| [تقرير الجلسات](SESSION_REPORT_AR.md) | سجل كل التطوير |

---

## 🔒 مبادئ غير قابلة للكسر

- ❌ لا هاتف/SIM/بريد/SMS/OTP للتسجيل
- ✅ الحساب والجهاز يحتاجان موافقة إدارية (سلطة يونس)
- ✅ RED voice/video عبر WebRTC وبـ RED ID دون SIM
- ✅ DINSTAR مسار صوت PSTN منفصل تتحكم به الإدارة
- ✅ مفاتيح libsignal الخاصة لا تغادر Android
- ⚠️ المحتوى الاجتماعي العام ليس E2EE

---

## ✅ التحقق والجودة

بوابة CI (GitHub Actions) تتحقق تلقائيًا من:
1. **Backend**: بناء + 23 اختبار JUnit
2. **لوحة الإدارة**: عقد API (35/35) + بناء TypeScript
3. **فحوصات ثابتة**: تطابق الكيانات مع DB + SFU + mock_backend + STOPSHIP
4. **Docker Compose**: صحة الإعداد
5. **Android**: بناء APK مع dependency verification صارم

---

## 📦 ما تم إنجازه (ملخص)

- ✅ **دمج كل فروع arena** — أفضل ما في 7 جلسات سابقة
- ✅ **لوحة إدارة مطوّرة وآمنة** (React 19 + مصادقة حقيقية)
- ✅ **DINSTAR كامل**: Digest auth + SMS + Call Forward + جرد SIM
- ✅ **nginx محصّن**: rate limiting + HTTPS + حماية actuator
- ✅ **16 ترحيل قاعدة بيانات** (V1→V16)
- ✅ **صفر TODO** | **صفر println** | **صفر استيرادات مكسورة**

---

*آخر تحديث: 2026-08-09 — الفرع `arena/019fe3bc-pro-v1`*
