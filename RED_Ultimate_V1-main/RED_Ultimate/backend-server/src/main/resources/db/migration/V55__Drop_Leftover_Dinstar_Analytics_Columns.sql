-- V55__Drop_Leftover_Dinstar_Analytics_Columns.sql
-- ═══════════════════════════════════════════════════════════════════════
-- تتمة V54 (إصلاح إقلاع 2026-09-15): بعد إسقاط `calls_pstn` ظهرت بقايا ثانية
-- من النطاق الملغي (Phase 8 / V52) — أربعة أعمدة dinstar_* في `system_analytics`
-- بصيغة NOT NULL بلا DEFAULT، ولا يذكرها أي كود (Kotlin/SQL/TS) ولا أي هجرة
-- تنشئها (وُلدت عبر ddl-auto في نسخة قديمة)، فيفشل كل INSERT من كيان
-- `SystemAnalytics` ويفشل إحماء `DashboardDataScheduler` كل إقلاع.
-- تحقق: صفر مرجع في الشجرة + غياب من الكيان = إسقاط آمن.
-- ═══════════════════════════════════════════════════════════════════════

ALTER TABLE IF EXISTS system_analytics DROP COLUMN IF EXISTS dinstar_active_ports;
ALTER TABLE IF EXISTS system_analytics DROP COLUMN IF EXISTS dinstar_balance_remaining;
ALTER TABLE IF EXISTS system_analytics DROP COLUMN IF EXISTS dinstar_total_calls;
ALTER TABLE IF EXISTS system_analytics DROP COLUMN IF EXISTS dinstar_total_duration_seconds;
