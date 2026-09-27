-- V70: إسقاط نهائي لجداول بوابات DINSTAR وPSTN — نطاق ملغى (2026-09-27)
--
-- السياق: أُلغي نطاق بوابة DINSTAR/PSTN/SMS-Yemen كلياً في المرحلة 8
-- (commit 543c645c «Phase 8: cancel Yemen-phone/PSTN/Dinstar scope end-to-end»)
-- وحُذف كل الكود النشط التابع له. بقيت الجداول يتيمة في قاعدة البيانات
-- لأن هجرات Flyway القديمة (V2/V23/V25/V26_1/V34/V38/V42/V43/V47/V48)
-- لا يجوز إعادة تسميتها أو حذفها دون كسر checksum للمثبتات القائمة.
--
-- هذه الهجرة تسقط كل جداول البوابات بسلامة، وتُترك الهجرات التاريخية كما هي.
-- ملاحظة: sms_templates وscheduled_sms أُنشئت في مسار Dinstar ولم يعد لهما
-- أي مستهلك في الكود (تحقّق 2026-09-27) فتُسقط مع بقية المجموعة.

DROP TABLE IF EXISTS dinstar_sms_scheduled;
DROP TABLE IF EXISTS dinstar_sms_templates;
DROP TABLE IF EXISTS dinstar_sms_log;
DROP TABLE IF EXISTS dinstar_ussd_log;
DROP TABLE IF EXISTS dinstar_port_control;
DROP TABLE IF EXISTS dinstar_daily_stats;
DROP TABLE IF EXISTS dinstar_device_status;
DROP TABLE IF EXISTS dinstar_config_changes;
DROP TABLE IF EXISTS dinstar_alerts;
DROP TABLE IF EXISTS dinstar_logs;
DROP TABLE IF EXISTS dinstar_cdr;
DROP TABLE IF EXISTS dinstar_ports;
DROP TABLE IF EXISTS dinstar_config;
DROP TABLE IF EXISTS scheduled_sms;
DROP TABLE IF EXISTS sms_templates;
DROP TABLE IF EXISTS pstn_active_calls;
DROP TABLE IF EXISTS gateway_route_decisions;
DROP TABLE IF EXISTS gateway_health_history;
DROP TABLE IF EXISTS gateway_config_backups;
DROP TABLE IF EXISTS gateway_port_reservations;
DROP TABLE IF EXISTS port_control_state;
