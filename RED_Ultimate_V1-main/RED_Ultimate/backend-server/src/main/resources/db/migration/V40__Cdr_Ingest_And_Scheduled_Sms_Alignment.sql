-- V40__Cdr_Ingest_And_Scheduled_Sms_Alignment.sql
-- ═══════════════════════════════════════════════════════════════════════
-- محاذاة `dinstar_cdr` و`scheduled_sms` مع ما تكتبه الشيفرة فعلًا.
-- ═══════════════════════════════════════════════════════════════════════
--
-- ## كيف وقع العطل
--
-- `dinstar_cdr` أُنشئ في V15 بأعمدة: `duration_seconds` و`status`.
-- ثم كتب V26_1 تعريفًا ثانيًا للجدول نفسه بأعمدة مختلفة
-- (`duration`, `hangup_cause`, `codec`, `sip_call_id`, `asterisk_channel`,
-- `raw_data`) — لكنه `CREATE TABLE IF NOT EXISTS`، والجدول كان موجودًا،
-- فلم يُنفَّذ التعريف الثاني ولا سطر منه. بقي الجدول على شكل V15.
--
-- الشيفرة كُتبت على الشكل الثاني المتخيَّل:
--
--   * `CdrIngestScheduler.ingestFrom` تُدخل `duration, hangup_cause, codec,
--     raw_data` — أعمدة لا وجود لها. الإدراج يرفع
--     `column "duration" of relation "dinstar_cdr" does not exist`،
--     والاستثناء يُبتلَع في `catch` فوق الحلقة ويُسجَّل على مستوى DEBUG.
--     النتيجة: `dinstar_cdr` بقي فارغًا (0 صفوف) بلا أي أثر في السجل
--     العادي، مع أن المُجدول يعمل كل خمس دقائق منذ نشره.
--
--   * `DinstarApiService.saveCdrRecord` تُدخل الأعمدة نفسها + عمودين
--     آخرين، وتفشل بالمثل.
--
-- `scheduled_sms` أُنشئ في V25 بلا `error_text` ولا `updated_at`، بينما
-- `ScheduledSmsDispatcher` يكتبهما في مسار الفشل — أي أن تسجيل سبب فشل
-- رسالة مجدوَلة يفشل هو نفسه، فتبقى الرسالة على `PENDING` وتُعاد
-- محاولتها كل دقيقة أبدًا.
--
-- ## ما تفعله هذه الترقية
--
-- تُضيف الأعمدة التشخيصية الناقصة إلى الشكل القائم (لا تُعيد تسمية
-- `duration_seconds` ولا `status`: كلاهما مستخدم في V26_1 view وفي
-- سياسات الاحتفاظ، وإعادة التسمية تكسرهما). الشيفرة تُصحَّح لتكتب
-- الأسماء الحقيقية.

-- ─────────────────────────────────────────────────────────────────────
-- 1. أعمدة CDR التشخيصية
-- ─────────────────────────────────────────────────────────────────────

ALTER TABLE dinstar_cdr
    -- سبب الإنهاء النصي من `get_cdr.hangup` — يفسّر لماذا لم تُجَب.
    ADD COLUMN IF NOT EXISTS hangup_cause VARCHAR(50),

    -- ترميز الصوت المتفاوَض عليه (`codec`) — لتشخيص جودة المكالمة.
    ADD COLUMN IF NOT EXISTS codec VARCHAR(20),

    -- رمز إطلاق GSM من الشبكة (`gsm_code`) — كان يُحسب في الشيفرة ثم
    -- يُهمَل لعدم وجود عمود له.
    ADD COLUMN IF NOT EXISTS gsm_code INTEGER,

    -- قناة Asterisk ومعرّف SIP — يربطان سجل البوابة بسجل الوسيط.
    ADD COLUMN IF NOT EXISTS sip_call_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS asterisk_channel VARCHAR(100),

    -- الرد الخام كما ورد من الجهاز. يُحفظ لأن حقول `get_cdr` تتغيّر
    -- بين إصدارات البرنامج الثابت، فبلا الخام يستحيل التحقيق لاحقًا.
    ADD COLUMN IF NOT EXISTS raw_data JSONB;

