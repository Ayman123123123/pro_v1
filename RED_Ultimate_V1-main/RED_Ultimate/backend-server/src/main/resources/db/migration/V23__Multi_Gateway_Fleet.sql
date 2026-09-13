-- ═══════════════════════════════════════════════════════════════════
-- أسطول بوابات DINSTAR — دعم عدة أجهزة UC2000-VE-8G / UC2000-VE-8T
-- ═══════════════════════════════════════════════════════════════════
-- جدول telecom_gateways كان موجودًا منذ V12 ويقبل عدة صفوف، لكن الشيفرة
-- كانت تتعامل مع بوابة واحدة فقط عبر red.dinstar.ip. هذه الهجرة تضيف
-- الحقول التي يحتاجها تشغيل أسطول حقيقي.

-- عدد المنافذ يختلف بين الطرازات (4 أو 8)، وكان مُثبّتًا في الشيفرة على 8.
ALTER TABLE telecom_gateways ADD COLUMN IF NOT EXISTS port_count INTEGER NOT NULL DEFAULT 8
    CHECK (port_count BETWEEN 1 AND 32);

-- أولوية التوجيه: الأقل رقمًا يُجرَّب أولًا عند تساوي بقية العوامل.
ALTER TABLE telecom_gateways ADD COLUMN IF NOT EXISTS routing_priority INTEGER NOT NULL DEFAULT 100;

-- نتيجة آخر فحص صحّة، لتفادي إرسال مكالمة إلى بوابة ساقطة.
ALTER TABLE telecom_gateways ADD COLUMN IF NOT EXISTS health_state VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN'
    CHECK (health_state IN ('UNKNOWN','ONLINE','DEGRADED','OFFLINE'));
ALTER TABLE telecom_gateways ADD COLUMN IF NOT EXISTS last_error TEXT;
ALTER TABLE telecom_gateways ADD COLUMN IF NOT EXISTS consecutive_failures INTEGER NOT NULL DEFAULT 0;

-- اسم نظير PJSIP في Asterisk المقابل لهذه البوابة. بدونه لا يعرف الخادم
-- عبر أي trunk يُخرج المكالمة عند وجود أكثر من جهاز.
ALTER TABLE telecom_gateways ADD COLUMN IF NOT EXISTS pjsip_endpoint VARCHAR(120);

-- موقع الجهاز الفعلي — يفيد عند توزيع الأجهزة على فروع.
ALTER TABLE telecom_gateways ADD COLUMN IF NOT EXISTS site_label VARCHAR(120);

-- التعرّف التلقائي: كيف اكتُشف هذا الجهاز ومتى.
ALTER TABLE telecom_gateways ADD COLUMN IF NOT EXISTS discovery_method VARCHAR(30) NOT NULL DEFAULT 'MANUAL'
    CHECK (discovery_method IN ('MANUAL','SUBNET_SCAN','CONFIG_SEED','MDNS'));
ALTER TABLE telecom_gateways ADD COLUMN IF NOT EXISTS discovered_at TIMESTAMP;

-- الرقم التسلسلي وإصدار البرنامج الثابت كما يقرأهما get_status.
-- المفتاح الفعلي لتمييز جهاز عن آخر حتى لو تغيّر عنوانه بـ DHCP.
ALTER TABLE telecom_gateways ADD COLUMN IF NOT EXISTS serial_number VARCHAR(80);
ALTER TABLE telecom_gateways ADD COLUMN IF NOT EXISTS firmware_version VARCHAR(80);
ALTER TABLE telecom_gateways ADD COLUMN IF NOT EXISTS mac_address VARCHAR(32);

CREATE UNIQUE INDEX IF NOT EXISTS idx_gateways_serial
    ON telecom_gateways(serial_number) WHERE serial_number IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_gateways_routing
    ON telecom_gateways(enabled, health_state, routing_priority);

-- ── لقطات المنافذ: الحقول التي تصحّح تفسير الإشارة ──────────────────
-- كان signal_percent وحده، وكان يُحسب من قراءة خام مغلوطة (99 ⇒ 100%).
-- نضيف dBm الفعلي وعلامة الصلاحية حتى يستطيع الموزّع استبعاد المنافذ
-- التي لا تحمل شبكة أصلًا.
ALTER TABLE gateway_port_snapshots ADD COLUMN IF NOT EXISTS signal_dbm INTEGER
    CHECK (signal_dbm IS NULL OR signal_dbm BETWEEN -140 AND 0);
ALTER TABLE gateway_port_snapshots ADD COLUMN IF NOT EXISTS signal_usable BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE gateway_port_snapshots ADD COLUMN IF NOT EXISTS operator_name VARCHAR(60);

-- signal_percent صار يقبل NULL: «لا قياس» تختلف عن «صفر بالمئة».
ALTER TABLE gateway_port_snapshots ALTER COLUMN signal_percent DROP NOT NULL;

-- ── توجيه المكالمات عبر الأسطول ─────────────────────────────────────
-- سجل اختيار المنفذ لكل مكالمة: يمكّن من تدقيق سبب اختيار بوابة بعينها
-- ومن حساب معدلات النجاح لكل جهاز.
CREATE TABLE IF NOT EXISTS gateway_route_decisions (
    id UUID PRIMARY KEY,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE SET NULL,
    port_index INTEGER,
    destination_prefix VARCHAR(8),
    matched_operator VARCHAR(40),
    score DOUBLE PRECISION,
    reason VARCHAR(200),
    outcome VARCHAR(20) NOT NULL DEFAULT 'SELECTED'
        CHECK (outcome IN ('SELECTED','REJECTED_NO_SIGNAL','REJECTED_BUSY','REJECTED_OFFLINE','FALLBACK')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_route_decisions_created ON gateway_route_decisions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_route_decisions_gateway ON gateway_route_decisions(gateway_id, created_at DESC);
