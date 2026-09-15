# ملف إنقاذ: `arena/01a0a0cf-pro-v1` (2026-09-16)

> **لماذا هذا المجلد؟** الفرع `arena/01a0a0cf-pro-v1` غير مدموج في `main`، وPR الخاص به (#55) أُغلق بدون دمج. يوجد فيه عمل فريد لا يوجد في main. نُقل هنا لضمان عدم ضياعه حتى لو حُذف الفرع لاحقًا.

## بيانات المصدر (Provenance)

| البند | القيمة |
|---|---|
| الفرع | `arena/01a0a0cf-pro-v1` |
| آخر كميت | `927cb2d50e4fc65821ec4a4f21580610f2cb5dad` (2026-09-14 18:30Z) |
| PR | #55 — مُغلق بلا دمج (2026-09-14 22:17) |
| أساس الدمج (merge-base) | `32ca3141` — «phase-2 reliability» (2026-09-14 16:22) |
| كميتات فريدة | 34 كميتًا، منها 32 بلا نظير ببصمة الفرق في `main` |
| سلف الإنقاذ | `main` = `f8dc4ed1` (2026-09-15 20:40) |

**كيف أُنقذ؟** بدمج ثلاثي حقيقي لـ `main` + الفرع مع حسم **كل** التعارضات (37 ملفًا) لصالح `main`. المحصلة الصافية كانت **45 ملفًا جديدًا** فقط (وهي المضمّنة هنا)، دون أي حذف لأي ملف من `main`.
تفصيل كامل: `BRANCHES_AUDIT_2026-09-16_AR.md`.

---

## 1) ما تم إنقاذه إلى مساراته الطبيعية (13 وثيقة)

| # | المسار |
|---|---|
| 1 | `BETTER_THAN_ALL_GIANTS_2026.md` |
| 2 | `DATABASE_V8_ULTIMATE_REPORT.md` |
| 3 | `FINAL_FIX_REPORT_AR.md` |
| 4 | `FINAL_UNIFIED_REPORT_AR.md` |
| 5 | `LEGENDARY_FINAL_REPORT.md` |
| 6 | `LEGENDARY_V3_COMPLETE.md` |
| 7 | `تقرير_المجموعات_والمكالمات_مقابل_واتساب_2026-09-14_AR.md` |
| 8 | `تقرير_توحيد_وحذف_الفروع_2026-09-14_AR.md` |
| 9 | `docs/RED_ENGINEERING_ROADMAP_AR.md` — ⭐ الخطة الهندسية المُنتزعة من PR #50 قبل حذف فرعه |
| 10 | `RED_Ultimate_V1-main/RED_Ultimate/STATUS_FINAL_UNIFIED.md` |
| 11 | `RED_Ultimate_V1-main/RED_Ultimate/docs/ARCHITECTURE_V2_UNIFIED_AR.md` |
| 12 | `RED_Ultimate_V1-main/RED_Ultimate/docs/CALLS_ARCHITECTURE_AR.md` |
| 13 | `RED_Ultimate_V1-main/RED_Ultimate/docs/MODERN_TECH_STACK_2026.md` |

## 2) ما تم أرشفته هنا (32 وحدة Kotlin — **غير مُدمجة في البناء**)

هذه الملفات **لا تُترجم ولا تُبنى** في هذا المكان (خارج مسارات `src`)، فهي محفوظة للرجوع فقط. حجمها الكلي 463 KB.

```
└─ 01a0a0cf-features/
   └─ RED_Ultimate_V1-main/RED_Ultimate/
      ├─ backend-server/src/main/kotlin/com/red/server/
      │  ├─ api/NetworkDiscoveryController.kt          (+136)
      │  ├─ api/UnifiedCallsControllerV2.kt            (+304)
      │  ├─ api/UnifiedGroupsControllerV2.kt           (+265)
      │  ├─ calls/ModernCallsController.kt             (+315)
      │  └─ calls/UnifiedCallDeliveryService.kt
      └─ red-app/src/main/java/com/red/sovereign/
         ├─ calls/{ModernCallScreens,PrivateCallScreen,ImprovedConferenceScreen,
         │         ImprovedGroupCallScreen,ImprovedLiveStreamScreen,
         │         ImprovedSpacesScreen,UnifiedCallOrchestrator}.kt
         ├─ core/{LegendaryFixes,MessageSender,ModernChatSystem,UnifiedApiClient,
         │         UnifiedNetworkManager,SovereignUltimateSystemV3,
         │         SovereignBetterThanAllV4,SovereignSecurityV4,UltimateImprovementsV3}.kt
         ├─ core/database/{UltimateEntitiesV8,UltimateDao,UltimateLocalRepositoryV8}.kt
         ├─ features/{ModernFeaturesV3,SovereignStoriesAndNotes,SovereignPollsAndAI}.kt
         ├─ groups/ModernGroupSystem.kt
         ├─ settings/ModernNetworkSettingsScreen.kt
         ├─ ui/{ModernRedDashboard,ModernAdaptiveSystem}.kt
         └─ ui/components/ModernChatComponents.kt
```

## 3) نتيجة التحليل الساكن (فحص الاستيرادات مقابل `main`)

- **28 من 32** ملفًا: كل استيراداتها المشروعية محلولة في `main` ⇒ ترشيح عالٍ للترجمة.
- **4 ملفات** بها استيرادات غير محلولة (تحتاج نقل رموز إضافية):

| الملف | الرموز الناقصة | مكان تعريفها في الفرع |
|---|---|---|
| `core/MessageSender.kt` | `DecryptedMessage`, `LocalHistoryEntity` | `crypto/DecryptedMessageBus.kt`، `core/database/Entities.kt` |
| `core/UnifiedApiClient.kt` | `ApiResult` | `auth/AuthApi.kt` |
| `settings/ModernNetworkSettingsScreen.kt` | `DiscoveredServer`, `NetworkState` | `settings/SmartServerSettingsScreen.kt`، `features/calls/service/NetworkMonitor.kt` |
| `ui/ModernRedDashboard.kt` | `AuthState` | `auth/AuthViewModel.kt` |

> ⚠️ هذا فحص ساكن فقط (لا يعوّض الترجمة). لم تُشغَّل أي عملية بناء على هذه الملفات.

## 4) لماذا لم تُدمج مباشرة؟

1. **ليست موصولة بأي شيء:** عدد الإشارات إلى أيٍّ من هذه الأصناف الـ32 داخل `main` = **صفر**. ستكون كودًا مترجمًا غير مستخدَم ما لم تُوصَل بشاشات/مسارات.
2. **نقاط الوصل تقع في ملفات متعارضة:** توصيلها يحتاج تعديل `MainActivity.kt`, `RedDashboard.kt`, `AndroidManifest.xml`, `settings.gradle.kts` — وكلها ضمن الـ37 ملفًا المتعارضًا مع main الحالي، والنسخة الأحدث فيها هي نسخة main.
3. **جزء من قيمة الفرع صار في main بأحدث نسخة:** اللوبي/غرفة الانتظار (`ConferenceWebSocketHandler.kt` = 666 سطرًا و14 إشارة `LOBBY` في main مقابل 559 سطرًا في الفرع)، وروابط المكالمات (`RoomAliasService.kt`, `RoomSeparationPolicy.kt`)، وترحيلات Flyway (main = 60 ملفًا حتى V55، الفرع = 57 حتى V51).
4. **حماية CI:** أي إضافة إلى مجلدات `src` تمر عبر بوابة CI (`RED Ultimate CI` — 6 مهام). الإضافة الجماعية قد تُحمرّها، والقاعدة المعتمدة هنا: لا كسر لـ main.

## 5) خطة الدمج مستقبلًا (وحدة واحدة كل مرة)

1. انتقل إلى الفرع المصدر: `git checkout -b salvage/<module> origin/arena/01a0a0cf-pro-v1`.
2. انسخ **ملفًا واحدًا** من مجلد الأرشيف إلى مساره داخل `src`.
3. أصلح الاستيرادات الناقصة (الجدول في §3) من الفرع المصدر.
4. شغّل CI على الفرع وشاهد المهمة المعنية (backend / android).
5. أوصِل الوحدة فعليًا (شاشة/مسار/DI) — وإلا فابقاؤها غير موصولة لا قيمة له.
6. افتح PR صغيرًا لكل وحدة. الأسهل أولًا: `NetworkDiscoveryController.kt`, `UnifiedGroupsControllerV2.kt`, `ModernCallScreens.kt`.

## 6) حالة الفرع المصدر الآن

الفرع **لم يُحذف** وهو المرجع الحيّ الكامل (11,547 ملفًا تحت `RED_Ultimate_V1-main/`). إن أُريد التخلّص منه مستقبلًا، فيكفي أن يبقى موجودًا:
`927cb2d50e4fc65821ec4a4f21580610f2cb5dad`
