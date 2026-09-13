-- V39__Sms_Delivery_Correlation.sql
-- ═══════════════════════════════════════════════════════════════════════
-- إصلاح مسار تتبّع الرسائل من الطرف إلى الطرف مع بوابة DINSTAR UC2000.
-- ═══════════════════════════════════════════════════════════════════════
--
-- ## العطل الذي تُعالجه هذه الترقية
--
-- واجهة UC2000 تربط الرسالة بتقرير تسليمها عبر مفتاحين لا ثالث لهما:
--
--   1. `user_id`  — عدد صحيح يُرسله العميل في `send_sms`، ويُعاد كما هو
--                   في `query_sms_result`. هو المفتاح الذي يعرّف «أي
--                   رسالة من رسائلي» في الرد.
--   2. `ref_id`   — مرجع تُصدره الشبكة، يظهر في `query_sms_result`، ثم
--                   يعود في `query_sms_deliver_status`. هو المفتاح الذي
--                   يُطابَق به تقرير التسليم.
--
-- الشيفرة السابقة لم تحفظ أيًّا منهما:
--
-- * `SmsService.send` كان يرسل `user_id = users.red_id` وهو **نص**
--   (مثل "RED-8F3A"). الحقل رقمي في الواجهة، فالبوابة ترفضه أو تُصفّره،
--   وبأي حال لا يعود في الرد فيستحيل معرفة صاحب النتيجة.
--
-- * تقرير التسليم كان يُطابَق **بالرقم**. رسالتان إلى الرقم نفسه في
--   دقيقة واحدة — وهو الشائع في OTP والتنبيهات — تخلطان تقريريهما،
--   فتُعلَّم رسالة فاشلة كأنها وصلت.
--
-- * `status_code` الرقمي (0 وصلت / 32-63 فشل مؤقت / 64-255 فشل دائم)
--   كان يُقرأ كأنه نص `status`. الحقل النصي غير موجود في هذا المسار،
--   فلم تنتقل أي رسالة من SENT إلى DELIVERED منذ كتابة الشيفرة.
--
-- * الرسائل الواردة كانت تُقرأ بلا `incoming_sms_id`، فيُعاد الصندوق
--   كاملًا كل 12 ثانية، وتُبنى إزالة التكرار على تشابه النص خلال
--   دقيقتين — حيلة تُسقط رسالتين متطابقتين حقيقيتين وتُبقي التكرار
--   إن تأخّرت الدورة.
--
-- ## ما تضيفه
--
-- أعمدة المطابقة على `sms_messages`، وجدول مؤشّر لكل بوابة يحفظ أعلى
-- `incoming_sms_id` عُلِم به، وحالتان جديدتان في دورة حياة الرسالة.

-- ─────────────────────────────────────────────────────────────────────
-- 1. أعمدة المطابقة
-- ─────────────────────────────────────────────────────────────────────

ALTER TABLE sms_messages
    -- المعرّف الرقمي المُرسل إلى البوابة في send_sms.param[].user_id.
    -- لا يُشتق من red_id: ذاك نص، وهذا يجب أن يكون عددًا فريدًا.
    ADD COLUMN IF NOT EXISTS dinstar_user_id BIGINT,

    -- مرجع الشبكة من query_sms_result — به يُطابَق تقرير التسليم.
    ADD COLUMN IF NOT EXISTS dinstar_ref_id BIGINT,

    -- معرّف مهمة الإرسال من رد send_sms، يلزم لإيقافها عبر stop_sms.
    ADD COLUMN IF NOT EXISTS dinstar_task_id BIGINT,

    -- المؤشّر التزايدي للرسالة الواردة — أساس القراءة بلا تكرار.
    ADD COLUMN IF NOT EXISTS incoming_sms_id BIGINT,

    -- الترميز الفعلي على السلك (gsm-7bit / unicode). كان يُحسب ثم
    -- يُنسى، فيستحيل تفسير لماذا انقسمت رسالة إلى أجزاء أكثر.
    ADD COLUMN IF NOT EXISTS encoding VARCHAR(12),

    -- status_code الخام كما ورد، محفوظًا للتشخيص بعد الترجمة.
    ADD COLUMN IF NOT EXISTS delivery_status_code INT,

    -- عدد الأجزاء التي أكّدت الشبكة نجاحها (succ_count مقابل count).
    ADD COLUMN IF NOT EXISTS parts_confirmed INT,

    -- IMSI الشريحة المُرسِلة — يُثبّت أي رقم ظهر للمستلم فعلًا.
    ADD COLUMN IF NOT EXISTS sender_imsi VARCHAR(32),

    -- آخر محاولة استعلام: يمنع إعادة الاستعلام عن الشيء نفسه كل دورة.
    ADD COLUMN IF NOT EXISTS last_polled_at TIMESTAMPTZ,

    -- عدد مرات إعادة الإرسال بعد فشل مؤقت (status_code 32..63).
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;

-- المطابقة تجري بهذين المفتاحين، فلا بد من فهرسة كل منهما.
CREATE INDEX IF NOT EXISTS idx_sms_dinstar_user_id
    ON sms_messages(dinstar_user_id) WHERE dinstar_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sms_dinstar_ref_id
    ON sms_messages(dinstar_ref_id) WHERE dinstar_ref_id IS NOT NULL;

-- دورة الاستعلام تبحث عن الرسائل المعلّقة الأقدم استعلامًا.
CREATE INDEX IF NOT EXISTS idx_sms_pending_poll
    ON sms_messages(status, last_polled_at NULLS FIRST)
    WHERE direction = 'OUT' AND status IN ('QUEUED', 'SENT');

