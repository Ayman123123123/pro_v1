# خطة إزالة وسيط Asterisk/AMI — مرحلية وقابلة للتحقق

**التاريخ:** 2026-09-05 · **المstatus:** لم تُنفَّذ بعد (خطة فقط — لا حذف جماعي)

## 1. لماذا الإزالة

الطوبولوجيا المعتمدة في هذا المشروع:

| المكوّن | العنوان | الدور |
|---|---|---|
| DINSTAR **UC200** IP PBX | `192.168.11.3:5060/udp` | خادم SIP الوحيد: تسجيلات، تمديدات داخلية، حتى 500 مستخدم / 30 مكالمة متزامنة |
| DINSTAR **UC2000** GSM gateway | `192.168.11.2:5060` | حاملو الشرائح الحقيقيون. الوصول إليه **حصريًا عبر trunk معرَّف على الصندوقين** |

بذلك تكون حاوية Asterisk + AMI طبقة زائدة: مسير مكالمات أطول، سرّ مشترك إضافي
(`WEBRTC_SIP_SECRET`)، وحساب SIP واحد مزيَّف — فالكود يردّ على كل المستخدمين
`sipUsername = "red-webrtc-client"` (نفس `sipSecret`) بينما التعليق في
`pstn-asterisk/docker-entrypoint.sh` يدّعي أن «كل مستخدم يحصل على حساب SIP فريد».
أي أن الوسيط لا يوفّر عزلًا ولا هوية، ويترك بابًا للرسوم على الشرائح.

**القاعدة الثابتة:** لا يتصل التطبيق ولا الخادم بـ UC2000 مباشرةً إطلاقًا؛ المسار
الخلوي شغل الـ trunk على الأجهزة.

## 2. حجم الارتباط المقاس (وليس انطباعًا)

| الطبقة | ما يذكر Asterisk/AMI |
|---|---|
| backend | 21 ملفًا في `src/main/kotlin` (منها `pstn/PstnBridgeController.kt`, `pstn/PstnCallService.kt`, `pstn/DinstarEventListener.kt`, `pstn/EnhancedPstnManager.kt`, `pstn/CallTimelineService.kt`, `config/SecurityConfig.kt`, `dinstar/NumberLearningService.kt`) |
| اختبارات backend | 5 ملفات (`PstnCallServiceTest`, `PstnCallProgressTrackerTest`, `PstnCallControllerTest`, `DinstarModelProfileTest`, `auth/AuthExceptionHandlerTest`) |
| Android | 10 ملفات (`calls/PstnWebRtcManager.kt`, `calls/WebRtcSipClient.kt`, `calls/PstnCallOverlay.kt`, `calls/CallScreen.kt`, `calls/CallType.kt`, `calls/PstnCallModels.kt`, `auth/AuthViewModel.kt`, `hardware/DinstarHardwareService.kt`, `features/dinstar/*`) |
| تبعية Gradle | `backend-server/build.gradle.kts:53` → `org.asteriskjava:asterisk-java:3.41.0` |
| بنية تحتية | خدمة `pstn-gateway` في `docker-compose.yml` + `docker-compose.pstn-only.yml` + مجلد `pstn-asterisk/` (Dockerfile، entrypoint، 7 ملفات conf) |
| nginx | `upstream … server pstn-gateway:8089 resolve` (سطر 59) و`location /ws/sip` (سطر 185) |
| قاعدة البيانات | عمود/مرجع في `V26_1__Dinstar_Enhanced.sql` و`V40__Cdr_Ingest_And_Scheduled_Sms_Alignment.sql` — **الهجرة المطبَّقة لا تُعدَّل** |
| أدوات | `admin_dashboard/scripts/check-asterisk-fleet.mjs`، `scripts/mock_backend.py`، لقطات `docs/diagnostics/**` و`build-logs/asterisk-*` |

**لماذا لم يُحذف الآن؟** بيئة العمل هنا بلا JDK/Gradle وبلا شبكة لميرالمفن (تعذّر
تنزيل أي toolchain). حذف ملفات Kotlin بلا `compileKotlin` = كسر بناء صامت. الخطة
مكتوبة لتُنفَّذ على جهاز فيه أداة البناء، دفعةً دفعة، وكل دفعة تُقاس بفحص.

## 3. المراحل

### المرحلة 0 — التجهيز (لا حذف)
1. ربط `calls/sip/DirectSipClient.kt` (المُسلَّم في هذا العمل: UDP/5060 + RFC 3261 +
   تحدي 401/407 + إعادة إرسال + تجديد تسجيل) بدل `WebRtcSipClient` عبر مفتاح:
   `red.pstn.transport=asterisk-wss|uc200-udp` (افتراضيًا `asterisk-wss` حتى يثبت البديل).
2. برهان القبول على UC200: تسجيل ناجح من جهاز فعلي + مكالمة صاعدة عبر الـ trunk +
   مكالمة داخلية بين تمديدَين + إعادة اتصال بعد انقطاع شبكة 30 ثانية.
