-- طلبات الصداقة: إتاحة الإلغاء مع الاحتفاظ بسجل قرار واضح.
ALTER TABLE contact_requests
    DROP CONSTRAINT IF EXISTS contact_requests_status_check;

ALTER TABLE contact_requests
    ADD CONSTRAINT contact_requests_status_check
    CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELED'));

CREATE INDEX IF NOT EXISTS idx_contact_requests_requester_status
    ON contact_requests(requester_id, status, created_at DESC);