COMMENT ON COLUMN dinstar_cdr.hangup_cause IS
    'حقل hangup من get_cdr — السبب النصي لإنهاء المكالمة.';
COMMENT ON COLUMN dinstar_cdr.gsm_code IS
    'حقل gsm_code من get_cdr — رمز إطلاق شبكة GSM.';
COMMENT ON COLUMN dinstar_cdr.raw_data IS
    'استجابة get_cdr الخام لهذا السجل — مرجع التحقيق عند تغيّر الحقول.';

-- ─────────────────────────────────────────────────────────────────────
-- 2. الحقول الإلزامية التي لا تملأها عملية الابتلاع
-- ─────────────────────────────────────────────────────────────────────
--
-- `id` من نوع UUID و`NOT NULL` بلا قيمة افتراضية، فكل إدراج كان
-- ملزَمًا بتوليده في التطبيق. إعطاؤه افتراضًا في قاعدة البيانات يجعل
-- الإدراج الجزئي (المسار الصحيح: عمود واحد لكل حقل معروف) ممكنًا
-- ويمنع تكرار منطق التوليد في كل موضع.

ALTER TABLE dinstar_cdr
    ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- ─────────────────────────────────────────────────────────────────────
-- 3. منع تكرار سجلات CDR على مستوى قاعدة البيانات
-- ─────────────────────────────────────────────────────────────────────
--
-- كان المُجدول يفحص الوجود بـ`SELECT COUNT(*)` ثم يُدرج: نافذة سباق
-- مفتوحة بين الفحص والإدراج، ودورتان متزامنتان تُدرجان السجل مرتين.
-- المفتاح الطبيعي لسجل مكالمة على بوابة هو (البوابة، المنفذ، زمن
-- البدء، المتصل، المطلوب) — فيصير القيد هو الضمانة، ويصبح الإدراج
-- `ON CONFLICT DO NOTHING` بلا فحص مسبق.
--
-- الفهرس جزئي: السجلات القديمة قد تحمل NULL في هذه الحقول، و`UNIQUE`
-- على NULL لا يمنع التكرار أصلًا، فيُستثنى الناقص صراحةً.

CREATE UNIQUE INDEX IF NOT EXISTS uq_dinstar_cdr_natural_key
    ON dinstar_cdr(gateway_id, port_index, start_time, caller_number, callee_number)
    WHERE gateway_id IS NOT NULL
      AND port_index IS NOT NULL
      AND start_time IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────
-- 4. أعمدة تتبّع فشل الرسائل المجدوَلة
-- ─────────────────────────────────────────────────────────────────────

ALTER TABLE scheduled_sms
    -- سبب الفشل كما ورد من البوابة أو من الاستثناء. بلا هذا العمود كان
    -- تسجيل الفشل يفشل، فتبقى الرسالة PENDING وتُعاد كل دقيقة.
    ADD COLUMN IF NOT EXISTS error_text VARCHAR(300),

    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- حالة RETRYING تلزم لتمييز «فشل مؤقت سيُعاد» من «فشل نهائي»، وهي
-- مستخدمة في دورة حياة sms_messages بعد V39 فتُوحَّد هنا أيضًا.
ALTER TABLE scheduled_sms
    DROP CONSTRAINT IF EXISTS scheduled_sms_status_check;

ALTER TABLE scheduled_sms
    ADD CONSTRAINT scheduled_sms_status_check
    CHECK (status IN ('PENDING','QUEUED','SENT','DELIVERED','FAILED','CANCELLED','RETRYING'));

COMMENT ON COLUMN scheduled_sms.error_text IS
    'سبب فشل آخر محاولة إرسال — كان يُكتب إلى عمود غير موجود.';
