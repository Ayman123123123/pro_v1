'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { clientErrorCode, clientErrorPayload } = require('./protocol');

test('maps known protocol failures to stable client codes', () => {
  assert.equal(clientErrorCode(new Error('Unauthorized')), 'UNAUTHORIZED');
  assert.equal(clientErrorCode(new Error('Invalid roomId')), 'INVALID_REQUEST');
  assert.equal(clientErrorCode(new Error('Transport not found')), 'INVALID_REQUEST');
});

test('does not expose internal error details to the client', () => {
  const internal = new Error('mediasoup worker 4219 failed while allocating transport');
  const payload = clientErrorPayload(internal);
  assert.deepEqual(payload, { status: 'error', error: 'REQUEST_FAILED' });
  assert.equal(JSON.stringify(payload).includes('mediasoup'), false);
});
