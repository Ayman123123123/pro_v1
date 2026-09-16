# PROJECT_STRUCTURE_2026-09-16 — شجرة المجلدات الحية فقط

**تاريخ الإنشاء:** 2026-09-16  
**الجذر:** D:\pro_new\pro_new\RED_Ultimate_V1-main\RED_Ultimate  
**القاعدة:** المجلدات النشطة في `settings.gradle.kts` + البنية التحتية + التوثيق فقط  
**مستبعد:** lib/, core/, feature/, demo/, android/, app-android/, app/, build/, .gradle/, .kotlin/, node_modules/, gradle/, build-logs/, baseline-profile/, benchmark/, microbenchmark/, reproducible-builds/, wire-handler/, pstn-asterisk/, dinstar-config/, integration_patches/, fast-lint/, lintchecks/, artwork/, signing/, demo/ (قديمة)

---

```
RED_Ultimate/
├── red-app/                          # تطبيق Android الرئيسي (Compose، Kotlin)
│   ├── src/
│   │   └── main/
│   │       ├── java/com/red/sovereign/   # كود المصدر الرئيسي
│   │       │   ├── auth/                 # DeviceKeyManager، Signal crypto
│   │       │   ├── calls/                # CallOverlay، GroupCall، LiveStream، WebRTC
│   │       │   ├── crypto/               # SignalProtocolStore، SessionManager
│   │       │   ├── features/             # شاشات الميزات (admin، calls، chat، contacts، explore، devices، lan، media، privacy)
│   │       │   ├── groups/               # GroupCryptoManager
│   │       │   ├── lan/                  # LanSignalCrypto، LanPeers
│   │       │   ├── sovereign/            # Core sovereign logic
│   │       │   └── ui/                   # شاشات UI، Composables، Dashboard
│   │       ├── res/
│   │       │   ├── raw/                  # 6 ملفات صوتية (notification، redphone، webrtc، typing)
│   │       │   ├── values/               # strings، colors، themes
│   │       │   └── xml/                  # network_security_config، backup_rules
│   │       └── AndroidManifest.xml
│   ├── build.gradle.kts                # Android app config، dependencies، signing
│   ├── keystore.properties             # Debug signing (gitignored)
│   ├── proguard-rules.pro
│   └── README.md
│
├── backend-server/                     # Spring Boot 4.0.7 (Kotlin، Java 21)
│   ├── src/
│   │   └── main/
│   │       ├── kotlin/com/red/         # Controllers، Services، Repositories، Config
│   │       │   ├── auth/               # JWT، Device certificates، UnifiedPush
│   │       │   ├── calls/              # Call signaling، SFU tickets، WebSocket
│   │       │   ├── chat/               # Messages، Reactions، Polls، Groups
│   │       │   ├── contacts/           # Contacts، QR، Discovery
│   │       │   ├── crypto/             # Argon2id، Identity keys
│   │       │   ├── admin/              # Admin panel APIs
│   │       │   ├── media/              # MinIO، Uploads، CDN
│   │       │   ├── retention/          # Data retention policies
│   │       │   └── health/             # /health endpoint، Flyway status
│   │       └── resources/
│   │           ├── application.yml     # Spring config
│   │           └── db/migration/       # Flyway SQL migrations (V1..V56)
│   ├── build.gradle.kts                # Spring Boot، dependencies، bootJar
│   ├── settings.gradle.kts
│   ├── Dockerfile / Dockerfile.fast / Dockerfile.local
│   ├── gradlew / gradlew.bat
│   └── README.md
│
├── admin_dashboard/                    # React 18 + Vite + TypeScript
│   ├── src/
│   │   ├── components/                 # UI components (Tables، Forms، Charts)
│   │   ├── pages/                      # Dashboard، Users، Calls، Settings، Logs
│   │   ├── services/                   # API client (axios)، WebSocket
│   │   ├── store/                      # Zustand/Redux state
│   │   └── utils/                      # Helpers، formatters
│   ├── public/
│   ├── dist/                           # Build output (gitignored)
│   ├── node_modules/                   # (gitignored)
│   ├── package.json / package-lock.json
│   ├── vite.config.js
│   ├── tsconfig.json
│   ├── Dockerfile
│   ├── dashboard.nginx.conf
│   └── README.md
│
├── shared-proto/                       # Protobuf مشترك (Android + Backend)
│   ├── src/
│   │   └── main/proto/                 # .proto definitions
│   │       ├── calls.proto             # Call signaling، SFU tickets
│   │       ├── chat.proto              # Messages، Reactions، Polls
│   │       ├── contacts.proto          # Contacts، QR، Discovery
│   │       ├── common.proto            # Enums، Wrappers، Timestamps
│   │       └── admin.proto             # Admin panel DTOs
│   ├── build.gradle.kts                # protobuf-gradle-plugin
│   └── README.md
│
├── media-sfu/                          # MediaSoup SFU (Node.js 24، TypeScript)
│   ├── server.js                       # Main entry: WebSocket، MediaSoup workers، RTP transport
│   ├── protocol.js                     # Signaling protocol handlers
│   ├── protocol.test.js                # Unit tests
│   ├── package.json / package-lock.json
│   ├── Dockerfile
│   ├── install-worker.sh               # MediaSoup worker build script
│   └── README.md
│
├── infrastructure/                     # Infrastructure as Code
│   ├── observability/                  # Grafana dashboards، Prometheus rules
│   ├── init-certs.sh                   # TLS cert generation script
│   ├── setup-env.sh                    # Environment setup
│   ├── tls-init.Dockerfile             # Certs init container
│   └── README.md
│
├── monitoring/                         # Monitoring stack configs
│   ├── grafana/
│   │   └── dashboards/                 # JSON dashboards
│   ├── prometheus.yml                  # Prometheus scrape config
│   └── alertmanager.yml                # Alert routing
│
├── build-logic/                        # Gradle plugins مخصصة
│   ├── plugins/
│   │   ├── ktlint/                     # ktlint plugin
│   │   └── convention/                 # Android/Java/Kotlin conventions
│   ├── tools/
│   │   └── test/                       # Build logic tests
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   └── README.md
│
├── scripts/                            # 70+ سكريبتات أتمتة
│   ├── build-*.sh / build-*.ps1        # Build scripts (Android، Backend، APK)
│   ├── check-*.sh / check-*.py / check-*.ps1  # Validation scripts (integrity، contracts، schema، Kotlin structure، infrastructure، network security)
│   ├── fix-*.sh / fix-*.ps1 / fix-*.py        # Repair scripts (build cache، env encoding، certs، bytes، parens)
│   ├── deploy/*.sh                     # Deployment scripts
│   ├── backup-platform.sh / restore-platform.sh
│   ├── local-first-run.sh / local-first-run.ps1
│   ├── fetch-lfs-content.py / regenerate-lfs-pending.py / restore-lfs-pending.py
│   ├── system_health_check.py
│   ├── red-cli.sh
│   ├── server-announcer.py / advertise-mdns.py / start-announcer.ps1
│   ├── generate-ssl-certs.sh / verify-ssl-certs.sh / fix-red-proxy-certs.sh
│   ├── setup-production-ssl.sh
│   ├── docker-build-apk.sh
│   ├── lfs-oids.txt                    # LFS object IDs
│   └── README.md
│
├── docs/                               # توثيق شامل (33 markdown + archive/)
│   ├── archive/
│   │   ├── merge-2026-09-12/           # ملفات دمج قديمة
│   │   ├── superseded-2026-09-12/      # ملفات مستبدلة
│   │   └── verify-unique-2026-08-23/   # تحقق تفرد
│   ├── diagnostics/                    # تشخيصات DINSTAR/Live
│   ├── operations/                     # Runbooks تشغيلية
│   ├── recovered/                      # ملفات مستعادة
│   ├── 01-PROJECT-OVERVIEW.md          # نظرة عامة المشروع
│   ├── 02-DATABASES.md                 # قواعد البيانات
│   ├── 03-SERVER-ADMIN-PANEL.md        # لوحة الإدارة
│   ├── 04-APPS.md                      # التطبيقات
│   ├── 31-YOUNES-PRODUCT-UX-ROADMAP.md # خارطة منتج
│   ├── ANDROID_DEPENDENCY_PINS.md      # Pinning dependencies
│   ├── ARCHIVE_INVENTORY_2026-09-16.md # جرد الأرشيف + SHA256
│   ├── AUDIT_FIXES_VERIFIED_AR.md      # تدقيق الإصلاحات
│   ├── BACKUP_RESTORE_RUNBOOK_AR.md    # Runbook النسخ الاحتياطي
│   ├── CALLS_AR.md / CALLS_DEEP_AUDIT_AR.md
│   ├── CLEANUP_2026-09-16.md           # توثيق التنظيف
│   ├── DEEP_AUDIT_2_AR.md
│   ├── FEATURE_ACTIVATION_COMPLETION_AR.md
│   ├── IMPROVEMENT_LOG.md
│   ├── INDEX_2026-09-16.md             # فهرس الملفات
│   ├── NGINX_SSL_PROXY_GUIDE_AR.md
│   ├── OLD_MODULES_DECISION_2026-09-16.md # قرار إزالة modules قديمة
│   ├── POLLS_EVENTS_WIRED_AR.md
│   ├── REACTIONS_FEATURE_AR.md
│   ├── STICKERS_FEATURE_AR.md
│   ├── TROUBLESHOOTING_AR.md
│   ├── UNIFICATION_2026-08-19.md
│   ├── WINDOWS_GIT_DOCKER_2026-08-19.md
│   ├── FINAL_STATE_2026-09-16.md       # (هذا الملف جزء من المجموعة)
│   ├── PROJECT_STRUCTURE_2026-09-16.md # (هذا الملف)
│   └── LEGACY_REMOVAL_2026-09-16.md    # (الملف الثالث)
│
├── secrets/                            # مفاتيح الهوية (gitignored)
│   ├── fullchain.pem/                  # مجلد قديم (placeholder)
│   ├── privkey.pem/                    # مجلد قديم (placeholder)
│   ├── fullchain.pem                   # 1B placeholder
│   ├── privkey.pem                     # 1B placeholder
│   ├── red_identity_private_key.pem    # 241 B — Ed25519 private key
│   ├── red_identity_public_key.pem     # 178 B — Ed25519 public key
│   └── README_2026-09-16.md            # توثيق المفاتيح
│
├── prebuilt-backend/                   # Backend JAR مبني مسبقاً
│   ├── app.jar                         # 117 MB — red-backend.jar
│   └── Dockerfile
│
├── apk-output/                         # مخرجات APK build
│   └── build-output.log                # Build log فقط حالياً
│
├── local-maven/                        # مستودع Maven محلي (org.signal)
│   └── org/                            # فارغ — CI يملؤه عبر LFS
│
├── local-artifacts/                    # (فارغ حالياً — مخصص للـ artifacts المحلية)
│
├── lfs-pending/                        # ملفات LFS معلقة
│   └── README.md
│
├── backups/                            # نسخ احتياطية modules قديمة
│   ├── asterisk-dinstar-2026-08-23-pre-contact-acl-fix/
│   ├── asterisk-dinstar-2026-08-23-pre-image-build-fix/
│   ├── asterisk-dinstar-2026-08-23-pre-registration-acl/
│   ├── auto-number-learning-safety-2026-08-23/
│   ├── dinstar-per-port-routing-2026-08-23-pre-change/
│   ├── dinstar-source-defaults-2026-08-23/
│   ├── strongest-2026-08-14-111311/
│   ├── ContactService-before-last-seen-output-fix-2026-08-24.kt
│   ├── env-before-final-3-isolation-2026-08-23.env
│   ├── MainActivity-before-android-auth-build-fix-2026-08-24.kt
│   ├── red-app-build-before-server-url-fix-2026-08-24.gradle.kts
│   ├── ServerEndpoint-before-legacy-host-migration-2026-08-24.kt
│   └── UserAccount-before-last-seen-type-fix-2026-08-24.kt
│
├── .env                                # بيئة RED_Ultimate (17 KB، 43 مفتاح)
├── .env.example                        # قالب .env
├── .gitignore
├── .dockerignore
├── build.gradle.kts                    # Root build: tasks، verification، subprojects
├── settings.gradle.kts                 # يشمل :app + :shared-proto فقط
├── gradle.properties                   # JVM args، Kotlin، versions
├── gradlew / gradlew.bat
├── docker-compose.yml                  # 11 خدمة (backend، SFU، TURN، DBs، Redis، MinIO، Nginx، Admin، ntfy، certs)
├── docker-compose.dev.yml / .prod.yml / .staging.yml
├── nginx.conf / nginx-simple.conf
├── android-build.Dockerfile
├── CHANGELOG-ULTIMATE.md
├── CONTRIBUTING.md
├── DEPLOY.md
├── LICENSE
├── README.md / README_AR.md
├── ROADMAP_NEXT_STEPS.md
├── TODO.md
├── W0_MODULE_BOUNDARIES.md
└── [ملفات build logs متنوعة: *.log، *.txt]
```

