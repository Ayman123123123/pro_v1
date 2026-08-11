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

