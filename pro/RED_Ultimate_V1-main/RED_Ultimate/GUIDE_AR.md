# 🚀 دليل التشغيل النهائي — RED Ultimate / YOUNES Sovereign Platform

> **آخر تحديث:** 2026-08-09 | **الفرع:** `arena/019fe3bc-pro-v1` | **PR:** [#5](https://github.com/Ayman123123123/pro_v1/pull/5)

---

## ⚡ 1) التشغيل السريع (اختر طريقًا)

### الطريق A — على جهازك (Windows أو Linux)
```bash
# من مجلد المشروع:
RUN.bat        # Windows — تشغيل فوري
./run.sh       # Linux/macOS — تشغيل فوري
```

### الطريق B — تطوير سريع (لوحة + خادم وهمي)
```bash
cd RED_Ultimate
./run.sh                      # اختر 1
# أو يدويًا:
cd admin_dashboard && npm run mock &   # خادم وهمي Node.js
npm run dev                            # لوحة حية على 8088
```

### الطريق C — المنظومة الكاملة عبر Docker (الإنتاج المحلي)
```bash
cd RED_Ultimate
./scripts/local-first-run.sh 192.168.1.50   # Linux
# أو Windows:
.\scripts\local-first-run.ps1 -ServerIp 192.168.1.50
```

---

## 🔌 2) الوصول بعد التشغيل

| الخدمة | العنوان |
|---|---|
| 🖥️ لوحة الإدارة | `http://<IP>:8088/` |
| ❤️ صحة الخادم | `http://<IP>:8088/health` |
| 🎥 صحة SFU | `http://<IP>:8088/sfu-health` |
| 🔐 HTTPS (إن فُعّل) | `https://<IP>:8443` |

بيانات المسؤول في `RED_Ultimate/.env` (لا تُرفع إلى GitHub).

---

## 🧪 3) الفحص الآلي (كل شيء بأمر واحد)
```bash
cd RED_Ultimate
bash scripts/check-all.sh
# النتيجة: 10/10 فحوصات (الكيانات + عقد API + بناء + SFU + mock + YAML + سكربتات)
```

---

## ☁️ 4) CI على GitHub (يحتاج تفعيل Actions)

1. **فعّل GitHub Actions**: إعدادات المستودع ← Actions ← Enable
2. شغّل CI يدويًا:
```bash
gh workflow run "RED CI" --ref arena/019fe3bc-pro-v1
```
3. أو ادفع أي تغيير صغير → CI يبني تلقائيًا:
   - ✅ Backend: بناء + **24 اختبار JUnit**
   - ✅ اللوحة: عقد API (127 endpoint) + بناء TypeScript
   - ✅ فحوصات ثابتة: الكيانات + SFU + mock + STOPSHIP
   - ✅ Docker Compose: صحة الإعداد
   - ✅ Android: **APK** مع dependency verification صارم

---

## 📱 5) اختبار الهاتفين (بوابة الإطلاق)

1. ثبّت APK على جهازين (من CI → artifact أو من جهازك):
```bash
adb install -r red-app-debug.apk
```
2. افتح التطبيق → سجّل (بدون هاتف/بريد) → **انتظر موافقة المسؤول**
3. من اللوحة: وافق على الحساب والجهاز
4. سجّل الدخول → جرّب: رسائل مشفرة، مجموعات، حالات، قصص
5. جرّب مكالمة WebRTC بين الهاتفين

---

## 📞 6) DINSTAR (مسار الصوت الهاتفي)

```bash
# 1) شغّل الخادم
# 2) من اللوحة: DINSTAR Control ← اكتشف ← تحقق من الحالة
# 3) أو من سطر الأوامر:
curl -u admin:admin -k "https://192.168.11.1/api/get_port_info?port=0&info_type=slot,callstate,signal"
```

**ملاحظة**: مكالمات DINSTAR تستهلك رصيد SIM — اختبرها بعد ضبط الحدود من اللوحة
(صلاحيات الاتصال اليمني).

---

## 🗄️ 7) النسخ الاحتياطي

```bash
mkdir -p local-backup
cp RED_Ultimate/.env local-backup/red.env
cp -R RED_Ultimate/secrets local-backup/secrets

# PostgreSQL:
docker compose -f RED_Ultimate/docker-compose.yml --env-file RED_Ultimate/.env \
  exec -T db-postgres pg_dump -U admin -d red_sovereign -Fc > local-backup/postgres.dump

# MongoDB:
docker compose -f RED_Ultimate/docker-compose.yml --env-file RED_Ultimate/.env \
  exec -T db-mongo mongodump --archive > local-backup/mongo.archive
```

---

## 📚 8) المرجع السريع

| الملف | المحتوى |
|---|---|
| `API_REFERENCE.md` | 127 endpoint موثق |
| `docs/01-PROJECT-OVERVIEW.md` | المعمارية |
| `docs/02-DATABASES.md` | قواعد البيانات |
| `docs/05-DINSTAR-UC2000-VE-8G.md` | دليل DINSTAR |
| `SESSION_REPORT_AR.md` | سجل كل الجلسات |
| `README.md` | نظرة عامة |

---

## ⚠️ 9) الحالة والقيود

### ✅ مكتمل
- كل الجلسات (8 فروع) مدمجة + أفضل ما فيها
- DINSTAR كامل + أمان (WebSocket origins، SFU capability، remote wipe)
- 17 ترحيل DB + 24 اختبار + لوحة مطوّرة

### ⏳ يتطلب تجربة فعلية (حسب المبادئ)
- مكالمة WebRTC على **هاتفين حقيقيين**
- DINSTAR على **عتاد فعلي** (UC2000-VE-8G)
- بناء APK عبر CI (بعد تفعيل Actions)