3. تشغيل الاختبارات الوحدوية:
   `.\gradlew.bat :app:testDebugUnitTest --tests "com.red.sovereign.calls.sip.DirectSipClientTest" -PRED_SKIP_BUILD_LOGIC=true`

### المرحلة 1 — قطع مسار الاتصال عن Asterisk
* `PstnBridgeController` يرجّع إحداثيات UC200 (UDP host/port + المستخدم + السرّ القصير
  العمر) بدل `ASTERISK_WSS_URL`.
* إزالة ادّعاء «حساب لكل مستخدم»: إمّا توليد تمديد حقيقي على UC200 أو تصحيح النص والتوثيق.
* إبقاء أصناف AMI لكن خارج التوصيل (`@ConditionalOnProperty red.pstn.asterisk.enabled=false` كافتراضي).
* فحص: `.\gradlew.bat :app:compileDebugKotlin :backend-server:compileKotlin -PRED_SKIP_BUILD_LOGIC=true` ثم اختبارَي المكالمات.

### المرحلة 2 — البنية التحتية
* من `docker-compose.yml`: حذف خدمة `pstn-gateway` وما يليها (`build: ./pstn-asterisk`, منافذ 5060/10000-10100، `expose: [5038]`).
* من `nginx.conf`: حذف الـ upstream البوابة و`location /ws/sip`.
* حذف `docker-compose.pstn-only.yml` أو إعادة توجيهه إلى مسار UC200 المباشر.
* حذف `pstn-asterisk/` بالكامل (Dockerfile + entrypoint + confs).
* فحص: `python3 scripts/check-infrastructure.py` (حاليًا 98/98) ثم
  `docker compose config -q` و`docker compose up -d` مع تسجيل من هاتف.

### المرحلة 3 — الكود
* حذف التبعية `org.asteriskjava:asterisk-java:3.41.0` ثم أصناف AMI و`EnhancedPstnManager`
  و`InternalPstnController` (callback الـ dialplan يسقط بسقوط البوابة) مع `PSTN_INTERNAL_SECRET`
  من `application.yml`/`docker-compose.yml`/`.env.example`.
* حذف `red-app/.../calls/WebRtcSipClient.kt` (SIP-over-WSS لـ Asterisk) و`android/features/pstn/PstnSipEngine.kt`
  (يحمل `sipPass = "red-secret-token"` مكتوبًا في المصدر) بعد التأكد من عدم مرجعية.
* حذف `admin_dashboard/scripts/check-asterisk-fleet.mjs` وتحديث `quality-gate.yml` إن كان يستدعيه.
* نقل ما بقي صالحًا (ICE/TURN، سجل المكالمات، CDR) إلى مسارات UC200 دون تغيير عقود الواجهة.
* فحص: بناء كامل + `:backend-server:test` (425 اختبارًا حاليًا يجب أن تبقى خضراء).

### المرحلة 4 — قاعدة البيانات والتوثيق
* هجرة **أمامية** `V47__Drop_Asterisk_Residue.sql` تسقط الأعمدة الخاصة بالبوابة فقط
  (تُحدَّد من `V26_1`/`V40`) — لا تعديل على ملفات هجرة مطبَّقة.
* إعادة `spring.flyway.validate-on-migrate: true` ومعالجة فجوة `V35` المذكورة في التقرير الشامل.
* أرشفة `docs/diagnostics/live-snapshots-2026-08-23/ASTERISK_*` و`build-logs/asterisk-*` خارج شجرة البناء.
* تحديث `MASTER_CHECKLIST.txt` و`todo.md` (59 بندًا مفتوحًا يتعارض مع «NO FEATURES MISSING»).

## 4. أوامر التنفيذ (Windows، من `RED_Ultimate_V1-main\RED_Ultimate`)

```powershell
.\gradlew.bat :app:compileDebugKotlin -PRED_SKIP_BUILD_LOGIC=true --console=plain
.\gradlew.bat :backend-server:compileKotlin -PRED_SKIP_BUILD_LOGIC=true --console=plain
.\gradlew.bat :app:testDebugUnitTest :backend-server:test -PRED_SKIP_BUILD_LOGIC=true
python scripts/check-infrastructure.py
cd admin_dashboard; npm run check:api-contract; npx tsc --noEmit
```

كل مرحلة = commit مستقل قابل للتراجع: `git revert <sha>`. لا تُدمَج مرحلتان معًا.

## 5. ما لن يُفعل

* لا `git rm -r pstn-asterisk` قبل أن تخضر المرحلة 1 (البناء أولًا، الحذف ثانيًا).
* لا لمس لـ `192.168.11.2` / إعدادات UC2000 من التطبيق أو الخادم؛ التعريف الوحيد
  المشروع هو الـ trunk داخل الصندوقين.
* لا إعادة كتابة لهجرة مُطبَّقة (V26_1/V40) — فقط هجرة أمامية.