---

## وصف سطر لكل مجلد نشط

| المجلد | الغرض | حالة البناء |
|--------|-------|-------------|
| `red-app/` | تطبيق Android الوحيد — Compose، Signal protocol، WebRTC، Room، WorkManager | **نشط** — `:app` في settings.gradle.kts |
| `backend-server/` | Spring Boot API — WebSocket، MongoDB، PostgreSQL، Redis، Flyway، MinIO | **مستقل** — `scripts/build-backend.sh` + Docker |
| `admin_dashboard/` | واجهة إدارة React — Users، Calls، Analytics، Settings | **نشط** — Docker build في compose |
| `shared-proto/` | Protobuf schema مشترك — calls، chat، contacts، admin | **نشط** — `:shared-proto` في settings.gradle.kts |
| `media-sfu/` | MediaSoup SFU — WebRTC media routing، SIMD workers | **نشط** — Docker service في compose |
| `infrastructure/` | TLS init، observability، certs management | **داعم** — يستخدمه compose |
| `monitoring/` | Prometheus/Grafana/Alertmanager configs | **داعم** — يستخدمه compose |
| `build-logic/` | Gradle plugins: ktlint، conventions، QA tools | **Composite** — مشروط بـ QA tasks |
| `scripts/` | أتمتة: build، check، fix، deploy، backup، LFS، SSL | **أدوات** — يدوي/CI |
| `docs/` | توثيق شامل + أرشيف مفهرس | **مرجعي** |
| `secrets/` | Identity keys (Ed25519) — gitignored | **Runtime** — volume في compose |
| `prebuilt-backend/` | JAR مبني مسبقاً للـ Docker COPY | **Artifact** |
| `apk-output/` | مخرجات APK (log حالياً) | **Output** |
| `local-maven/` | Maven repo محلي لـ libsignal-android | **Cache** — CI populated |
| `local-artifacts/` | مخصص للـ artifacts المحلية المستقبلية | **فارغ** |
| `lfs-pending/` | LFS staging area | **مؤقت** |
| `backups/` | نسخ احتياطية hand-picked من refactors | **أرشيف محلي** |

---

## ملاحظة هامة

**المجلدات التالية تم إزالتها/أرشفتها وليست في الشجرة الحية:**
- `lib/`、`core/`、`feature/`、`demo/` — مؤرشفة في `D:\pro_archive_2026-09-16\old-modules/` (انظر LEGACY_REMOVAL_2026-09-16.md)
- `android/`、`app-android/`、`app/` — مدمجة/محذوفة في Phase 11 (2026-08-19/2026-09-14)
- `build/`، `.gradle/`، `.kotlin/`، `node_modules/` — build outputs (gitignored)
- `gradle/`، `build-logs/`، `baseline-profile/`، `benchmark/`، `microbenchmark/` — build artifacts
- `reproducible-builds/`، `wire-handler/`، `pstn-asterisk/`، `dinstar-config/` — legacy/deprecated
- `integration_patches/`، `fast-lint/`، `lintchecks/`، `artwork/`، `signing/` — غير مستخدمة حالياً

---

*تم إنشاؤه في 2026-09-16 — يعكس الحالة الحية فقط كما في `settings.gradle.kts` و `docker-compose.yml`*