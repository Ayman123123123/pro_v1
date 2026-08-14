-- V29: المجموعات الحية تُكتب في MongoDB (`GroupService`)، لا في جدول Postgres `groups`.
-- مفاتيح pinning/edit/disappear كانت REFERENCES groups(id) فتفشل كل عملية تثبيت
-- لمجموعة حقيقية بـ FK violation رغم أن الرسالة موجودة.
-- نُسقط قيود FK فقط. الأعمدة تبقى للمراجع المنطقية (UUID v7 من Mongo).

DO $$
DECLARE r record;
BEGIN
    FOR r IN
        SELECT c.conrelid::regclass AS tbl, c.conname
        FROM pg_constraint c
        WHERE c.contype = 'f'
          AND c.confrelid = 'public.groups'::regclass
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT IF EXISTS %I', r.tbl, r.conname);
    END LOOP;
END $$;
