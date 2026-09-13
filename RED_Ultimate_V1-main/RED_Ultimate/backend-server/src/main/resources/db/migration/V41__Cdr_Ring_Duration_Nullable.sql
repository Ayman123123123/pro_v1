-- V41__Cdr_Ring_Duration_Nullable.sql
-- ═══════════════════════════════════════════════════════════════════════
-- `ring_duration_seconds` يقبل NULL: المكالمة غير المُجابة لا زمن رنين لها.
-- ═══════════════════════════════════════════════════════════════════════
--
-- ## ما كان معطوبًا
--
-- العمود مُعرَّف في V15 بـ`NOT NULL DEFAULT 0`، ولم يكن أيّ مُدرِج يكتبه —
-- فحمل الصفر في 110 صفًّا من 110. والصفر هنا ليس «غير معروف» بل جملةٌ خبرية:
-- «رَنَّ صفر ثانية»، أي أُجيبت لحظيًا. فيُقرأ سجل مكالمات كامل كأن كل نداء
-- أُجيب فورًا.
--
-- الطرفان اللازمان للحساب موجودان في كل صفّ (`start_time` و`answer_time`)،
-- فصار [DinstarApiContract.Cdr.ringSeconds] يشتقّه. لكن المكالمة غير المُجابة
-- بلا `answer_time` أصلًا، فزمن رنينها **مجهول** لا صفر: الجهاز لا يُصدر زمن
-- الإنهاء في `get_cdr`، فلا سبيل لمعرفة كم رَنَّ قبل أن يُقطع.
--
-- بقاء `NOT NULL DEFAULT 0` يُجبر المُدرِج على كتابة صفرٍ كاذب في كل مكالمة
-- غير مُجابة — وهو نفس العيب الذي نُصلحه. لذا يُرفَع القيد ويُزال الافتراضي:
-- NULL هو التمثيل الصادق للمجهول.
--
-- ## لماذا لا تُحدَّث الصفوف القائمة
--
-- الصفوف الـ110 الحالية كلها `answered` بـ`answer_time` غير فارغ، فقيمتها
-- الصحيحة محسوبة لا مجهولة — وتُصحَّح أدناه من طرفَيها بدل تركها صفرًا.
-- ما لا `answer_time` له يُترك NULL.

ALTER TABLE dinstar_cdr
    ALTER COLUMN ring_duration_seconds DROP DEFAULT,
    ALTER COLUMN ring_duration_seconds DROP NOT NULL;

COMMENT ON COLUMN dinstar_cdr.ring_duration_seconds IS
    'زمن الرنين بالثواني = answer_time - start_time. NULL للمكالمة غير المُجابة: '
    'الجهاز لا يُصدر زمن الإنهاء في get_cdr فزمن رنينها مجهول، والصفر يُقرأ «أُجيبت فورًا».';

-- تصحيح الصفوف التي يمكن حسابها: أُدرِجت قبل الاشتقاق فحملت افتراضي الصفر.
-- الشرط `answer_time IS NOT NULL` يمنع كتابة صفرٍ كاذب في غير المُجابة،
-- و`>= start_time` يحمي من ساعة جهاز عُدِّلت بين الحقلين.
UPDATE dinstar_cdr
   SET ring_duration_seconds = EXTRACT(EPOCH FROM (answer_time - start_time))::int
 WHERE answer_time IS NOT NULL
   AND answer_time >= start_time
   AND COALESCE(ring_duration_seconds, 0) = 0;

-- ما لا يُحسَب يُعلَن مجهولًا صراحةً بدل صفرٍ موروث من الافتراضي المحذوف.
UPDATE dinstar_cdr
   SET ring_duration_seconds = NULL
 WHERE answer_time IS NULL
   AND ring_duration_seconds = 0;
