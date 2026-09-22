-- V61__Message_Search_FTS.sql
-- ═══════════════════════════════════════════════════════════════════════
-- فهارس البحث النصي الكامل (Full-Text Search) - 2026-09-17
-- V64-revised: message_search_fts بدون FK لجدول messages (الرسائل في MongoDB)
-- ═══════════════════════════════════════════════════════════════════════

-- ── 1) تفعيل إضافات البحث النصي ──
CREATE EXTENSION IF NOT EXISTS pg_trgm;
DO $$ BEGIN
  CREATE EXTENSION IF NOT EXISTS unaccent;
EXCEPTION WHEN OTHERS THEN
  -- unaccent might not be available in all PG installations
  NULL;
END $$;

-- ── 2) تكوين البحث النصي للعربية ──
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'arabic_custom') THEN
    CREATE TEXT SEARCH CONFIGURATION arabic_custom (COPY = simple);
    -- Use simple dictionary for Arabic (no arabic_stem in standard PG)
    ALTER TEXT SEARCH CONFIGURATION arabic_custom
      ALTER MAPPING FOR asciiword, asciihword, hword_asciipart, word, hword, hword_part
      WITH simple;
  END IF;
END $$;

-- ── 3) دالة تطبيع النص العربي ──
CREATE OR REPLACE FUNCTION red_normalize_arabic(text_in TEXT) RETURNS TEXT AS $$
DECLARE
  result TEXT := text_in;
BEGIN
  -- إزالة التشكيل
  result := regexp_replace(result, '[\u064B-\u0652\u0670\u0640]', '', 'g');
  -- توحيد الألف
  result := regexp_replace(result, '[\u0622\u0623\u0625\u0671]', '\u0627', 'g');
  -- توحيد الياء
  result := regexp_replace(result, '\u0649', '\u064A', 'g');
  -- توحيد التاء المربوطة
  result := regexp_replace(result, '\u0629', '\u0647', 'g');
  RETURN result;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- ── 4) دالة tsvector ──
CREATE OR REPLACE FUNCTION red_message_to_tsvector(content TEXT, lang TEXT DEFAULT 'arabic_custom') RETURNS tsvector AS $$
BEGIN
  IF content IS NULL OR content = '' THEN
    RETURN ''::tsvector;
  END IF;
  RETURN to_tsvector(lang, red_normalize_arabic(content));
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- ── 5) جدول البحث النصي (بدون FK لـ messages - الرسائل في MongoDB) ──
-- V64 ينشئ الجدول بشكل صحيح؛ هنا نضمن الوجود فقط
CREATE TABLE IF NOT EXISTS message_search_fts (
    message_id      UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    sender_id       UUID NOT NULL,
    content         TEXT NOT NULL,
    content_vector  tsvector NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message_type    TEXT NOT NULL DEFAULT 'TEXT'
);

CREATE INDEX IF NOT EXISTS idx_message_search_fts_vector ON message_search_fts USING GIN(content_vector);
CREATE INDEX IF NOT EXISTS idx_message_search_fts_conv ON message_search_fts(conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_message_search_fts_sender ON message_search_fts(sender_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_message_search_fts_type ON message_search_fts(message_type, created_at DESC);

-- ── 6) فهارس trigram على جداول المستخدمين ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='username') THEN
    CREATE INDEX IF NOT EXISTS idx_users_username_trgm ON users USING gin (lower(username) gin_trgm_ops);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='full_name') THEN
    CREATE INDEX IF NOT EXISTS idx_users_fullname_trgm ON users USING gin (lower(full_name) gin_trgm_ops);
  END IF;
END $$;

-- ── 7) دالة بحث في الرسائل ──
CREATE OR REPLACE FUNCTION red_search_messages(
    p_query TEXT,
    p_conversation_id UUID DEFAULT NULL,
    p_limit INT DEFAULT 50
) RETURNS TABLE(
    message_id UUID,
    conversation_id UUID,
    sender_id UUID,
    snippet TEXT,
    rank REAL,
    created_at TIMESTAMPTZ
) AS $$
DECLARE
    tsq tsvector;
BEGIN
    tsq := red_message_to_tsvector(p_query);
    RETURN QUERY
    SELECT
        msf.message_id,
        msf.conversation_id,
        msf.sender_id,
        LEFT(msf.content, 200) AS snippet,
        ts_rank(msf.content_vector, plainto_tsquery('arabic_custom', red_normalize_arabic(p_query))) AS rank,
        msf.created_at
    FROM message_search_fts msf
    WHERE msf.content_vector @@ plainto_tsquery('arabic_custom', red_normalize_arabic(p_query))
      AND (p_conversation_id IS NULL OR msf.conversation_id = p_conversation_id)
    ORDER BY rank DESC
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON TABLE message_search_fts IS 'فهرس البحث النصي للرسائل (message_id يشير إلى MongoDB)';
COMMENT ON FUNCTION red_search_messages IS 'بحث نصي كامل في الرسائل مع دعم العربية';