-- ─────────────────────────────────────────────────────────────────────
-- 2. منع تكرار الرسائل الواردة على مستوى قاعدة البيانات
-- ─────────────────────────────────────────────────────────────────────
--
-- المؤشّر التزايدي فريد لكل بوابة. القيد هنا هو الضمانة الأخيرة: حتى
-- إن تعطّل المؤشّر أو تسابقت دورتان، لا تُدرَج الرسالة نفسها مرتين.
-- الشرط الجزئي يستثني الصادر والرسائل القديمة التي لا تحمل مؤشّرًا.

CREATE UNIQUE INDEX IF NOT EXISTS uq_sms_incoming_per_gateway
    ON sms_messages(gateway_id, incoming_sms_id)
    WHERE direction = 'IN' AND incoming_sms_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────
-- 3. مؤشّر القراءة لكل بوابة
-- ─────────────────────────────────────────────────────────────────────
--
-- بدل قراءة الصندوق كاملًا كل دورة، يُحفَظ أعلى معرّف عُلِم به لكل
-- بوابة ويُمرَّر في الطلب التالي. هذا هو ما تتوقعه الواجهة أصلًا.

CREATE TABLE IF NOT EXISTS dinstar_sms_cursor (
    gateway_id          UUID PRIMARY KEY,

    -- أعلى incoming_sms_id تمّت معالجته بنجاح.
    last_incoming_id    BIGINT      NOT NULL DEFAULT 0,

    -- آخر مزامنة ناجحة — للتشخيص وقياس تأخّر الابتلاع.
    last_sync_at        TIMESTAMPTZ,

    -- عدد الدورات الفاشلة المتتالية. تُستخدم لكتم السجل ولفتح
    -- قاطع الدائرة بدل إغراق البوابة بمحاولات على جهاز ساقط.
    consecutive_failures INT        NOT NULL DEFAULT 0,

    last_error          VARCHAR(300),

    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_sms_cursor_id_non_negative CHECK (last_incoming_id >= 0)
);

COMMENT ON TABLE dinstar_sms_cursor IS
    'مؤشّر القراءة التزايدية للرسائل الواردة لكل بوابة (incoming_sms_id).';

-- ─────────────────────────────────────────────────────────────────────
-- 3.b مُصدِر معرّفات user_id
-- ─────────────────────────────────────────────────────────────────────
--
-- `user_id` يجب أن يكون عددًا فريدًا **يبقى فريدًا بعد إعادة التشغيل**.
-- التوليد السابق كان `AtomicInteger` مُهيَّأً من `currentTimeMillis()`:
-- يفقد موضعه عند كل إقلاع، فقد يعيد إصدار معرّف لرسالة ما زالت معلّقة
-- في البوابة، فتُنسَب نتيجتها إلى الرسالة الخطأ.
--
-- التسلسل في قاعدة البيانات يحلّ ذلك: مركزي، ذرّي، ومستمر.
-- الحد الأعلى 2^31-1 يقارب حد `int` في الواجهة، والدورة تعيد من 1
-- بأمان لأن الرسائل القديمة تكون قد أُنجزت قبل استنفاد المدى.

CREATE SEQUENCE IF NOT EXISTS dinstar_sms_user_id_seq
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 2147483647
    CYCLE;

COMMENT ON SEQUENCE dinstar_sms_user_id_seq IS
    'مُصدِر send_sms.param[].user_id — فريد ومستمر عبر إعادة التشغيل.';

-- ─────────────────────────────────────────────────────────────────────
-- 4. توسيع دورة حياة الرسالة
-- ─────────────────────────────────────────────────────────────────────
--
-- كانت الحالات: PENDING, SENT, DELIVERED, FAILED, RECEIVED. وكان
-- `SENT` يُكتب بمجرد ردّ HTTP 2xx — أي حتى مع error_code = 550 (لا منفذ
-- متاح) أو 413 (تجاوز الحد). فيرى المستخدم «أُرسلت» ولم تُرسَل.
--
-- تُضاف حالتان تفصلان المراحل الثلاث فصلًا صحيحًا:
--   QUEUED    — البوابة قبلت الطلب (error_code 202) ولم تسلّمه للشبكة.
--   SENT      — الشبكة قبلته (query_sms_result: SENT_OK) ومعه ref_id.
--   DELIVERED — تقرير التسليم أكّد الوصول (status_code = 0).
--   RETRYING  — فشل مؤقت (32..63) وسيُعاد.
--
-- العمود VARCHAR(12) يتّسع لأطولها ("DELIVERED" = 9، "RETRYING" = 8).

ALTER TABLE sms_messages
    ALTER COLUMN status TYPE VARCHAR(12);

-- ─────────────────────────────────────────────────────────────────────
-- 5. تعليقات توثيقية على الأعمدة الحرجة
-- ─────────────────────────────────────────────────────────────────────

COMMENT ON COLUMN sms_messages.dinstar_user_id IS
    'المعرّف الرقمي في send_sms.param[].user_id — به تُطابَق نتيجة query_sms_result.';
COMMENT ON COLUMN sms_messages.dinstar_ref_id IS
    'مرجع الشبكة من query_sms_result — به يُطابَق query_sms_deliver_status.';
COMMENT ON COLUMN sms_messages.delivery_status_code IS
    'TP-Status الخام (3GPP TS 23.040 §9.2.3.15): 0 وصلت، 32-63 مؤقت، 64-255 دائم.';
COMMENT ON COLUMN sms_messages.incoming_sms_id IS
    'المؤشّر التزايدي من query_incoming_sms — فريد لكل بوابة.';
