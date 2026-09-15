-- V54__Drop_Leftover_System_Analytics_Calls_Pstn.sql
-- ═══════════════════════════════════════════════════════════════════════
-- إصلاح إقلاع 2026-09-15: عمود `system_analytics.calls_pstn` بقايا من النطاق
-- الملغي (Phase 8 / V52) — لا يذكره أي كود (Kotlin/SQL/TS) ولا أي هجرة تنشئه
-- (وُلد عبر ddl-auto في نسخة قديمة)، لكنه NOT NULL بلا DEFAULT، فيفشل كل
-- INSERT من كيان `SystemAnalytics` (الذي أُسقط منه الحقل) ويفشل إحماء
-- `DashboardDataScheduler` كل إقلاع. الإسقاط آمن: لا قارئ ولا كاتب ولا VIEW.
-- ═══════════════════════════════════════════════════════════════════════

ALTER TABLE IF EXISTS system_analytics DROP COLUMN IF EXISTS calls_pstn;
