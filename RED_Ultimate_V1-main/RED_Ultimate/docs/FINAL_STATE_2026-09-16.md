# FINAL_STATE_2026-09-16 — تقرير حالة نهائي شامل

**تاريخ الإنشاء:** 2026-09-16  
**مساحة العمل:** D:\pro_new\pro_new\RED_Ultimate_V1-main  
**الفرع النشط:** main

---

## 1. Git — commits، files، remotes، branches

### Remotes
| اسم | URL |
|------|-----|
| github | https://github.com/Ayman123123123/pro_v1.git (fetch/push) |

### Branches
| Branch | Type | Status |
|--------|------|--------|
| main | local | * الحالي |
| backup/local-2026-09-14 | local | محلي فقط |
| remotes/github/arena/01a0a0cf-pro-v1 | remote | arena branch |
| remotes/github/arena/01a0a14b-pro-v1 | remote | arena branch |
| remotes/github/arena/01a0a242-pro-v1 | remote | arena branch |
| remotes/github/arena/01a0a2f4-pro-v1 | remote | arena branch |
| remotes/github/main | remote | tracking |

### آخر 10 commits (git log --oneline -10)
```
153856b refactor(calls): use MaterialTheme colors in IncomingCallScreen
ef2d811 docs: archive checksums and index update
07da342 fix(calls,profile,live): offline delivery, history sync, livestream IDs, profile status
bc8cbaa chore(repo): untrack 50 junk files, tighten gitignore, document archive
67b3da3 fix(calls): fix offline call delivery and mailbox TTL
ceb3185 fix(core,ui,calls): fix okhttp pool flood, surface lifecycle, and composition limits
8d10e25 fix(calls,messages,live): P0 fixes — SFU viewer pipeline, messages visibility, bitrate mapping
7cd2083 chore: remove DINSTAR/Yemen, clean CI workflows
7526ef5 feat(channels,calls,battery): add ChannelsScreen UI, OEM battery wake-up guidance, and call reliability improvements
4b7313f chore: rebase fix dashboard merge
```

### حالة Git (git status)
- **Staged:** renamed ملف تقرير الفحص إلى project-meta/
- **Deleted (unstaged):** 50+ ملفات قديمة/مكررة تم إزالتها (انظر CLEANUP_2026-09-16.md)
- **Modified:** 30+ ملفات في red-app/src (تحسينات UI/messages/calls)
- **Untracked:** 
  - RED_Ultimate/docs/ARCHIVE_INVENTORY_2026-09-16.md
  - RED_Ultimate/docs/CLEANUP_2026-09-16.md
  - project-meta/ (ملفات الأرشفة المنقولة)

---

## 2. Workspace — الحجم، الملفات، المجلدات الحية

### الحجم الإجمالي (RED_Ultimate_V1-main)
- **إجمالي الملفات:** 26,288 ملف
- **الحجم الإجمالي:** ~711.6 MB (678 MB في RED_Ultimate/)

### المجلدات الحية في RED_Ultimate/ (المشاريع النشطة فقط)

| المسار | الوصف | الحجم التقريبي |
|--------|--------|---------------|
| red-app/ | تطبيق Android الرئيسي (Compose، Kotlin) | ~12 MB |
| backend-server/ | Spring Boot 4.0.7 (Kotlin، PostgreSQL، MongoDB، Redis) | ~5 MB |
| admin_dashboard/ | واجهة إدارة React/Vite/TypeScript | ~2 MB |
| shared-proto/ | Protobuf مشترك بين Android و Backend | ~1 MB |
| media-sfu/ | MediaSoup SFU (Node.js، WebRTC) | ~1 MB |
| infrastructure/ | TLS init، شهادات، observability | < 1 MB |
| monitoring/ | Prometheus، Grafana، Alertmanager configs | < 1 MB |
| build-logic/ | Gradle plugins مخصصة (ktlint، QA tools) | ~1 MB |
| scripts/ | 70+ سكريبتات build/check/deploy (bash/ps1/py) | ~2 MB |
| docs/ | توثيق شامل (33 ملف markdown + archive) | ~2 MB |
| secrets/ | مفاتيح identidad (placeholder، gitignored) | < 1 MB |
| prebuilt-backend/ | app.jar مبني مسبقاً (117 MB) | 117 MB |
| apk-output/ | مخرجات APK build (log فقط حالياً) | < 1 MB |
| local-maven/ | مستودع Maven محلي (org.signal) | < 1 MB |
| local-artifacts/ | (فارغ حالياً) | 0 B |
| lfs-pending/ | ملفات LFS معلقة (README فقط) | < 1 MB |
| backups/ | نسخ احتياطية old modules (7 zip + env backups) | ~7 MB |

