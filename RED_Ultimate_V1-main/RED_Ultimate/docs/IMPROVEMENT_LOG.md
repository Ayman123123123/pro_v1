# سجل التحسينات

## 2026-08-09 — دفع بدون توقف
- `ae41d58d` RichMessage mentions/hashtags test
- `0dd13dbb` storage orphan listAllKeys + deleteOrphans
- `0dd13dbb` OrphanCleanupScheduler 03:00 Asia/Aden
- `5ecf6cdf` FTS5 search + `d0859813` ephemeral
- `a1b42e9e` Groups E2EE + `c536756` SFU ticket
- `ad4963c7` LFS fix + `ae0bd85c` 9 JPG + `8dfa3ade` HttpOnly

- `5fa88300` feat(communities): 3 مجتمعات + بحث + انضم (عام ليس مشفراً)
- `5fa88300` CommunitiesTest 3 اختبارات

## 2026-08-11 — feat(reactions): تفاعلات إيموجي E2EE على الرسائل
- RichMessage: `REACTION`/`REACTION_REMOVE` actions + حقول `reactionOf`/`emoji` + تحقق
- Room: `MessageReactionEntity` (PK مركّب messageId+senderId) + migration 1→2 آمن
- RedConnectionService: `ReactionEventBus` + معالجة الاستقبال + `applyOutgoingReactionLocally` + 4 دوال إرسال (فردية/جماعية × إضافة/إزالة)
- UI: `MessageReactions` chips + `ReactionEmojiBar` سريع + تحميل/تحديث فوري (فردية + مجموعات)
- E2EE كامل: الإيموجي داخل ciphertext، الخادم يوجّه فقط (لا تغيير في backend — RICH_TEXT مقبول بالفعل)
- اختبارات: 11 اختبار وحدة في RichMessageTest (round-trip + validation + failure cases)
- التوثيق: docs/REACTIONS_FEATURE_AR.md (Threat model + proto + tests + RTL)

## 2026-08-11 — chore: حذف شاشتَي الاستطلاعات/الفعاليات الميتتين (1,871 سطر)
- حذف EventsScreen.kt (784) + PollsScreen.kt (766) + EventsApi.kt (166) + PollsApi.kt (155) + PollsApiTest.kt
- الشاشتان كانتا ميتتين (صفر نقطة دخول) + مختلطتين إداري/مستخدم (نصفهما يفشل 403 للمستخدم العادي)
- متوافق مع الميثاق: ميزة مجتمعية (مرحلة E) تسبق ترتيب الـ Alpha (الرسائل + المكالمات + الإدارة)
- الـ backend (ContentController/Service + V20 migration) ولوحة الإدارة (ContentManagement.tsx) تبقى تدير الميزة
- استثناءات SecurityConfig للمستخدم تبقى جاهزة لإعادة واجهة نظيفة عند مرحلة E
- Inline Polls في المجموعات (InlinePollCard → RichMessage.poll) مستقلة ولم تُمسّ — تعمل
- صفر ارتدادات (تأكد: صفر مرجع خارجي قبل الحذف) · فاحص التكامل 23/23 أخضر
- التوثيق: docs/AUDIT_FIXES_VERIFIED_AR.md (§ما لم يُنفّذ)
- **عطل تصريف #1:** 3 دوال مفقودة في YounesCallService (silenceRinger/holdActiveCall/resumeRinger) — PhoneStateReceiver كان يستدعيها بلا تعريف ⇒ لا APK
- **عطل تصريب #2:** ApiResult.Success arity (4 مواضع بوسيط واحد بدل اثنين)
- **سباق التوكن:** Mutex مشترك + فحص مزدوج + حذف runBlocking — يمنع الطرد الجماعي للأجهزة عند 401 متوازي
- **unblock 404:** وحّد التطبيق لـ DELETE /block (كان POST /unblock)
- **events GET 405:** أضفت @GetMapping("/events/{eventId}") + getEvent() + استثناء SecurityConfig
- **votePoll كاذب:** أخطاء صريحة (404/409/400) + تحقّق optionId ينتمي للاستطلاع + تحويل آمن
- **قناة red_calls:** وحّدت أهمية الراوتر لـ IMPORTANCE_HIGH (كان MAX = تعارض غير حتمي)
- **CallTelemetry.flush:** نطاق مشترك بـ SupervisorJob (كان نطاق يتيم لكل نداء)
- **ProGuard:** قواعد كاملة (11 قسم) — كان 3 أسطر تحطّم الإصدار
- **13 تحويل غير آمن:** as String → as? String ?: 400 (ContentController + AdminV2Controller)
- **N+1 حذف استطلاع:** deleteAllByPollId (حذف مجمّع) + حذف الأصوات لمنع اليتم
- **حارس عقد:** scripts/check-android-integrity.py (23 فحص) + check-all.sh + CI build-red.yml
- **تصحيحان للتقرير:** StoriesScreen وSettingsScreen ليستا ميتتين (StoryFullscreen/YounesSettingsSheet مستخدمتان)
- التوثيق: docs/AUDIT_FIXES_VERIFIED_AR.md
- **A1 البروفايل**: V25 migration (avatar_url + bio) + UserAccount + updateProfile + PublicRedProfile(avatarUrl)
  + ProfileViewModel + ProfileScreen (صورة مشفّرة + بايو + QR) + MoreScreen
- **A3 قفل البصمة**: androidx.biometric + AppLockScreen (BiometricPrompt) + appLockEnabled
  + MainActivity.onResume lock + PrivacySettings toggle
- **B1 تعديل/حذف للجميع**: مؤكد مكتمل (EDIT + DELETE for everyone في RichMessage + UI)
- **B6 Presence + آخر ظهور**: presenceDetailed (PresenceInfo) + /api/contacts/presence/detailed
  + DirectoryViewModel.lastSeenLabel + عرض في رأس المحادثة + hideLastSeen setting
- التوثيق: docs/FOUR_FEATURES_BUNDLE_AR.md

