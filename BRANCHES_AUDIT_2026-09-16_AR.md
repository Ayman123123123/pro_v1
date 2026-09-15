# تدقيق شامل للفروع — pro_v1 (2026-09-16)

> مرجع التدقيق: `main` = `f8dc4ed1` (2026-09-15 20:40:30Z) — 548 كميت
> طريقة الفحص: **مستنسخ كامل** من GitHub (لأن النسخة المحلية shallow/grafted) + اختبارات دمج ثلاثي حقيقية + `git cherry` (بصمة الفروق) + GitHub API.
> النتيجة أدناه مبنية على دليل قابل لإعادة الإنتاج، لا على مقارنة أشجار.

---

## 1) حالة الـ remote بالكامل

| العنصر | القيمة |
|---|---|
| إجمالي المراجع | 63 (5 فروع + 58 `refs/pull/*`) |
| الفروع على الـ remote | `main`, `arena/01a0a0cf-pro-v1`, `arena/01a0a14b-pro-v1`, `arena/01a0a242-pro-v1`, `arena/01a0a2f4-pro-v1` |
| الوسوم (Tags) | 0 |
| الإصدارات (Releases) | 0 |
| PRs مفتوحة | 0 |
| Issues مفتوحة | 0 |
| Forks / Network | 0 / 0 |
| حماية main | غير محمي (`protected=false`) |
| آخر دفعة | 2026-09-15T20:40:30Z |
| حجم المستودع | 59,713 KB (‏`.git` = 62 MB) |
| CI آخر تشغيل على main | **نجاح** (2026-09-15 20:40) — كان هناك فشل 20:06 ثم أُصلح |
| Dependabot / Code scanning / Secret scanning | معطّل أو غير متاح للتوكن ⇒ لا يمكن الجزم |
| مرآة GitLab | غير موجودة/غير قابلة للوصول؛ الدليل `GITLAB_PUSH_GUIDE_AR.md` يفترض إنشاء مشروع جديد |

**فرع هذه الجلسة:** `arena/01a0a6ce-pro-v1` = مطابق تمامًا لـ `main` (نفس الكميت `f8dc4ed1` ونفس الشجرة حرفيًا). لا شيء فيه خارج main.

---

## 2) الحكم النهائي بحالة الفروع (بالدليل)

```
$ git branch -r --merged main        $ git branch -r --no-merged main
  origin/HEAD -> origin/main           origin/arena/01a0a0cf-pro-v1
  origin/arena/01a0a14b-pro-v1         origin/arena/01a0a2f4-pro-v1
  origin/arena/01a0a242-pro-v1
  origin/main
```

| الفرع | Tip | PR | مدموج في main؟ | الدليل | عمل فريد غير موجود في main |
|---|---|---|---|---|---|
| `arena/01a0a242-pro-v1` | `0aa6c0a3` | **#57** | ✅ **مدموج** 2026-09-15 20:40 | `--merged` + هو الأب الثاني لـ `f8dc4ed` | لا شيء — آمن للحذف |
| `arena/01a0a14b-pro-v1` | `bcf4fcba` | **#56** | ✅ **مدموج** 2026-09-14 23:24 | داخل `main` (ahead_by=0) | لا شيء — آمن للحذف |
| `arena/01a0a0cf-pro-v1` | `927cb2d5` | #55 مغلق بلا دمج | ❌ **غير مدموج** | `--no-merged` + `git cherry` = 32/34 كميت بلا نظير | **نعم** — 32 ملف Kotlin + 13 وثيقة (تفصيل §4) |
| `arena/01a0a2f4-pro-v1` | `a4b210c7` | لا يوجد PR | ❌ **غير مدموج** | `--no-merged` | نعم — 3 ملفات + حذف 27.7 MB نفايات (تفصيل §5) |

---

## 3) تصحيح مهم لتحليل سابق (شفافية)

في الفحص الأول قلت إن دمج `01a0a0cf` «يحذف 48 ملفًا من main منها `.github/workflows/red-ultimate-ci.yml` و`.gitlab-ci.yml` و`RoomAliasService.kt` وترحيلات V52/V53».

**هذا خطأ منهجي:** كنت أقارن *الشجرة بالشجرة*، وهذا يقيس «ما ينقص شجرة الفرع» لا «ما يحذفه الدمج».

