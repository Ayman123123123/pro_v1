'use strict';

/**
 * حدّ العقد بين الـ SFU والعميل (نسخة TypeScript).
 *
 * مرآة طبق الأصل من `protocol.js` — يبقى `src/server.ts` يستورد
 * `./protocol.js` (مع `moduleResolution: NodeNext` يُحلّ إلى هذا الملف).
 * أي رمز يُضاف هنا يجب أن يُضاف هناك أيضاً، والعكس.
 */

const UNAUTHORIZED_MESSAGES: ReadonlySet<string> = new Set([
  'Unauthorized',
  'Expired or invalid token',
  'Invalid token claims',
  'Ticket not bound to this room',
]);

const FORBIDDEN_MESSAGES: ReadonlySet<string> = new Set([
  'Produce not permitted by ticket',
  'Consume not permitted by ticket',
  'Produce not permitted',
  'Consume not permitted',
]);

const INVALID_REQUEST_MESSAGES: ReadonlySet<string> = new Set([
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
  'Live stream start timeout',
]);

const MAX_PRODUCERS_PATTERN = /^Max \d+ producers per kind$/;

export function clientErrorCode(error: unknown): string {
  const message = String((error as Error | undefined)?.message ?? '');
  if (FORBIDDEN_MESSAGES.has(message)) return 'FORBIDDEN';
  if (UNAUTHORIZED_MESSAGES.has(message)) return 'UNAUTHORIZED';
  if (INVALID_REQUEST_MESSAGES.has(message) || MAX_PRODUCERS_PATTERN.test(message)) return 'INVALID_REQUEST';
  return 'REQUEST_FAILED';
}

export function clientErrorPayload(error: unknown): { status: 'error'; error: string } {
  return { status: 'error', error: clientErrorCode(error) };
}
