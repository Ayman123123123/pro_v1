-- كانت هذه الهجرة V30 في فرع main، وV30 آخر (فهارس الأداء السيادية)
-- في هذا الفرع؛ فلمّا التقى الفرعان صار للإصدار الواحد ملفّان، وFlyway
-- يرفض الإقلاع عند تكرار الإصدار فيسقط الخادم كلّه عند بدء التشغيل.
-- رُقّيت هذه إلى V31 لأنها الأحدث زمنيًّا: أما V30 الأخرى فأقدم وقد
-- تكون طُبّقت في بيئات قائمة، وتغيير رقم هجرة مطبَّقة يفسد سجل Flyway.
-- طلبات الصداقة: إتاحة الإلغاء مع الاحتفاظ بسجل قرار واضح.
ALTER TABLE contact_requests
    DROP CONSTRAINT IF EXISTS contact_requests_status_check;

ALTER TABLE contact_requests
    ADD CONSTRAINT contact_requests_status_check
    CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELED'));

CREATE INDEX IF NOT EXISTS idx_contact_requests_requester_status
    ON contact_requests(requester_id, status, created_at DESC);
