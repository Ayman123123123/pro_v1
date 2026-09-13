-- ══════════════════════════════════════════════════════════════════
-- V51: خصوصية مؤشر الكتابة — typing_indicators في user_privacy_settings
-- Typing privacy — sender controls who sees their typing indicator
-- ══════════════════════════════════════════════════════════════════
-- آمنة: عمود جديد بقيمة افتراضية EVERYONE (لا يكسر الصفوف القائمة)،
-- مع قيد تحقق مستقل حتى لا نمس القيد الأصلي privacy_level_check.
-- ══════════════════════════════════════════════════════════════════

ALTER TABLE user_privacy_settings
    ADD COLUMN IF NOT EXISTS typing_indicators VARCHAR(20) NOT NULL DEFAULT 'EVERYONE';

ALTER TABLE user_privacy_settings DROP CONSTRAINT IF EXISTS privacy_typing_check;
ALTER TABLE user_privacy_settings ADD CONSTRAINT privacy_typing_check CHECK (
    typing_indicators IN ('EVERYONE','CONTACTS','CONTACTS_EXCEPT','ONLY_SHARE_WITH','NOBODY')
);

COMMENT ON COLUMN user_privacy_settings.typing_indicators IS 'V51: من يرى مؤشر الكتابة — يفرضه RedMasterHandler قبل بث TYPING';
