#!/usr/bin/env node
'use strict';

/**
 * Read-only SIP registration diagnostic.
 * It opens a WebSocket to Asterisk, performs REGISTER + legacy MD5 Digest,
 * prints only protocol milestones, and never sends INVITE, BYE, SMS, or a phone number.
 * Usage: WEBRTC_TEST_PASSWORD=... node diagnose_sip_register.js ws://host:8089/ws
 */

const crypto = require('node:crypto');

const url = process.argv[2];
const username = process.env.WEBRTC_TEST_USERNAME || 'red-webrtc-client';
const password = process.env.WEBRTC_TEST_PASSWORD;
if (!url || !password) {
  console.error('USAGE_ERROR: URL and WEBRTC_TEST_PASSWORD are required');
  process.exit(2);
}

const md5 = (value) => crypto.createHash('md5').update(value).digest('hex');
const randomHex = (bytes) => crypto.randomBytes(bytes).toString('hex');
const host = new URL(url).hostname;
const callId = `${randomHex(16)}@red-register-diagnostic`;
const fromTag = randomHex(8);
let cseq = 1;
let registered = false;
let sentAuthenticatedRegister = false;

function header(message, name) {
  const row = message.split(/\r?\n/).find((line) => line.toLowerCase().startsWith(`${name.toLowerCase()}:`));
  return row ? row.slice(row.indexOf(':') + 1).trim() : null;
}

function digestParam(value, name) {
  if (!value) return null;
  const quoted = new RegExp(`${name}="([^"]+)"`, 'i').exec(value);
  const bare = new RegExp(`${name}=([^,\\s]+)`, 'i').exec(value);
  return quoted?.[1] ?? bare?.[1] ?? null;
}

function buildRegister(auth = null) {
  cseq += 1;
  const uri = `sip:${host}`;
  const lines = [
    `REGISTER ${uri} SIP/2.0`,
    `Via: SIP/2.0/WSS ${host};branch=z9hG4bK${randomHex(16)};rport`,
    'Max-Forwards: 70',
    `From: <sip:${username}@${host}>;tag=${fromTag}`,
    `To: <sip:${username}@${host}>`,
    `Call-ID: ${callId}`,
    `CSeq: ${cseq} REGISTER`,
    'Expires: 120',
    `Contact: <sip:${username}@${host};transport=ws>`,
    'Allow: INVITE,ACK,CANCEL,BYE,OPTIONS,INFO',
    'User-Agent: RED-SIP-REGISTER-DIAGNOSTIC/1.0',
  ];
  if (auth) {
    const ha1 = md5(`${username}:${auth.realm}:${password}`);
    const ha2 = md5(`REGISTER:${uri}`);
    const supportsAuthQop = auth.qop?.split(',').map((value) => value.trim().toLowerCase()).includes('auth');
    if (auth.qop && !supportsAuthQop) throw new Error(`unsupported qop: ${auth.qop}`);
    if (supportsAuthQop) {
      const nc = '00000001';
      const cnonce = randomHex(16);
      const response = md5(`${ha1}:${auth.nonce}:${nc}:${cnonce}:auth:${ha2}`);
      lines.push(`Authorization: Digest username="${username}", realm="${auth.realm}", nonce="${auth.nonce}", uri="${uri}", response="${response}", algorithm=MD5, qop=auth, nc=${nc}, cnonce="${cnonce}"`);
    } else {
      const response = md5(`${ha1}:${auth.nonce}:${ha2}`);
      lines.push(`Authorization: Digest username="${username}", realm="${auth.realm}", nonce="${auth.nonce}", uri="${uri}", response="${response}", algorithm=MD5`);
    }
  }
  lines.push('Content-Length: 0', '', '');
  return lines.join('\r\n');
}

const timeout = setTimeout(() => {
  console.error('RESULT=TIMEOUT');
  process.exit(1);
}, 12_000);

const ws = new WebSocket(url, 'sip');
ws.addEventListener('open', () => {
  console.log('WS_OPEN');
  ws.send(buildRegister());
  console.log('REGISTER_SENT');
});
ws.addEventListener('message', async (event) => {
  const text = typeof event.data === 'string' ? event.data : await event.data.text();
  const first = text.split(/\r?\n/, 1)[0] || '';
  const cseqHeader = header(text, 'CSeq') || '';
  console.log(`SIP_RESPONSE=${first} CSEQ=${cseqHeader}`);
  if (first.startsWith('SIP/2.0 401') && !sentAuthenticatedRegister) {
    const challenge = header(text, 'WWW-Authenticate');
    const realm = digestParam(challenge, 'realm');
    const nonce = digestParam(challenge, 'nonce');
    const qop = digestParam(challenge, 'qop');
    if (!realm || !nonce) {
      console.error('RESULT=AUTH_CHALLENGE_INCOMPLETE');
      process.exit(1);
    }
    sentAuthenticatedRegister = true;
    ws.send(buildRegister({ realm, nonce, qop }));
    console.log('REGISTER_AUTH_SENT');
    return;
  }
  if (first.startsWith('SIP/2.0 200') && /REGISTER$/i.test(cseqHeader)) {
    registered = true;
    console.log('RESULT=REGISTER_OK');
    ws.close(1000, 'diagnostic complete');
  }
});
ws.addEventListener('close', () => {
  clearTimeout(timeout);
  if (!registered) {
    console.error('RESULT=WEBSOCKET_CLOSED_BEFORE_REGISTER');
    process.exit(1);
  }
  process.exit(0);
});
ws.addEventListener('error', () => {
  console.error('RESULT=WEBSOCKET_ERROR');
});
