-- V49: جداول سجلات SMS التي يكتبها DinstarApiService (saveSmsResult/saveSmsDeliveryStatus/saveIncomingSms)
-- هذه الجداول لم تُعرّف في أي هجرة سابقة؛ الإدخالات كانت تفشل بصمت داخل try/catch.
-- آمنة تماماً: إنشاء فقط مع IF NOT EXISTS، بلا تعديل لأي جدول قائم.

CREATE TABLE IF NOT EXISTS dinstar_sms_result (
    id SERIAL PRIMARY KEY,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    user_id TEXT,
    message_id VARCHAR(100),
    status VARCHAR(30),
    status_code VARCHAR(30),
    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sms_result_gateway ON dinstar_sms_result(gateway_id);
CREATE INDEX IF NOT EXISTS idx_sms_result_user ON dinstar_sms_result(user_id);

CREATE TABLE IF NOT EXISTS dinstar_sms_delivery_status (
    id SERIAL PRIMARY KEY,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    phone_number VARCHAR(50),
    status VARCHAR(30),
    status_code VARCHAR(30),
    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sms_delivery_gateway ON dinstar_sms_delivery_status(gateway_id);
CREATE INDEX IF NOT EXISTS idx_sms_delivery_phone ON dinstar_sms_delivery_status(phone_number);

CREATE TABLE IF NOT EXISTS dinstar_incoming_sms (
    id SERIAL PRIMARY KEY,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    phone_number VARCHAR(50),
    text TEXT,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_incoming_sms_gateway ON dinstar_incoming_sms(gateway_id);
CREATE INDEX IF NOT EXISTS idx_incoming_sms_phone ON dinstar_incoming_sms(phone_number);