---

## 3. Archive — المسار، المحتويات، الأحجام، SHA256

**مسار الأرشيف:** D:\pro_archive_2026-09-16

### ملخص الأرشيف
- **إجمالي الملفات:** 57 ملف
- **الحجم الإجمالي:** 6.39 GB
- **الهيكل:** 7 مجلدات فرعية + 3 ملفات zip كبيرة

### محتويات الأرشيف بالتفصيل

| المجلد/الملف | عدد الملفات | الحجم | الوصف |
|-------------|-------------|-------|--------|
| `apks/` | 10 | 2.75 GB | APKs خام (debug builds من تواريخ مختلفة) |
| `apks-stray/` | 3 | 818 MB | APKs مكررة/متبقية (SHA256 متطابق مع apks/) |
| `dead-backend-shadow/` | 0 | 0 B | مجلد فارغ (backend قديم تم إزالته) |
| `docs-duplicates/` | 1 | 5 KB | ملف markdown مكرر واحد |
| `env-backup/` | 4 | 22 KB | نسخ احتياطية .env الحالية (4 بيئات) |
| `env-history/` | 9 | 89 KB | تاريخ .env.before-* (9 نقاط زمنية) |
| `gradle/` | 1 | 699 KB | verification-metadata.xml.disabled |
| `nested-git-backup/` | 20 | 26 KB | ملفات .git احتياطية |
| `old-modules/` | 5 | 7.3 MB | **مزودة بـ SHA256SUMS.txt** — core/demo/feature/lib ك zip |
| `.git-temp.zip` | 1 | 2.47 GB | أرشيف .git مؤقت |
| `bundles.zip` | 1 | 148 MB | AAB bundles |
| `red-sha1.zip` | 1 | 191 MB | أرشيف red-sha1 |

### SHA256 للملفات الكبيرة (من ARCHIVE_SHA256SUMS.txt)
```
4523C18D40A17CE885DC63EC2B7998F83A8B5392D451B3F1DF45443FB4324535  .git-temp.zip
05C2C1161C3209076E9EDE966C0C386C79F9016DF2A6658BA8D66CA4E0354ED2  red-sha1.zip
D7066AE2942CCB056730D4E6CC94CB70ADBEADBA9E68FEE1B21A2476FB3D6792  bundles.zip
```

### old-modules SHA256 (من old-modules/SHA256SUMS.txt)
```
B5904B835DA85B301D7BAD1BD67D5499972F376C0DBED63D658CF4BEC00D434E  core.zip
08ABF36D2B659AFB095521A10799A9D62C8EDD87402D3D43BBC196683BB8B7EE  demo.zip
30E1E8C1DCE07DC84976F2D7BD79BD96E6B3E93521975041EBA8F3A50DF56167  feature.zip
6A4FD9DEC5169513799A22D1FBC58ED08C62DFA1A583B7DBC1CDE7E52D656B96  lib.zip
```

---

## 4. Env — حالة .env، المفاتيح، الأمان

### ملفات .env الحالية (4 ملفات، 68 مفتاح إجمالي)
| الملف | المسار | الحجم | عدد المفاتيح |
|--------|--------|-------|-------------|
| root | D:\pro_new\pro_new\.env | 1,385 B | 15 |
| RED_Ultimate_V1-main | D:\pro_new\pro_new\RED_Ultimate_V1-main\.env | 2,485 B | 9 |
| RED_Ultimate | D:\pro_new\pro_new\RED_Ultimate_V1-main\RED_Ultimate\.env | 18,326 B | 43 |
| backend-server | D:\pro_new\pro_new\RED_Ultimate_V1-main\RED_Ultimate\backend-server\.env | 79 B | 1 (فارغ فعلياً) |

