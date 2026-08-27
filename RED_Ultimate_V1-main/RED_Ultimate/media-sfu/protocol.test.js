'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { clientErrorCode, clientErrorPayload } = require('./protocol');

test('maps known protocol failures to stable client codes', () => {
  assert.equal(clientErrorCode(new Error('Unauthorized')), 'UNAUTHORIZED');
  assert.equal(clientErrorCode(new Error('Expired or invalid token')), 'UNAUTHORIZED');
  assert.equal(clientErrorCode(new Error('Ticket not bound to this room')), 'UNAUTHORIZED');
  assert.equal(clientErrorCode(new Error('Invalid roomId')), 'INVALID_REQUEST');
  assert.equal(clientErrorCode(new Error('Transport not found')), 'INVALID_REQUEST');
  assert.equal(clientErrorCode(new Error('Producer not found')), 'INVALID_REQUEST');
  assert.equal(clientErrorCode(new Error('Too many transports')), 'INVALID_REQUEST');
  assert.equal(clientErrorCode(new Error('Max 3 producers per kind')), 'INVALID_REQUEST');
});

test('does not expose internal error details to the client', () => {
  const internal = new Error('mediasoup worker 4219 failed while allocating transport');
  const payload = clientErrorPayload(internal);
  assert.deepEqual(payload, { status: 'error', error: 'REQUEST_FAILED' });
  assert.equal(JSON.stringify(payload).includes('mediasoup'), false);
});

test('tolerates a missing or non-error argument', () => {
  assert.equal(clientErrorCode(undefined), 'REQUEST_FAILED');
  assert.equal(clientErrorCode({}), 'REQUEST_FAILED');
});

/**
 * حارس العقد: كل `throw new Error('literal')` في server.js يجب أن يكون
 * مصنّفاً في protocol.js. بدون هذا الحارس، أي تحقق جديد يُضاف للخادم
 * يسقط صامتاً إلى REQUEST_FAILED العام، فيفقد العميل قدرته على التمييز
 * بين «جدّد رمزك» و«طلبك غير صالح» — وهذا بالضبط ما يجعل خطأ الانضمام
 * لغرفة يبدو كعطل خادم.
 */
test('every literal server-side failure is classified, not silently generic', () => {
  const server = fs.readFileSync(path.join(__dirname, 'server.js'), 'utf8');
  const literals = [...server.matchAll(/throw new Error\('([^']+)'\)/g)].map((m) => m[1]);

  assert.ok(literals.length > 10, `expected many literal throws, found ${literals.length}`);

  const unclassified = literals.filter((message) => {
    // JWT_SECRET يُرمى عند الإقلاع قبل وجود أي عميل، فلا يعبر العقد أبداً.
    if (message.startsWith('JWT_SECRET')) return false;
    return clientErrorCode(new Error(message)) === 'REQUEST_FAILED';
  });

  assert.deepEqual(unclassified, [], `unclassified server errors: ${unclassified.join(', ')}`);
});
