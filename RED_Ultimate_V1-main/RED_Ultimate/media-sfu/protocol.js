'use strict';

/**
 * حدّ العقد بين الـ SFU والعميل.
 *
 * كان `server.js` يعيد `String(error.message)` حرفياً للعميل، فأي رسالة
 * داخلية من mediasoup (رقم عملية العامل، مسار، حالة نقل) تعبر الشبكة.
 * هذه الوحدة تحوّل كل فشل إلى رمز ثابت صغير، وتُبقي التفاصيل في سجل
 * الخادم فقط.
 *
 * القائمة أدناه مشتقة من كل `throw new Error(...)` في `server.js`،
 * وتُثبَّت باختبار في `protocol.test.js` حتى لا يسقط رمز جديد بالخطأ
 * إلى `REQUEST_FAILED` العام عند إضافة تحقق جديد.
 */

/** أخطاء المصادقة والتذكرة — يتعامل العميل معها بتجديد الرمز. */
const UNAUTHORIZED_MESSAGES = new Set([
  'Unauthorized',
  'Expired or invalid token',
  'Invalid token claims',
  'Ticket not bound to this room'
]);

/** مصرّح لكن غير مسموح — النشر/الاستهلاك بلا صلاحية. لا يُعاد المحاولة. */
const FORBIDDEN_MESSAGES = new Set([
  'Produce not permitted by ticket',
  'Consume not permitted by ticket',
  'Produce not permitted',
  'Consume not permitted'
]);

/** أخطاء طلب غير صالح — العميل أخطأ في الترتيب أو المعرّف أو تجاوز حدًّا. */
const INVALID_REQUEST_MESSAGES = new Set([
  'Join a room first',
  'Already joined',
  'Invalid roomId',
  'Invalid message format',
  'Message too large',
  'Transport not found',
  'Consumer not found',
  'Producer not found',
  'Cannot consume producer',
  'Too many transports',
  'Unknown message type',
  'Invalid kind: expected audio|video',
  'Invalid rtpParameters: missing codecs',
  'Max 3 encodings for video',
  'Room is full',
  'Room already exists',
  'Room not found',
  'Peer not found',
  'Peer already in room',
  'Pipe transport not found',
  'Recording not enabled',
  'Recording not enabled for this room',
  'Recording already in progress',
  'No active recording',
  'Live streaming not enabled',
  'Live streaming not enabled for this room',
  'Live stream already in progress',
  'No active live stream',
  'At least one output URL (RTMP or SRT) is required',
  'Invalid output path',
  'Invalid recording options',
  'Invalid livestream options',
  'Insufficient disk space for recording',
  'Recording start timeout',
  'Live stream start timeout'
]);

/** حدود الإنتاج تحمل عددًا متغيرًا في نصها، فتُطابق بنمط لا بمساواة. */
const MAX_PRODUCERS_PATTERN = /^Max \d+ producers per kind$/;

/** Maps protocol failures to a small, stable client contract without exposing runtime details. */
function clientErrorCode(error) {
  const message = String(error?.message || '');
  if (FORBIDDEN_MESSAGES.has(message)) return 'FORBIDDEN';
  if (UNAUTHORIZED_MESSAGES.has(message)) return 'UNAUTHORIZED';
  if (INVALID_REQUEST_MESSAGES.has(message) || MAX_PRODUCERS_PATTERN.test(message)) return 'INVALID_REQUEST';
  return 'REQUEST_FAILED';
}

function clientErrorPayload(error) {
  return { status: 'error', error: clientErrorCode(error) };
}

module.exports = { clientErrorCode, clientErrorPayload };
