'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { requireDedicatedSfuSecret } = require('./sfu-secret');

const jwt = 'jwt-only-test-secret-0123456789-abcdefgh';
const media = 'media-only-test-secret-0123456789-abcdefgh';

test('no SFU startup without a dedicated media secret', () => {
  assert.throws(() => requireDedicatedSfuSecret(jwt, ''), /SFU_TICKET_SECRET/);
  assert.throws(() => requireDedicatedSfuSecret(jwt, undefined), /SFU_TICKET_SECRET/);
  assert.throws(() => requireDedicatedSfuSecret(jwt, jwt), /must differ/);
  assert.throws(() => requireDedicatedSfuSecret(jwt, 'change-me-in-production-please'), /placeholder/);
});

test('a separate SFU key is accepted without changing the JWT key', () => {
  assert.equal(requireDedicatedSfuSecret(jwt, media), media);
});
