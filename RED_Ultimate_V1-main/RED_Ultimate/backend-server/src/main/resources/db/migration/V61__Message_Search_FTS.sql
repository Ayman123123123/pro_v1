-- V61__Message_Search_FTS.sql
-- ═══════════════════════════════════════════════════════════════════════
-- فهارس البحث النصي الكامل (Full-Text Search) للرسائل - 2026-09-17
-- PostgreSQL tsvector + GIN indexes للبحث السريع متعدد اللغات
-- متوافق مع: العربية، الإنجليزية، واللغات الأخرى
-- ═══════════════════════════════════════════════════════════════════════

-- ── 1) تفعيل إضافات البحث النصي ──
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- ── 2) تكوين البحث النصي للعربية (Arabic Text Search Configuration) ──
-- إنشاء قاموس عربي مخصص يتعامل مع الهمزات والتشكيل
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'arabic_custom') THEN
    CREATE TEXT SEARCH CONFIGURATION arabic_custom (COPY = simple);
    ALTER TEXT SEARCH CONFIGURATION arabic_custom
      ALTER MAPPING FOR asciiword, asciihword, hword_asciipart, word, hword, hword_part
      WITH unaccent, arabic_stem;
  END IF;
END $$;

-- ── 3) دالة تطبيع النص العربي (تستخدم في الفهرسة والاستعلام) ──
CREATE OR REPLACE FUNCTION red_normalize_arabic(text_in TEXT) RETURNS TEXT AS $$
DECLARE
  result TEXT := text_in;
BEGIN
  -- إزالة التشكيل (الحركات: فتحة، ضمة، كسرة، سكون، شدة، مد)
  result := regexp_replace(result, '[\u064B-\u0652\u0670\u0640]', '', 'g');
  -- توحيد أشكال الألف: آ، أ، إ، ٱ → ا
  result := regexp_replace(result, '[\u0622\u0623\u0625\u0671]', '\u0627', 'g');
  -- توحيد ياء: ى → ي
  result := regexp_replace(result, '\u0649', '\u064A', 'g');
  -- توحيد تاء مربوطة: ة → ه
  result := regexp_replace(result, '\u0629', '\u0647', 'g');
  RETURN result;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- ── 4) دالة إنشاء tsvector من نص مشفر/مخزن ──
-- تفترض أن النص يتم فك تشفيره قبل تمريره (على مستوى التطبيق)
CREATE OR REPLACE FUNCTION red_message_to_tsvector(content TEXT, lang TEXT DEFAULT 'arabic_custom') RETURNS tsvector AS $$
BEGIN
  IF content IS NULL OR content = '' THEN
    RETURN ''::tsvector;
  END IF;
  RETURN to_tsvector(lang, red_normalize_arabic(content));
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- ── 5) عمود tsvector محسوب (Generated Column) على جدول الرسائل ──
-- نضيف عمود search_vector للرسائل (يتطلب أن يكون المحتوى متاحاً كنص عادي)
-- ملاحظة: في الإنتاج، المحتوى مشفر - الفهرسة تتم على مستوى التطبيق أو عبر trigger
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='messages') THEN
    -- إضافة عمود tsvector إذا لم يكن موجوداً
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='messages' AND column_name='search_vector') THEN
      ALTER TABLE messages ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
        red_message_to_tsvector(
          COALESCE(
            NULLIF(decrypt_content(payload, 'search'), ''),
            ''
          ), 
          'arabic_custom'
        )
      ) STORED;
    END IF;
    
    -- فهرس GIN على search_vector للبحث السريع
    CREATE INDEX IF NOT EXISTS idx_messages_search_vector ON messages USING GIN(search_vector);
    
    -- فهارس مركبة للفلترة مع البحث
    CREATE INDEX IF NOT EXISTS idx_messages_search_conv_vector ON messages(conversation_id, search_vector) 
      WHERE search_vector IS NOT NULL;
    CREATE INDEX IF NOT EXISTS idx_messages_search_sender_vector ON messages(sender_id, search_vector)
      WHERE search_vector IS NOT NULL;
    CREATE INDEX IF NOT EXISTS idx_messages_search_date_vector ON messages(created_at DESC, search_vector)
      WHERE search_vector IS NOT NULL;
  END IF;
END $$;

-- ── 6) جدول منفصل للبحث النصي (FTS Table) - للأداء الأفضل مع المحتوى المشفر ──
-- هذا الجدول يُملأ من التطبيق بعد فك التشفير (نفس نمط Room FTS5)
CREATE TABLE IF NOT EXISTS message_search_fts (
  message_id      UUID PRIMARY KEY REFERENCES messages(id) ON DELETE CASCADE,
  conversation_id UUID NOT NULL,
  sender_id       UUID NOT NULL,
  content         TEXT NOT NULL,           -- النص المفكك (للقطع/snippet)
  content_vector  tsvector NOT NULL,       -- مُفهرس GIN
  created_at      TIMESTAMPTZ NOT NULL,
  message_type    TEXT NOT NULL
);

-- فهارس GIN على content_vector
CREATE INDEX IF NOT EXISTS idx_message_search_fts_vector ON message_search_fts USING GIN(content_vector);

-- فهارس للفلترة السريعة قبل البحث
CREATE INDEX IF NOT EXISTS idx_message_search_fts_conv ON message_search_fts(conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_message_search_fts_sender ON message_search_fts(sender_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_message_search_fts_type ON message_search_fts(message_type, created_at DESC);

-- ── 7) Trigger لتحديث message_search_fts تلقائياً (اختياري - يعتمد على نمط التطبيق) ──
-- إذا كان التطبيق يكتب للجدولين، هذا الـ trigger يضمن المزامنة
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='messages') THEN
    DROP TRIGGER IF EXISTS trigger_sync_message_fts ON messages;
    CREATE TRIGGER trigger_sync_message_fts
    AFTER INSERT OR UPDATE OR DELETE ON messages
    FOR EACH ROW EXECUTE FUNCTION red_sync_message_fts();
  END IF;