**الصحيح بعد اختبار دمج ثلاثي حقيقي (بعد حسم التعارضات لصالح main):**
- الملفات التي حُذفت فعليًا من main = **3 فقط**:
  - `RED_Ultimate_V1-main/RED_Ultimate/red-app/src/main/java/com/red/sovereign/_archive/README.md`
  - `.../red-app/src/main/java/com/red/sovereign/agents/Red20Agents.kt`
  - `.../red-app/src/main/java/com/red/sovereign/agents/RedAgentCore.kt`
- وكلها **كود ميت**: عدد الإشارات إليها داخل main = **0** لكلٍّ منها.
- السبب: ملفات مثل `.gitlab-ci.yml` موجودة في main وغائبة عن شجرة الفرع، لكن الفرع **لم يعدّل** تاريخها المشترك فلم يطلب الدمج حذفها.

---

## 4) تفصيل `arena/01a0a0cf-pro-v1` (الأهم — عمل حقيقي غير مدموج)

**الأساس (merge-base):** `32ca3141` — «phase-2 reliability: durable 1:1 sends…» (2026-09-14 16:22)
**الكميتات:** 34 فريدًا، منها **32 بلا نظير ببصمة الفرق في main** (`git cherry`)
**دلتا الفرع الحقيقية:** 148 ملفًا (45 مضاف / 67 معدّل / 36 محذوف)

### أ) الدمج كما هو اليوم
```
$ git merge origin/arena/01a0a0cf-pro-v1       → فشل
37 ملفًا متعارضًا
```
نطاق التعارض واسع: نظام المكالمات بالكامل (`CallScreen`, `CallRuntime`, `CallOverlay`, `CallScreens`, `CallServiceIntegration`, `WebRtcSipClient`, `ConferenceSignalingClient`, `EmergencyCallManager`, `IncomingCallActivity`, `PhoneStateReceiver`…)، واجهة الخادم (`ConferenceController`, `ConferenceWebSocketHandler`, `SfuTicketController`, `AdminV2Controller`)، `red-app/src/main/AndroidManifest.xml`، `MainActivity.kt`، `RedDashboard.kt`، `settings.gradle.kts`.

### ب) المحصلة الصافية لو حُسمت التعارضات لصالح main
```
87 ملفًا: 45 مضاف / 39 معدّل / 3 محذوف   (+15,263 سطرًا، -817)
المضاف: 32 Kotlin + 11 md + 2 md عربي
```

**وحدة الميزات الفريدة المضافة (قيمة حقيقية):**
`UnifiedCallsControllerV2.kt`, `UnifiedGroupsControllerV2.kt`, `NetworkDiscoveryController.kt`, `ModernCallsController.kt`, `UnifiedCallDeliveryService.kt`, `UnifiedCallOrchestrator.kt`, `PrivateCallScreen.kt`, `Improved{Conference,GroupCall,LiveStream,Spaces}Screen.kt`, `ModernCallScreens.kt`, `ModernChatSystem.kt`, `MessageSender.kt`, `LegendaryFixes.kt`, `Sovereign{UltimateSystemV3,BetterThanAllV4,SecurityV4}.kt`, `UltimateImprovementsV3.kt`, `ModernFeaturesV3.kt`, `SovereignStoriesAndNotes.kt`, `SovereignPollsAndAI.kt`, `ModernGroupSystem.kt`, `UnifiedApiClient.kt`, `UnifiedNetworkManager.kt`, `UltimateEntitiesV8.kt` + `UltimateDao.kt` + `UltimateLocalRepositoryV8.kt`, `ModernRedDashboard.kt`, `ModernAdaptiveSystem.kt`, `ModernChatComponents.kt`, `ModernNetworkSettingsScreen.kt`.

