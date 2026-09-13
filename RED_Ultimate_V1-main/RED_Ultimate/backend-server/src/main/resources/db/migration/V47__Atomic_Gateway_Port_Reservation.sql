-- ═══════════════════════════════════════════════════════════════════════════
-- V47 — حجز المنفذ يصبح ذريًّا فعلًا + تحرير دقيق بالمكالمة
-- ═══════════════════════════════════════════════════════════════════════════
-- المشكلة التي يعالجها هذا الترحيل:
--
-- `PersistentReservationService.tryReservePort` كُتب على أساس فهرس اسمه
-- `uq_gateway_port_active_reservation` يمنع حجزين حيَّين لنفس (بوابة، منفذ).
-- ذلك الفهرس **غير موجود في أي ترحيل**. الموجود فعلًا هو
-- `uq_gateway_port_reservation_call` على (gateway_id, port_index, call_id)،
-- و`call_id` فريد لكل مكالمة ⇒ الـINSERT لا يتعارض أبدًا ⇒ الدالة لا تُرجع
-- `false` من قاعدة البيانات قطّ، فالحجز «الذرّي» لم يكن يمنع شيئًا:
-- مكالمتان متزامنتان تحصلان على المنفذ نفسه.
--
-- لماذا لم يُكتب الفهرس الصحيح؟ لأن `WHERE expires_at > NOW()` مستحيل في
-- فهرس PostgreSQL: predicate الفهرس يجب أن يكون IMMUTABLE و`NOW()` ليست
-- كذلك. الحل الصحيح ليس فهرسًا شرطيًا بل فهرس فريد بسيط على
-- (gateway_id, port_index) مع كتابة «استيلاء» ذرّية تستبدل الصف المنتهي:
--   INSERT … ON CONFLICT (gateway_id, port_index)
--   DO UPDATE … WHERE gateway_port_reservations.expires_at <= NOW()
-- فإن كان الحجز حيًّا لا يتغيّر شيء ويعود عدد الصفوف صفرًا = مرفوض،
-- وإن كان منتهيًا يُستولى عليه في نفس العبارة بلا نافذة سباق.
--
-- NULLS NOT DISTINCT: `gateway_id` قد يكون NULL (نشر ببوابة واحدة). بلا
-- هذه الصيغة تسمح PostgreSQL بعدد لا نهائي من صفوف NULL على المنفذ نفسه
-- فيسقط الضمان في أكثر الحالات شيوعًا. الصيغة مدعومة من PostgreSQL 15+،
-- والنشر هنا على 16.9.

-- 1) إزالة التعارضات القائمة قبل فرض القيد: نُبقي الأحدث انتهاءً لكل
--    (بوابة، منفذ) ونحذف الباقي. هذا آمن لأن الصفوف حجوزات مؤقتة.
DELETE FROM gateway_port_reservations r
WHERE EXISTS (
    SELECT 1 FROM gateway_port_reservations k
    WHERE k.gateway_id IS NOT DISTINCT FROM r.gateway_id
      AND k.port_index = r.port_index
      AND (k.expires_at, k.id) > (r.expires_at, r.id)
);

-- 2) الفهرس الفريد الحقيقي — حجز حيّ واحد لكل منفذ.
CREATE UNIQUE INDEX IF NOT EXISTS uq_gateway_port_active_reservation
    ON gateway_port_reservations (gateway_id, port_index) NULLS NOT DISTINCT;

-- 3) الفهرس القديم صار زائدًا (أوسع من الجديد) وضارًّا: تعارض عليه يرفع
--    استثناءً بدل أن يسلك مسار ON CONFLICT المُستنتَج، فيفشل الاستيلاء
--    المشروع على حجز منتهٍ.
DROP INDEX IF EXISTS uq_gateway_port_reservation_call;

COMMENT ON INDEX uq_gateway_port_active_reservation IS
    'حجز واحد لكل (بوابة، منفذ). الانتهاء يُدار بالاستيلاء الذرّي في tryReservePort لا بشرط فهرس (NOW() ليست IMMUTABLE).';
