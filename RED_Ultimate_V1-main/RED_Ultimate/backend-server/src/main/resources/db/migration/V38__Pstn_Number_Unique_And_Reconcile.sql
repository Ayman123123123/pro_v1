-- ═══════════════════════════════════════════════════════════════════════════
-- V35: PSTN number global uniqueness + reconcile transparency
-- Prevents duplicate CLIP / inbound ambiguity: the same MSISDN must not
-- be bound to two different RED accounts (would route inbound to wrong user).
-- Partial index allows multiple unbound rows (pstn_number IS NULL).
-- ═══════════════════════════════════════════════════════════════════════════

-- Global unique number — two accounts must never share the same MSISDN.
-- IF NOT EXISTS keeps the migration idempotent on rerun.
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_pstn_number
    ON users(pstn_number) WHERE pstn_number IS NOT NULL;

COMMENT ON INDEX ux_users_pstn_number IS 'Guarantees 1:1 MSISDN→account; prevents duplicate CLIP/inbound routing (V35 smart SIM→port discovery)';

-- Helper view for reconcile transparency: joins live snapshots with bindings.
-- Used by DinstarControl and reconcile endpoint for audit; shows per-port
-- live state vs bound RED account. No data change, read-only.
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

COMMENT ON VIEW v_pstn_reconcile IS 'Live port snapshot vs permanent SIM binding (V35) — used by /api/admin/dinstar/bindings/reconcile for transparency';
