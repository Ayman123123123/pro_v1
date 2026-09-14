-- V52__Drop_Cancelled_Pstn_Dinstar_Scope.sql
-- ═══════════════════════════════════════════════════════════════════════
-- القبول النهائي (DoD): إسقاط كل ما تبقى من نطاق PSTN/Dinstar/الهاتف اليمني
-- الملغي (المرحلة 8). الهجرات القديمة (V2..V49) تُترك كما هي عمدًا — حذف هجرة
-- مطبَّقة يكسر سلسلة Flyway — وهذه الهجرة هي الإغلاق الرسمي للنطاق.
--
-- تحقق قبل الكتابة (2026-09-15):
--  - صفر مرجع Kotlin (main+test) لأي جدول/عمود/عرض هنا.
--  - كيان UserAccount لا يملك حقول PSTN — JPA لن يلمس الأعمدة المسقطة.
--  - كل المفاتيح الأجنبية الداخلية (الأبناء ← telecom_gateways/sms_templates/
--    pstn_active_calls/dinstar_sms_templates) تنشأ من هجرات ميتة أيضًا —
--    لا جدول حي يشير لجدول ميت، فـ CASCADE آمن ولا يطال إلا الميت.
--  - العروض الحية (v_user_stats/v_message_stats/v_call_stats/v_trending_hashtags)
--    لا تقرأ أي جدول هنا — تقرأ users/call_history فقط.
--  - جداول V15 (rate_limit/encryption/prekeys/receipts) غير مشار إليها كوديًا
--    لكنها خارج النطاق الملغي — تُركت عمدًا لفرز المالك، لا تُسقط هنا.
-- ═══════════════════════════════════════════════════════════════════════

-- 1) العروض الميتة أولًا (تعتمد على الجداول الميتة).
DROP VIEW IF EXISTS v_pstn_reconcile;
DROP VIEW IF EXISTS v_pstn_daily_stats;
DROP VIEW IF EXISTS v_dinstar_recent_calls;
DROP VIEW IF EXISTS v_dinstar_gateway_stats;
DROP VIEW IF EXISTS v_dinstar_active_alerts;

-- 2) فهارس users على الأعمدة الملغاة (تموت مع الأعمدة، لكن صريح أفضل).
DROP INDEX IF EXISTS ux_users_pstn_number;
DROP INDEX IF EXISTS idx_users_pstn_number;
DROP INDEX IF EXISTS idx_users_pstn_gateway_port;

-- 3) قيود users الملغاة (V5/V34/V38).
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_pstn_daily_limit_check;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_pstn_port_range;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_pstn_binding_consistency;
ALTER TABLE users DROP CONSTRAINT IF EXISTS uq_users_pstn_port;

-- 4) أعمدة users الملغاة (V5/V34).
ALTER TABLE users DROP COLUMN IF EXISTS pstn_enabled;
ALTER TABLE users DROP COLUMN IF EXISTS pstn_daily_limit;
ALTER TABLE users DROP COLUMN IF EXISTS pstn_gateway_id;
ALTER TABLE users DROP COLUMN IF EXISTS pstn_port_index;
ALTER TABLE users DROP COLUMN IF EXISTS pstn_number;

-- 5) الجداول الميتة (35) — V2/V12/V18/V23/V25/V26_1/V33/V36/V39/V42/V43/V44/V48/V49.
-- CASCADE للفهارس/المفاتيح الداخلية فقط — لا تابع حي لأيٍّ منها (موثّق أعلاه).
DROP TABLE IF EXISTS
    dinstar_config,
    dinstar_ports,
    dinstar_logs,
    dinstar_device_status,
    dinstar_cdr,
    dinstar_sms_log,
    dinstar_ussd_log,
    dinstar_sms_templates,
    dinstar_sms_scheduled,
    dinstar_sms_result,
    dinstar_sms_delivery_status,
    dinstar_incoming_sms,
    dinstar_sms_cursor,
    dinstar_port_control,
    dinstar_daily_stats,
    dinstar_alerts,
    dinstar_config_changes,
    telecom_gateways,
    gateway_port_snapshots,
    gateway_operations,
    gateway_sim_inventory,
    gateway_route_decisions,
    gateway_port_reservations,
    gateway_config_backups,
    gateway_health_history,
    pstn_active_calls,
    pstn_call_timeline,
    sms_templates,
    scheduled_sms,
    sms_messages,
    sms_conversation_read,
    port_control_state,
    number_learning_config,
    number_learning_pool,
    number_learning_calls
CASCADE;
