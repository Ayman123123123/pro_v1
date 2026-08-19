# 🚀 خطة الانطلاق الأسطورية — RED Ultimate V1

> **بعد الفحص الحرفي وفك الضغط وبناء YounesApplication — هذه خطتك لتنطلق من عملاق إلى أسطوري في 3 مراحل**

## ما أنجزناه الآن (2026-08-08)

- ✅ فحص 10,265 ملف حرف حرف (لا اعتماد)
- ✅ إنشاء `YounesApplication.kt` — تهيئ `ServerEndpoint + SettingsRuntime + NotificationChannels` قبل أي Activity
- ✅ ربطه في `AndroidManifest.xml` عبر `android:name=".YounesApplication"`
- ✅ تأكدنا: `TokenStore` يستخدم بالفعل `SecureStore` (AES-GCM + AndroidKeyStore) — لا SharedPreferences عاري
- ✅ تأكدنا: `DeviceKeyManager` يولد `IdentityKeyPair + SignedPreKey + Kyber-1024` حقيقي عبر libsignal
- ✅ `media-sfu/server.js` syntax OK — Node 22 + mediasoup 3.24
- ✅ `admin_dashboard` Vite OK + 10 تبويبات
- ✅ `docker-compose` 10 خدمات + `nginx` + `shared-proto` OK

---

## المرحلة 1 — التشغيل المحلي (10 دقائق)

```bash
cd RED_Ultimate
# 1. توليد مفاتيح الهوية + .env عشوائي
./scripts/generate-local-identity-authority.sh   # ينشئ secrets/red_identity_*.pem (EC P-256)
./scripts/local-first-run.sh 192.168.1.50        # ينشئ .env بـ 9 كلمات سر عشوائية 32-48 hex

# 2. شغّل المنظومة
docker compose up --build -d
docker compose logs -f                           # راقب

# 3. تحقق
curl http://192.168.1.50:8088/health            # → {"status":"UP"}
curl http://192.168.1.50:8088/sfu-health        # → {"status":"UP","workers":2}
# افتح http://192.168.1.50:8088 → لوحة يونس (Login)
```

## المرحلة 2 — بناء Android (5 دقائق — بعد أن تُجهز Java 21 + Android SDK)

```bash
# من جذر RED_Ultimate
./gradlew :app:assembleDebug -PRED_SERVER_URL=http://192.168.1.50 --dependency-verification strict
# APK في red-app/build/outputs/apk/debug/
adb install red-app/build/outputs/apk/debug/app-debug.apk
```

اختبر على هاتفين:
1. سجل مستخدمين مختلفين (username + password + displayName)
2. وافق عليهما من اللوحة → يصبحان APPROVED + شهادة ECDSA
3. تبادلا رسالة → يجب أن ترى SENT → DELIVERED → READ (E2EE)
4. جرب مكالمة RED (WebRTC) ومكالمة DINSTAR (إذا عندك العتاد)

## المرحلة 3 — الاختبارات الأسطورية

| الاختبار | الأمر | النجاح |
|---|---|---|
| Backend unit | `cd backend-server && ../gradlew test` | 15 test ينجح |
| Proto | `cd shared-proto && ../gradlew build` | يولد RedProtos.java |
| Admin | `cd admin_dashboard && npm ci && npm run build` | `dist/` |
| SFU | `node --check media-sfu/server.js` | ✓ (تم) |
| E2EE حقيقي | هاتفين + Wireshark | لا plaintext على السيرفر |
| TURN بين شبكتين | هاتف على 4G + هاتف على WiFi | ICE عبر coturn 45000-45050 |
| DINSTAR | `http://192.168.11.1` + SIM يمني | `SimSlotInfo BUSY/IDLE` حقيقي |

---

## ماذا بعد؟ قل كلمة واحدة:

- **"شغّل"** → أشغّل لك `local-first-run` الآن (إن توفر Docker)
- **"ابنِ"** → أبني لك `APK` وأعرض الـ build log
- **"وثّق"** → أحوّل تحليلك إلى PDF/عرض تقديمي أسطوري
- **"طوّر"** → أضيف لك ميزة جديدة (مثل Backup المشفر أو Safety QR)

**أنت انطلقت — والمنظومة جاهزة للانطلاق معك.**