**الوثائق الفريدة:** `docs/RED_ENGINEERING_ROADMAP_AR.md` (المُنتزَع من PR #50 قبل حذف فرعه)، `docs/CALLS_ARCHITECTURE_AR.md`, `docs/ARCHITECTURE_V2_UNIFIED_AR.md`, `docs/MODERN_TECH_STACK_2026.md`, تقارير V3/V4/V8، وتقرير تصفية الفروع.

### ج) ما هو **متجاوَز** (لا قيمة في دمجه)
| الميزة | main | الفرع | الحكم |
|---|---|---|---|
| اللوبي / غرفة الانتظار | `ConferenceWebSocketHandler.kt` = **666 سطرًا**، 14 إشارة `LOBBY` | 559 سطرًا، نسخة أقدم | نسخة main أحدث (وصلت عبر #57) |
| روابط المكالمات | موجودة: `RoomAliasService.kt`, `RoomSeparationPolicy.kt`, `SfuTicketController.kt` | نسخة أقدم | main أحدث |
| ترحيلات Flyway | **60 ملفًا حتى V55** | 57 ملفًا حتى V51 | الفرع متأخر |

### د) المخاطر
- 32 ملف Kotlin جديدًا لم تُبنَ/تُختبر قط فوق main الحالي (CI نجح على الفرع في 2026-09-15 02:46، لكن الأساس مختلف تمامًا عن main بعد #57).
- دمجها «إطلاقي» قد يكسر الترجمة/الاختبارات ⇒ أي إنقاذ يجب أن يمرّ ببناءٍ فعلي + CI قبل الدمج.

---

## 5) تفصيل `arena/01a0a2f4-pro-v1`

**الأساس:** `1757c35f` (متأخر عن main بـ 26 كميتًا) — **كميت واحد فريد**.

| العنصر | مضمون |
|---|---|
| اختبار دمج فعلي | 7 تعارضات في ملفات المكالمات (سببها قِدم الأساس، لا تعارض منطقي) — تُحل بـ `-X ours` |
| المحصلة الصافية | **+3 ملفات**: `GITLAB_PUSH_GUIDE_AR.md`, `scripts/push-to-gitlab.sh`, `scripts/push-to-gitlab.ps1` |
| الفائدة الحقيقية | حذف **41 ملف نفايات** متتبَّعة = **27.7 MB**، منها `RED_Ultimate_V1-main/RED_Ultimate/logcat.txt` = **25.0 MB** |
| ملاحظة | هذه الملفات مُدرجة في `.gitignore` ومع ذلك **لا تزال متتبَّعة في main** |
| إصلاح `.gitlab-ci.yml` | **متجاوَز**: main عدّل الملف بعده بكميت `8c1f5f38` (خط 6 مهام + إصلاح موديول `:app`) ⇒ لا حاجة لتغييره |

---

## 6) أرقام main الحالية (مراجع سريعة)

- الكميتات: **548** | المسارات: 4,656 | كائنات blob الفريدة: 4,385
- ترحيلات Flyway: **60** (أعلى V55)
- ملفات نفايات متتبَّعة: **41 ملفًا = 27.7 MB** (≈44% من حجم `.git` البالغ 62 MB)
- أكبر ملوّث: `RED_Ultimate_V1-main/RED_Ultimate/logcat.txt` = 25.0 MB، ثم `docs/diagnostics/working-tree-inventory.txt` (742 KB)، `build-logs/git-status-20260821.txt` (741 KB)، `build-logs/motorola-now.png` (675 KB)

---

## 7) التوصيات المرتّبة

1. **حذف الفروع المدموجة (آمن 100%):** `arena/01a0a242-pro-v1` و`arena/01a0a14b-pro-v1`. (المستودع لا يحذف الفروع تلقائيًا: `delete_branch_on_merge=false`.)
2. **`arena/01a0a2f4`:** لا يُدمج. الأفضل PR صغير = 3 ملفات GitLab + إزالة النفايات (27.7 MB) مع تحديث `.gitignore_local`.
3. **`arena/01a0a0cf`:** ثلاثة خيارات بترتيب الخطورة:
   - **(أ) تركيز على الوثائق فقط:** إنقاذ 13 وثيقة (صفر مخاطرة كسر).
   - **(ب) إنقاذ انتقائي للميزات:** نسخ الـ32 ملف Kotlin يدويًا + ترجيح main في التعارضات، مع بناء/CI إلزامي قبل الدمج.
   - **(ج) أرشفة الفرع** كما هو (وسم/تجميد) وعدم دمجه، لأن جزءًا كبيرًا من قيمته (اللوبي/الروابط) صار في main بأحدث نسخة.
4. **تنظيف تاريخ main (اختياري/خطر):** `git filter-repo` لإزالة `logcat.txt` من التاريخ ⇒ انخفاض كبير في حجم المستنسخ، لكنه **يعيد كتابة التاريخ** ويحتاج تنسيقًا لكل من لديه نسخة.
