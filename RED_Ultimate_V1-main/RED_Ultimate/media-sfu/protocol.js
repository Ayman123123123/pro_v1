'use strict';

/** Maps protocol failures to a small, stable client contract without exposing runtime details. */
function clientErrorCode(error) {
  const message = String(error?.message || '');
  if (message === 'Unauthorized' || message === 'Expired or invalid token') return 'UNAUTHORIZED';
  if (
    message === 'Join a room first' ||
    message === 'Already joined' ||
    message === 'Invalid roomId' ||
    message === 'Transport not found' ||
    message === 'Consumer not found' ||
    message === 'Cannot consume producer' ||
    message === 'Unknown message type'
  ) return 'INVALID_REQUEST';
  return 'REQUEST_FAILED';
}

function clientErrorPayload(error) {
  return { status: 'error', error: clientErrorCode(error) };
}

module.exports = { clientErrorCode, clientErrorPayload };
