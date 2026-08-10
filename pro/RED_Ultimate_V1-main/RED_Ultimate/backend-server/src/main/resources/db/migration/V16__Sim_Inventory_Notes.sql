-- جرد شرائح SIM: ملاحظات إدارية اختيارية على كل منفذ بوابة.
ALTER TABLE gateway_port_snapshots ADD COLUMN IF NOT EXISTS note VARCHAR(200);
