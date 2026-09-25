import { createHash, createHmac, timingSafeEqual } from 'crypto';
import type { Config } from './config.js';
import type { AuthClaims } from './types.js';

export class AuthManager {
  private config: Config;

  constructor(config: Config) {
    this.config = config;
  }

  private base64UrlDecode(value: string): Buffer {
    try {
      return Buffer.from(value.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
    } catch {
      throw new Error('Unauthorized');
    }
  }

  private decodeClaims(segment: string): AuthClaims {
    try {
      return JSON.parse(this.base64UrlDecode(segment).toString('utf8')) as AuthClaims;
    } catch {
      throw new Error('Unauthorized');
    }
  }

  authenticate(token: string): AuthClaims {
    const cleanToken = token.replace(/^Bearer\s+/i, '');
    const parts = cleanToken.split('.');

    if (parts.length !== 3 || !parts[0] || !parts[1] || !parts[2]) {
      throw new Error('Unauthorized');
    }

    // رفض alg=none أو أي خوارزمية غير HS256 قبل التحقق من التوقيع.
    let header: { alg?: string };
    try {
      header = JSON.parse(this.base64UrlDecode(parts[0] as string).toString('utf8')) as { alg?: string };
    } catch {
      throw new Error('Unauthorized');
    }
    if (header.alg !== 'HS256') {
      throw new Error('Unauthorized');
    }

    const secret = this.config.sfuTicketSecret || this.config.jwtSecret;
    const key = createHash('sha256').update(secret, 'utf8').digest();
    const expected = createHmac('sha256', key).update(`${parts[0]}.${parts[1]}`).digest();
    let supplied: Buffer;
    try {
      supplied = this.base64UrlDecode(parts[2]);
    } catch {
      throw new Error('Unauthorized');
    }

    if (expected.length !== supplied.length || !timingSafeEqual(expected, supplied)) {
      throw new Error('Unauthorized');
    }

    const claims = this.decodeClaims(parts[1]);

    if (!claims.sub || !claims.redId || !claims.exp) {
      throw new Error('Invalid token claims');
    }

    const now = Date.now();
    const clockSkewMs = 120_000;
    if (claims.exp * 1000 <= now - clockSkewMs) {
      throw new Error('Expired or invalid token');
    }

    if (typeof claims.iat === 'number' && claims.iat * 1000 > now + clockSkewMs) {
      throw new Error('Invalid token claims');
    }

    const expectedIssuer = (this.config as { jwtIssuer?: string }).jwtIssuer;
    if (expectedIssuer && claims.iss !== expectedIssuer) {
      throw new Error('Invalid token claims');
    }

    const expectedAudience = (this.config as { jwtAudience?: string }).jwtAudience;
    if (expectedAudience) {
      const aud = claims.aud;
      const ok = Array.isArray(aud) ? aud.includes(expectedAudience) : aud === expectedAudience;
      if (!ok) {
        throw new Error('Invalid token claims');
      }
    }

    return claims;
  }

  validateRoomAccess(claims: AuthClaims, roomId: string): boolean {
    if (!claims.sfuGroupId) return false;

    const group = String(claims.sfuGroupId);
    return roomId === group || roomId === `GROUP_CALL_${group}`;
  }

  canProduce(claims: AuthClaims): boolean {
    return claims.sfuCanProduce === true;
  }

  canConsume(claims: AuthClaims): boolean {
    return claims.sfuCanConsume !== false;
  }

  generateTicket(claims: Partial<AuthClaims>, ttlSeconds: number = 600): string {
    const header = { alg: 'HS256', typ: 'JWT' };
    const cfg = this.config as { jwtIssuer?: string; jwtAudience?: string };
    const payload = {
      ...claims,
      ...(cfg.jwtIssuer && !(claims as AuthClaims).iss ? { iss: cfg.jwtIssuer } : {}),
      ...(cfg.jwtAudience && !(claims as AuthClaims).aud ? { aud: cfg.jwtAudience } : {}),
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + ttlSeconds,
    };

    const secret = this.config.sfuTicketSecret || this.config.jwtSecret;
    const key = createHash('sha256').update(secret, 'utf8').digest();

    const encodedHeader = Buffer.from(JSON.stringify(header)).toString('base64url');
    const encodedPayload = Buffer.from(JSON.stringify(payload)).toString('base64url');
    const signature = createHmac('sha256', key)
      .update(`${encodedHeader}.${encodedPayload}`)
      .digest('base64url');

    return `${encodedHeader}.${encodedPayload}.${signature}`;
  }

  verifyTicket(ticket: string): AuthClaims {
    return this.authenticate(ticket);
  }
}