### مفاتيح DINSTAR/PSTN/YEMEN (43 في RED_Ultimate/.env)
AMI_PASSWORD, DINSTAR_ENABLED, DINSTAR_USERNAME, DINSTAR_PASSWORD, DINSTAR_IP, DINSTAR_IPS, PSTN_EXTERNAL_IP, DINSTAR_DISCOVERY_ENABLED, DINSTAR_DISCOVERY_SUBNETS, DINSTAR_HEARTBEAT_MS, DINSTAR_CONNECT_TIMEOUT, DINSTAR_READ_TIMEOUT, DINSTAR_PROBE_TIMEOUT, DINSTAR_CERT_SHA256_PINS, DINSTAR_PORT, DINSTAR_SCHEME, PSTN_INTERNAL_SECRET, PSTN_INTERNAL_ALLOWED_IPS, DINSTAR_SIP_USER, DINSTAR_SIP_PASSWORD, DINSTAR_PORT_ACCOUNTS, DINSTAR_PORT_SIP_PORTS, DINSTAR_FROM_USER, DINSTAR_NIC_IP, SIP_OVER_TLS_ENABLED, DINSTAR_API_ENABLED, DINSTAR_SMS_ENABLED, DINSTAR_USSD_ENABLED, DINSTAR_BULK_SMS_ENABLED, DINSTAR_EMAIL_TO_SMS_ENABLED, DINSTAR_SMS_TO_EMAIL_ENABLED, PORT_7_ENABLED, PORT_7_PRIMARY_PHONE_NUMBER, PORT_7_SECONDARY_PHONE_NUMBER, PORT_7_SIM_CARD, PORT_7_DIAL_RULES_ENABLED, PORT_7_CALL_RECORDING_ENABLED, PORT_7_CALL_FORWARDING_ENABLED, PORT_7_AUTO_NUMBER_DETECTION, PORT_7_VERIFICATION_REQUIRED, YEMEN_MOBILE_NETWORK_OPTIMIZED, YEMEN_NETWORK_TIMEOUT, YEMEN_SMS_CHAR_LIMIT

### أسرار حرجة (موجودة في .env، **لا تكتبها في التوثيق**)
JWT_SECRET, DB_PASSWORD, REDIS_PASSWORD, TURN_SECRET, MINIO_PASSWORD, RED_ADMIN_PASSWORD, GRAFANA_ADMIN_PASSWORD

### مفاتيح غير موثقة/متبقية (للإزالة لاحقاً)
RED_DINSTAR_*, PORT_ROUTING_*, SABAFON/MTN, WEBRTC_SIP_SECRET, SIPJS/ASTERISK

### secrets/
- `fullchain.pem` — 1B placeholder
- `privkey.pem` — 1B placeholder
- `red_identity_private_key.pem` — 241 B
- `red_identity_public_key.pem` — 178 B
- مجلد `fullchain.pem/` و `privkey.pem/` (مجلدات قديمة)

**الحالة:** secrets/ في .gitignore، غير متتبع.

---

## 5. Build — Gradle، Docker، اختبارات

### Gradle (RED_Ultimate/)
- **Root build.gradle.kts:** يحدد plugins aliases، subprojects config، verification tasks (buildQa، androidCheck، backendCheck، qa، qualityGate، ci، format، checkStopship)
- **settings.gradle.kts:** يشمل `:app` (red-app) + `:shared-proto` فقط. **لا يشمل** backend-server (مستقل)، lib/core/feature/demo (مؤرشف). `build-logic` composite build مشروط بـ QA tasks.
- **red-app/build.gradle.kts:** Android تطبيق، minSdk 24، targetSdk 34، Compose BOM، libsignal-android 0.86.5 (من local-maven → Google mirror → repo1)، Room، WorkManager، Coil 3، Haze، Lottie، Vosk، emoji2، Paging 3، Tink 1.23.0، sqlcipher.
- **backend-server/build.gradle.kts:** Spring Boot 4.0.7، Kotlin 2.3.21، Java 21، PostgreSQL، MongoDB، Redis، Flyway، MinIO، JWT (jjwt 0.13.0)، bcprov 1.86، jsoup 1.22.2، shared-proto.

### Docker (docker-compose.yml — 10 خدمات)
| الخدمة | الصورة | المنافذ | الصحة |
|---------|--------|---------|-------|
| identity-init | alpine:3.21 | لا شيء | completed |
| backend | red-backend:local (Dockerfile) | 8080 (internal) | /health |
| media-sfu | red-media-sfu:local | 4000, 40000-40100/udp/tcp | /health |
| coturn | coturn/coturn:4.6.2-r10 | 3478, 5349, 443, 80, 45000-45050 | bash /dev/tcp |
| db-postgres | postgres:16.9-bookworm | 5432 | pg_isready |
| db-mongo | mongo:8.0.13-noble | 27017 | mongosh ping |
| cache-redis | redis:7.4.5-bookworm | 6379 | redis-cli ping |
| minio | minio/minio:RELEASE.2025-04-22 | 9000, 9001 (localhost فقط) | /minio/health/live |
| certs-init | red-tls-init:local | لا شيء | completed |
| nginx | nginx:1.28.0-alpine | 8088 (HTTP), 8443 (HTTPS) | /health |
| admin-panel | red-admin-ui:local | 3000 (internal) | / |
| ntfy | binwiederhier/ntfy:v2.28.0 | 2586 | /v1/health |

