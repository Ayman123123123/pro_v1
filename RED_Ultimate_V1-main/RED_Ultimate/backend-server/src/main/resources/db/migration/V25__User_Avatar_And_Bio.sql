-- V25: إضافة عمود avatar_url و bio لجدول users — دعم شاشة البروفايل
-- avatar_url: مفتاح الوسائط المشفّر (objectKey) للصورة المعروضة في الدردشات والقوائم
-- bio: نص تعريفي قصير يعرضه المستخدم في برفايله (اختياري)

ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio VARCHAR(280);
