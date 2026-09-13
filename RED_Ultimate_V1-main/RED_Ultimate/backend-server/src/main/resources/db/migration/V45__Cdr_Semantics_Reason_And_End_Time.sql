-- V45__Cdr_Semantics_Reason_And_End_Time.sql
-- ═══════════════════════════════════════════════════════════════════════
-- تصحيح دلالة عمودَين في `dinstar_cdr`:
--   1. `hangup_cause` ← حقل `reason` (السبب) لا `hangup` (الطرف المنهي)
--   2. `end_time` يُحسَب بدل أن يبقى فارغًا في كل صفّ
-- ═══════════════════════════════════════════════════════════════════════
--
-- ## العطل الأول: `hangup_cause` كان يحمل الطرف لا السبب
--
-- ردّ `get_cdr` من UC2000-VE يحمل حقلَين متمايزَين:
--
--   "hangup": "calling"          ← **مَن** أنهى المكالمة (calling/called)
--   "reason": "NORMAL HANG UP"   ← **لماذا** انتهت
--
-- وكان المُدرِجان يكتبان `hangup` في عمود اسمه `hangup_cause`. النتيجة: 110
-- صفًّا من 110 تحمل القيمة `calling` — أي «المتصل أنهى» — وهي جملة صحيحة عن
-- الطرف لكنها لا تقول شيئًا عن السبب. فتحليل أسباب الإخفاق (لا رد؟ مشغول؟
-- شبكة؟) كان مستحيلًا: العمود المخصَّص للسبب لا يحمل سببًا قطّ، ويُظهر توزيعًا
-- من قيمة واحدة يتيمة تُقرأ كأن كل المكالمات انتهت بالطريقة نفسها.
--
-- لاحظ أن `callOutcome` لم يتأثّر: هو يقرأ `hangup` أيضًا لكن لتصنيف الحالة
-- (`answered|no_answer|busy|failed|cancelled`)، وذاك استخدام مقصود ودقيق.
-- الخطأ كان في **تخزين** `hangup` تحت اسم `hangup_cause`.
--
-- ## العطل الثاني: `end_time` فارغ في كل صفّ
--
-- العمود موجود في V15 ولم يكتبه أيّ مُدرِج (0/110). وهو مشتقّ بالكامل من
-- حقول موجودة:
--
--   * مُجابة:      `answer_time + duration_seconds`
--     (`duration` في ردّ الجهاز هو زمن التحدُّث بعد الإجابة — تؤكّده العيّنة:
--      `start=17:55:08`, `answer=17:55:11`, `duration=2` ⇒ رنين 3ث ثم تحدُّث 2ث)
--   * غير مُجابة:  `start_time + ring_duration_seconds + duration_seconds`
--     (لا زمن إجابة، فالنهاية بعد الرنين مباشرةً)
--
-- بلا هذا العمود كان كل استعلام «كم استغرقت المكالمة من البداية إلى النهاية»
-- أو «ما التراكب الزمني بين مكالمتين على منفذَين» يتطلّب إعادة الحساب في كل
-- استدعاء، ولا فهرس يخدمه.
--
-- ## الاتّساق مع الكود
--
-- [DinstarApiContract.Cdr.INSERT_SQL] صار يُدرج `end_time` ويكتب `reason` في
-- `hangup_cause`، و[DinstarApiContract.Cdr.endTime] يُشتقّ القيمة بالمنطق
-- نفسه المكتوب أدناه. هذه الهجرة تُصلح الصفوف **القائمة**؛ والصفوف الجديدة
-- تُدرَج صحيحةً من المصدر.
--
-- ## لماذا `raw_data` هو المصدر
--
-- كل صفّ يحفظ ردّ الجهاز الخام في `raw_data` (V40)، فالتصحيح لا يحتاج
-- استدعاء الجهاز مجدَّدًا ولا يفقد شيئًا: `reason` محفوظ هناك حرفيًّا.

-- ── 1. hangup_cause ← reason ────────────────────────────────────────────
-- الشرط `raw_data ? 'reason'` يحمي الصفوف التي لا تحمل الحقل (إصدار أقدم من
-- البرنامج الثابت): تُترك كما هي بدل أن تُفرَّغ.
UPDATE dinstar_cdr
   SET hangup_cause = raw_data->>'reason'
 WHERE raw_data ? 'reason'
   AND (raw_data->>'reason') <> ''
   AND (hangup_cause IS DISTINCT FROM raw_data->>'reason');

-- ── 2. end_time محسوب ───────────────────────────────────────────────────
-- `make_interval` لا سَلسَلة نصّية: بناء `(x || ' seconds')::interval` يعتمد
-- على `lc_numeric` ويكسر مع أعداد كبيرة، و`make_interval` يأخذ عددًا صحيحًا.
UPDATE dinstar_cdr
   SET end_time = CASE
           WHEN answer_time IS NOT NULL
               THEN answer_time + make_interval(secs => duration_seconds)
           ELSE start_time + make_interval(
                    secs => COALESCE(ring_duration_seconds, 0) + duration_seconds
                )
       END
 WHERE end_time IS NULL
   AND start_time IS NOT NULL;

-- ── 3. توثيق الدلالة في المخطَّط نفسه ──────────────────────────────────
-- التعليق يمنع تكرار الالتباس: القارئ التالي يرى الفرق بين reason وhangup
-- في القاعدة لا في تاريخ Git.
COMMENT ON COLUMN dinstar_cdr.hangup_cause IS
    'سبب الإنهاء من الجهاز — حقل reason (NORMAL HANG UP / NO ANSWER / USER BUSY...). '
    'حقل hangup الخام (calling/called) يدلّ على الطرف المنهي لا السبب، ويُستخدم '
    'لتصنيف status عبر callOutcome فقط.';

COMMENT ON COLUMN dinstar_cdr.end_time IS
    'زمن نهاية المكالمة، مشتقّ لا مُصدَر من الجهاز: '
    'مُجابة = answer_time + duration_seconds؛ '
    'غير مُجابة = start_time + ring_duration_seconds + duration_seconds.';

-- ── 4. فهرس للاستعلامات الزمنية على النهاية ────────────────────────────
-- تقارير التراكب والإشغال ترتّب بـ end_time؛ بلا فهرس تُمسح الجدول كاملًا.
CREATE INDEX IF NOT EXISTS idx_dinstar_cdr_end_time
    ON dinstar_cdr (end_time DESC)
    WHERE end_time IS NOT NULL;