### اختبارات
- **Android:** `./gradlew testDebugUnitTest`، `lintDebug`، `assembleDebug` — معرفة في androidCheck/qa/ci
- **Backend:** `./gradlew test` في backend-server/ — معرفة في backendCheck/qualityGate
- **Build-logic:** `:tools:test`، `:tools:ktlintCheck`، `:plugins:ktlintCheck` — في buildQa
- **Fast-lint:** `:fast-lint:fastLint` — في ci

### Prebuilt artifacts
- `prebuilt-backend/app.jar` — 117 MB، مبني بتاريخ 2026-09-10
- `local-maven/org/signal/` — فارغ (CI يملؤه عبر LFS)

---

## 6. ما تم إنجازه في كل الجولات

### الجولة #1: التدقيق والفهم (2026-08-13 → 2026-08-19)
- COMPREHENSIVE_PROJECT_AUDIT_REPORT.md
- UNIFICATION_2026-08-19.md — دمج android/ + app-android/ → red-app/
- إزالة Signal fork app/ (~7000 ملف، 5 أصوات فقط مستخرجة)
- W0_MODULE_BOUNDARIES.md — حدود الوحدات

### الجولة #2: التطوير والإصلاحات (2026-08-19 → 2026-09-10)
- BUILD_FIX_REPORT_AR.md، BUILD_FIX_GUIDE_AR.md
- CALLS_SYSTEM_COMPLETE_AUDIT.md
- VOICE_MESSAGE_AUDIT_AR.md، VOICE_MESSAGE_ENHANCEMENT_REPORT_AR.md
- FEATURE_ACTIVATION_COMPLETION_AR.md
- إصلاحات calls: offline delivery، mailbox TTL، SFU viewer pipeline، bitrate mapping

### الجولة #3: الأرشفة والتنظيف (2026-09-11 → 2026-09-15)
- ARCHIVE_INVENTORY_2026-09-16.md — أرشيف 6.39 GB مع SHA256
- CLEANUP_2026-09-16.md — إزالة 50 ملف junk، توثيق env/secrets
- INDEX_2026-09-16.md — فهرس الملفات
- OLD_MODULES_DECISION_2026-09-16.md — قرار إزالة lib/core/feature/demo
- نقل 40+ ملف markdown قديم إلى project-meta/
- git rm --cached لـ 50 ملف (bc8cbaa)

### الجولة #4: توثيق نهائي (2026-09-16 — الحالية)
- إنشاء FINAL_STATE_2026-09-16.md (هذا الملف)
- إنشاء PROJECT_STRUCTURE_2026-09-16.md
- إنشاء LEGACY_REMOVAL_2026-09-16.md

---

## 7. ما بقي — قرارات بشرية مطلوبة

| # | العنصر | الوصف | أولوية |
|---|--------|--------|--------|
| 1 | **إزالة مفاتيح DINSTAR/YEMEN/PORT_7 غير المستخدمة** | 43 مفتاح في RED_Ultimate/.env — معظمها legacy من fork Signal. يحتاج مراجعة يدوية لتحديد ما يُحتفظ به. | عالية |
| 2 | **توقيع Release APK** | keystore.properties يحتاج مفاتيح خاصة حقيقية (RED_KEYSTORE_*). حالياً debug فقط. | عالية |
| 3 | **NTFY_BASE_URL للإنتاج** | docker-compose.yml يستخدم 127.0.0.1 fallback. يحتاج IP حقيقي للأجهزة الحقيقية. | متوسطة |
| 4 | **TURN_EXTERNAL_IP / MEDIASOUP_ANNOUNCED_IP** | مطلوبة لـ WebRTC في الإنتاج. حالياً placeholder. | متوسطة |
| 5 | **شهادات TLS للإنتاج** | certs-init يولد self-signed. يحتاج Let's Encrypt أو شهادات حقيقية للإنتاج. | متوسطة |
| 6 | **تشغيل CI/CD pipeline** | GitHub Actions workflows تحتاج تفعيل/مراجعة. آخر CI أخضر كان 6/6 في commit b70e600. | متوسطة |
| 7 | **التحقق من APK النهائي** | apk-output/ يحتوي log فقط. يحتاج build وإختبار على أجهزة حقيقية. | متوسطة |
| 8 | **مراجعة admin_dashboard** | React app، يحتاج build وإختبار التكامل مع backend APIs. | منخفضة |
| 9 | **تنظيف apks-stray/** | 818 MB مكررة في الأرشيف — قرار: حذف أم الاحتفاظ؟ | منخفضة |
| 10 | **قرار .git-temp.zip (2.47 GB)** | أرشيف .git ضخم — هل يُحتفظ أم يُحذف بعد التحقق من التاريخ؟ | منخفضة |

---

*تم إنشاؤه تلقائياً في 2026-09-16 — الوكيل #5 (توثيق شامل نهائي)*