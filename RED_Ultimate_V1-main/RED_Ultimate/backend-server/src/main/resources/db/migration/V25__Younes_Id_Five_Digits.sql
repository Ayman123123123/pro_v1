-- ═══════════════════════════════════════════════════════════════
-- V24 — معرّف يونس: الانتقال إلى خمسة أرقام
-- ═══════════════════════════════════════════════════════════════
--
-- الصيغة السابقة: YNS-XXXX-XXXX (أبجدية 32 رمزًا، ‎32^8 ≈ 1.1×10^12).
-- الصيغة الجديدة: 10001..99999 (خمسة أرقام، 89,999 معرّفًا).
-- القيمة 10000 محجوزة لمُرسِل رسائل النظام (RedIdGenerator.SYSTEM_ID)
-- ولا تُخصَّص لأي مستخدم، وإلا نُسبت رسائل الخادم إليه.
--
-- ## لماذا الترحيل لا إعادة التوليد العشوائي
--
-- المعرّف يظهر في `groups.created_by_red_id` وفي سجلات المكالمات وفي
-- جهات الاتصال المحفوظة على أجهزة المستخدمين. إعادة توليد عشوائية
-- تقطع هذه الروابط بصمت. الترحيل هنا **يحفظ الخريطة** في جدول
-- `red_id_migration_map` فيبقى أثر التحويل قابلًا للتدقيق والرجوع.
--
-- ## القيد الحرج
--
-- الفضاء الجديد 90,000 فقط. إن تجاوز عدد المستخدمين ذلك تفشل الهجرة
-- صراحةً بدل أن تُنتج معرّفات مكررة أو مبتورة.

-- ── 1. التحقق من السعة قبل أي تعديل ─────────────────────────────
DO $$
DECLARE
    user_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO user_count FROM users;
    IF user_count > 90000 THEN
        RAISE EXCEPTION
            'تعذّر الترحيل: % مستخدمًا يتجاوز سعة الصيغة الخماسية (90000)',
            user_count;
    END IF;
END $$;

-- ── 2. جدول خريطة التحويل (للتدقيق والرجوع) ─────────────────────
CREATE TABLE IF NOT EXISTS red_id_migration_map (
    user_id      UUID PRIMARY KEY,
    old_red_id   VARCHAR(32) NOT NULL,
    new_red_id   VARCHAR(5)  NOT NULL,
    migrated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_red_id_map_new UNIQUE (new_red_id)
);

COMMENT ON TABLE red_id_migration_map IS
    'خريطة تحويل معرّف يونس من YNS-XXXX-XXXX إلى خمسة أرقام (V24)';

-- ── 3. تخصيص المعرّفات الجديدة ──────────────────────────────────
-- الترتيب عشوائي (random()) لا حسب تاريخ التسجيل: الترتيب التسلسلي
-- يكشف أقدمية الحساب وحجم القاعدة، وهي بيانات لا داعي لتسريبها.
-- التخصيص من مجموعة الأرقام الحرة كاملةً يضمن عدم التصادم بالبناء
-- بدل الاعتماد على إعادة المحاولة.
INSERT INTO red_id_migration_map (user_id, old_red_id, new_red_id)
SELECT
    u.id,
    u.red_id,
    LPAD(candidate.n::TEXT, 5, '0')
FROM (
    SELECT id, red_id, ROW_NUMBER() OVER (ORDER BY random()) AS rn
    FROM users
    WHERE red_id !~ '^[1-9][0-9]{4}$'   -- ما لم يُحوَّل بعد
) u
JOIN (
    SELECT n, ROW_NUMBER() OVER (ORDER BY random()) AS rn
    FROM generate_series(10001, 99999) AS n
    WHERE n::TEXT NOT IN (SELECT red_id FROM users WHERE red_id ~ '^[1-9][0-9]{4}$')
) candidate ON candidate.rn = u.rn
ON CONFLICT (user_id) DO NOTHING;

-- ── 4. تطبيق التحويل على جدول المستخدمين ────────────────────────
UPDATE users u
SET red_id = m.new_red_id
FROM red_id_migration_map m
WHERE u.id = m.user_id AND u.red_id = m.old_red_id;

-- ── 5. تحديث المراجع في الجداول الأخرى ──────────────────────────
-- بلا هذه الخطوة تبقى المجموعات تشير إلى معرّف لم يعد موجودًا،
-- فيظهر منشئ المجموعة «غير معروف» في التطبيق.
UPDATE groups g
SET created_by_red_id = m.new_red_id
FROM red_id_migration_map m
WHERE g.created_by_red_id = m.old_red_id;

-- ── 6. فرض الصيغة على مستوى المخطط ──────────────────────────────
-- القيد يمنع إدخال صيغة قديمة مستقبلًا: التحقق في الشيفرة وحده
-- يُلتفّ عليه بأي إدخال مباشر أو خدمة جديدة تنسى التحقق.
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_red_id_format;
ALTER TABLE users
    ADD CONSTRAINT ck_users_red_id_format
    CHECK (red_id ~ '^[1-9][0-9]{4}$');

-- تضييق العمود: 32 حرفًا لم تعد مبرَّرة لخمسة أرقام
ALTER TABLE users ALTER COLUMN red_id TYPE VARCHAR(5);
ALTER TABLE groups ALTER COLUMN created_by_red_id TYPE VARCHAR(5);

-- ── 7. الفهارس ──────────────────────────────────────────────────
-- فهرس البادئة (varchar_pattern_ops) كان لبحث LIKE على صيغة طويلة.
-- مع خمسة أرقام يكفي فهرس المساواة، وهو أصغر وأسرع.
DROP INDEX IF EXISTS idx_users_red_id_prefix;
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_red_id ON users (red_id);

COMMENT ON COLUMN users.red_id IS
    'معرّف يونس: خمسة أرقام 10001-99999 (10000 محجوز للنظام). معرّف عرض عام لا سرّ — '
    'الفضاء قابل للتعداد الكامل فالحماية بتحديد المعدل لا بالمعرّف.';
