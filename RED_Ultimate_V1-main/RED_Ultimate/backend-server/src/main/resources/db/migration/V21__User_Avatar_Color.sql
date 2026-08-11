-- YOUNES Sovereign — User avatar color
-- يسمح للواجهات بعرض لون ثابت لكل مستخدم بدون صورة شخصية.
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_color VARCHAR(20);
