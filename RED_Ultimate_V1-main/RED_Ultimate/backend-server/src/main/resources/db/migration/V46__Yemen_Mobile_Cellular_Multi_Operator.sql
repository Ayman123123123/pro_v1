-- V46__Yemen_Mobile_Cellular_Multi_Operator.sql
-- ═══════════════════════════════════════════════════════════════════════
-- يمن موبايل ليست GSM: CDMA2000 (BC0 800) + LTE (MCC 421 MNC 03، بادئتا 77/78).
-- هذا الجهاز UC2000-VE @192.168.11.1 (SN dd45-0104-2109-0014، PKG 02240221،
-- PCB3، Userboard B3.11.19.11L4) يحمل وحدات LTE/CDMA — الكود السابق كان
-- يفترض GSM وSabafon فقط في 9 مواضع. هذه الهجرة تصلح البيانات لا الكود.
-- ═══════════════════════════════════════════════════════════════════════
--
-- ## 1. تعرفة V15 كانت مقلوبة: 77→سبأفون و71→MTN و73→يموبايل
-- الصحيح (YemenNumberPlan): 71=Sabafon، 73=YOU، 77/78=YemenMobile، 70=YTelecom.
-- بدون هذا تُحاسَب مكالمات 77 بتعرفة سبأفون ويتعلّم LCR تكاليف معكوسة.

UPDATE pstn_tariffs SET name = 'سبأفون اليمن', prefix_pattern = '71'
 WHERE id = '00000000-0000-0000-0000-000000000001'
   AND (prefix_pattern <> '71' OR name <> 'سبأفون اليمن');

UPDATE pstn_tariffs SET name = 'يو YOU (MTN سابقاً)', prefix_pattern = '73'
 WHERE id = '00000000-0000-0000-0000-000000000002'
   AND (prefix_pattern <> '73' OR name NOT LIKE 'يو%');

UPDATE pstn_tariffs SET name = 'يمن موبايل Yemen Mobile (CDMA/LTE)', prefix_pattern = '77'
 WHERE id = '00000000-0000-0000-0000-000000000003'
   AND (prefix_pattern <> '77' OR name NOT LIKE '%يمن موبايل%');

UPDATE pstn_tariffs SET name = 'واي Y Telecom', prefix_pattern = '70'
 WHERE id = '00000000-0000-0000-0000-000000000004'
   AND (prefix_pattern <> '70' OR name NOT LIKE '%واي%' AND name NOT LIKE '%Y Telecom%');

-- صف يمن موبايل 78 (لم يكن موجوداً — كل 78 كانت تُحاسَب 77 أو تسقط)
INSERT INTO pstn_tariffs(id, name, country_code, prefix_pattern, rate_per_minute_yer, rate_per_sms_yer, billing_increment_seconds)
VALUES ('00000000-0000-0000-0000-000000000005', 'يمن موبايل Yemen Mobile 78 (CDMA/LTE)', '967', '78', 15.0000, 5.0000, 60)
ON CONFLICT (id) DO NOTHING;

-- ── 2. radio_type أوسع: LTE-FDD/... يتجاوز VARCHAR(20) ──────────────────
-- 'LTE-FDD B3/VoLTE' و'CDMA2000 1xEV-DO' تُقصّ إلى 20 حرفاً فتضيع.
-- v_pstn_reconcile يعتمد على العمود فيجب إسقاطه أولاً ثم إعادة بنائه (V38).
DROP VIEW IF EXISTS v_pstn_reconcile;
ALTER TABLE gateway_port_snapshots ALTER COLUMN radio_type TYPE VARCHAR(60);
CREATE OR REPLACE VIEW v_pstn_reconcile AS
SELECT
    g.id                          AS gateway_id,
    g.host                        AS gateway_host,
    g.model                       AS gateway_model,
    g.enabled                     AS gateway_enabled,
    ps.port_index                 AS port_index,
    ps.radio_type                 AS radio_type,
    ps.registration_state         AS registration_state,
    ps.call_state                 AS call_state,
    ps.signal_raw                 AS signal_raw,
    ps.signal_dbm                 AS signal_dbm,
    ps.signal_percent             AS signal_percent,
    ps.signal_usable              AS signal_usable,
    ps.operator_name              AS operator_name,
    ps.gprs_state                 AS gprs_state,
    ps.sim_number_masked          AS live_number_masked,
    ps.imsi_masked                AS imsi_masked,
    ps.iccid_masked               AS iccid_masked,
    ps.observed_at                AS observed_at,
    u.id                          AS bound_user_id,
    u.red_id                      AS bound_red_id,
    u.username                    AS bound_username,
    u.pstn_number                 AS bound_number,
    CASE
        WHEN u.id IS NULL AND ps.signal_usable THEN 'UNBOUND_HAS_SIM'
        WHEN u.id IS NOT NULL AND ps.sim_number_masked IS NULL AND ps.signal_usable THEN 'NEEDS_NUMBER_LEARNING'
        WHEN u.id IS NOT NULL AND ps.signal_usable = false AND ps.sim_number_masked IS NULL THEN 'ORPHAN_BINDING_NEEDS_CLEAR'
        WHEN u.id IS NULL THEN 'EMPTY'
        ELSE 'BOUND'
    END AS reconcile_status
FROM telecom_gateways g
LEFT JOIN gateway_port_snapshots ps ON ps.gateway_id = g.id
LEFT JOIN users u ON u.pstn_gateway_id = g.id AND u.pstn_port_index = ps.port_index
WHERE g.vendor = 'DINSTAR';

-- ── 3. gsm_code هو release cause لكل RAT لا GSM فقط — وثّق لا تُعد تسمية ──
-- إعادة التسمية تكسر API؛ التعليق يمنع سوء القراءة ليمن موبايل LTE (380/486).
COMMENT ON COLUMN dinstar_cdr.gsm_code IS
    'رمز الإطلاق من الشبكة (حقل gsm_code/hangup الخام) — لكل تقنيات الراديو '
    '(GSM/CDMA2000/LTE)، ليس GSM فقط. يمن موبايل LTE تُرجع نفس الحقل عبر IMS.';

-- ── 4. بوابة البذرة: عطّل الأشباح .2/.3، وحّد على .1 الحي ───────────────
-- بعد تغيير .env إلى DINSTAR_IPS=192.168.11.1 تبقى صفوف .2/.3 مفعّلة
-- في telecom_gateways فيستمر heartbeat في Connect timed out ويختارها
-- LoadBalancer. هذا يطابق سلوك ensureSeedGateway دون حذف التاريخ.
UPDATE telecom_gateways SET enabled = false, updated_at = CURRENT_TIMESTAMP
 WHERE host IN ('192.168.11.2', '192.168.11.3') AND enabled = true;

-- ── 5. فهرس مطابقة داخل-الشبكة بعد تصحيح البادئات ──────────────────────
CREATE INDEX IF NOT EXISTS idx_dinstar_cdr_prefix_time
    ON dinstar_cdr (start_time DESC)
    WHERE start_time IS NOT NULL;