END $$;

-- ── 8) دالة المزامنة للـ Trigger ──
CREATE OR REPLACE FUNCTION red_sync_message_fts() RETURNS TRIGGER AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    DELETE FROM message_search_fts WHERE message_id = OLD.id;
    RETURN OLD;
  ELSIF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
    -- التطبيق مسؤول عن فك التشفير وإدراج الصف في message_search_fts
    -- هذا الـ trigger لا يفعل شيئاً هنا - يتم الإدراج من التطبيق
    RETURN NEW;
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- ── 9) دوال بحث محسنة ──
-- بحث في محادثة محددة
CREATE OR REPLACE FUNCTION red_search_messages_in_conversation(
  conv_id UUID, 
  query_text TEXT, 
  limit_count INT DEFAULT 50,
  lang TEXT DEFAULT 'arabic_custom'
) RETURNS TABLE(
  message_id UUID,
  conversation_id UUID,
  sender_id UUID,
  snippet TEXT,
  rank REAL,
  created_at TIMESTAMPTZ
) AS $$
DECLARE
  tsquery_val tsquery;
BEGIN
  -- تحويل الاستعلام إلى tsquery مع تطبيع عربي
  tsquery_val := plainto_tsquery(lang, red_normalize_arabic(query_text));
  
  RETURN QUERY
  SELECT 
    f.message_id,
    f.conversation_id,
    f.sender_id,
    -- استخراج مقتطف (snippet) حول المطابقة
    ts_headline(lang, f.content, tsquery_val, 'MaxFragments=3,MaxWords=30,MinWords=5,StartSel=<mark>,StopSel=</mark>') AS snippet,
    ts_rank_cd(f.content_vector, tsquery_val) AS rank,
    f.created_at
  FROM message_search_fts f
  WHERE f.conversation_id = conv_id
    AND f.content_vector @@ tsquery_val
  ORDER BY rank DESC, f.created_at DESC
  LIMIT limit_count;
END;
$$ LANGUAGE plpgsql STABLE;

-- بحث عام عبر كل رسائل المستخدم
CREATE OR REPLACE FUNCTION red_search_messages_global(
  user_id UUID,
  query_text TEXT,
  limit_count INT DEFAULT 50,
  lang TEXT DEFAULT 'arabic_custom'
) RETURNS TABLE(
  message_id UUID,
  conversation_id UUID,
  sender_id UUID,
  snippet TEXT,
  rank REAL,
  created_at TIMESTAMPTZ
) AS $$
DECLARE
  tsquery_val tsquery;
BEGIN
  tsquery_val := plainto_tsquery(lang, red_normalize_arabic(query_text));
  
  RETURN QUERY
  SELECT 
    f.message_id,
    f.conversation_id,
    f.sender_id,
    ts_headline(lang, f.content, tsquery_val, 'MaxFragments=3,MaxWords=30,MinWords=5,StartSel=<mark>,StopSel=</mark>') AS snippet,
    ts_rank_cd(f.content_vector, tsquery_val) AS rank,
    f.created_at
  FROM message_search_fts f
  JOIN conversation_participants cp ON cp.conversation_id = f.conversation_id
  WHERE cp.user_id = user_id
    AND f.content_vector @@ tsquery_val
  ORDER BY rank DESC, f.created_at DESC
  LIMIT limit_count;
END;
$$ LANGUAGE plpgsql STABLE;

-- ── 10) فهارس Trigram للبحث التقريبي (Fuzzy Search) ──
-- للبحث بأخطاء إملائية أو بادئات جزئية
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='message_search_fts') THEN
    CREATE INDEX IF NOT EXISTS idx_message_search_fts_content_trgm ON message_search_fts USING GIN(content gin_trgm_ops);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='messages') THEN
    CREATE INDEX IF NOT EXISTS idx_messages_content_trgm ON messages USING GIN(decrypt_content(payload, 'search') gin_trgm_ops)
      WHERE decrypt_content(payload, 'search') IS NOT NULL;
  END IF;
END $$;

-- ── 11) دالة بحث هجينة (FTS + Trigram للاقتراح/الإكمال التلقائي) ──
CREATE OR REPLACE FUNCTION red_search_messages_suggest(
  user_id UUID,
  prefix TEXT,
  limit_count INT DEFAULT 10
) RETURNS TABLE(
  message_id UUID,
  conversation_id UUID,
  sender_id UUID,
  snippet TEXT,
  created_at TIMESTAMPTZ
) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    f.message_id,
    f.conversation_id,
    f.sender_id,
    LEFT(f.content, 100) AS snippet,
    f.created_at
  FROM message_search_fts f
  JOIN conversation_participants cp ON cp.conversation_id = f.conversation_id
  WHERE cp.user_id = user_id
    AND f.content % red_normalize_arabic(prefix)  -- trigram similarity
  ORDER BY similarity(f.content, red_normalize_arabic(prefix)) DESC
  LIMIT limit_count;
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON TABLE message_search_fts IS 'جدول بحث نصي كامل للرسائل - يُملأ من التطبيق بعد فك التشفير';
COMMENT ON FUNCTION red_search_messages_in_conversation IS 'بحث FTS في محادثة واحدة مع ترتيب relevance';
COMMENT ON FUNCTION red_search_messages_global IS 'بحث FTS شامل عبر كل محادثات المستخدم';
COMMENT ON FUNCTION red_search_messages_suggest IS 'اقتراحات بحث تقريبية (Trigram) للإكمال التلقائي';