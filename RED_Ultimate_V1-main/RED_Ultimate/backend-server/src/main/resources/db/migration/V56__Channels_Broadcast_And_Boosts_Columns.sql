-- V56: أعمدة P1-G (وضع البث + عدّاد التعزيز) تدخل المخطَّط رسميًا.
--
-- كانت هذان العمودان موجودَين فقط عبر ALTER TABLE وقت التشغيل داخل
-- ChannelService.ensureBroadcastBoostColumns()، ولا يوجد أي ترحيل يُنشئهما
-- (فُحص V1..V55). نتائج ذلك:
--   1) التطبيق يحتاج صلاحية DDL وقت التشغيل في الإنتاج — صلاحية لا يُفترض منحها.
--   2) تاريخ Flyway لا يسجّلهما ⇒ انحراف مخطَّط بين البيئات (قاعدة جديدة بلا
--      العمودين تتصرف بشكل مختلف حتى أول create()/addBoosts()).
--   3) mapChannelRow وcreate() يحملان مسارات تراجع دفاعية سببها غياب العمودين.
--
-- ADD COLUMN IF NOT EXISTS مطابق تمامًا لما كان الـ self-heal ينفّذه، فيبقى
-- إبقاء الـ self-heal غير ضار إلى أن تُطبَّق الترحيلات على كل البيئات.
ALTER TABLE channels ADD COLUMN IF NOT EXISTS is_broadcast BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE channels ADD COLUMN IF NOT EXISTS boosts_count INTEGER NOT NULL DEFAULT 0;

-- تصحيح انحراف subscriber_count القائم.
--
-- create() كان يُدرج القناة بـ subscriber_count = 0 (افتراضي V26) ثم يُدرج صف
-- المالك في channel_members بلا زيادة العدّاد، بينما join()/leave() يحافظان على
-- الثابت subscriber_count == عدد صفوف channel_members. فتكون نتيجة الإنشاء خاطئة
-- بمقدار -1 لكل قناة أُنشئت، وتظل PostgreSQL متأخرة بصفٍّ واحد عن MongoDB دائمًا.
--
-- إعادة حساب كاملة (idempotent — إعادة تشغيلها لا تُغيّر شيئًا بعد الاستقرار).
UPDATE channels c
SET subscriber_count = m.cnt
FROM (
    SELECT channel_id, COUNT(*)::int AS cnt
    FROM channel_members
    GROUP BY channel_id
) m
WHERE m.channel_id = c.id
  AND c.subscriber_count <> m.cnt;

-- قنوات بلا أي صف عضوية (يتيم من حذف يدوي) تُصفَّر بدل أن تُترك بعدّاد وهمي.
UPDATE channels c
SET subscriber_count = 0
WHERE NOT EXISTS (SELECT 1 FROM channel_members m WHERE m.channel_id = c.id)
  AND c.subscriber_count <> 0;
