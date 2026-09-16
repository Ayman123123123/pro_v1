import { createHash, createHmac, timingSafeEqual } from 'crypto';
import { AuthClaims, Config } from './types.js';

export class AuthManager {
  private config: Config;

  constructor(config: Config) {
    this.config = config;
  }

  private base64UrlDecode(value: string): Buffer {
    return Buffer.from(value.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
  }

  authenticate(token: string): AuthClaims {
    const cleanToken = token.replace(/^Bearer\s+/i, '');
    const parts = cleanToken.split('.');

    if (parts.length !== 3) {
      throw new Error('Unauthorized');
    }

    const secret = this.config.sfuTicketSecret || this.config.jwtSecret;
    const key = createHash('sha256').update(secret, 'utf8').digest();
    const expected = createHmac('sha256', key).update(`${parts[0]}.${parts[1]}`).digest();
    const supplied = this.base64UrlDecode(parts[2]);

    if (expected.length !== supplied.length || !timingSafeEqual(expected, supplied)) {
      throw new Error('Unauthorized');
    }

    const claims = JSON.parse(this.base64UrlDecode(parts[1]).toString('utf8')) as AuthClaims;

    if (!claims.sub || !claims.redId || !claims.exp) {
      throw new Error('Invalid token claims');
    }

    const clockSkewMs = 120_000;
    if (claims.exp * 1000 <= Date.now() - clockSkewMs) {
      throw new Error('Expired or invalid token');
    }

    return claims;
  }

  validateRoomAccess(claims: AuthClaims, roomId: string): boolean {
    if (!claims.sfuGroupId) return false;

    const allowedRooms = [
      String(claims.sfuGroupId),
      `GROUP_CALL_${claims.sfuGroupId}`,
      claims.sfuRoomId,
    ].filter(Boolean);

    return allowedRooms.includes(roomId);
  }

  canProduce(claims: AuthClaims): boolean {
    return claims.sfuCanProduce === true;
  }

  canConsume(claims: AuthClaims): boolean {
    return claims.sfuCanConsume !== false;
  }

  generateTicket(claims: Partial<AuthClaims>, ttlSeconds: number = 600): string {
    const header = { alg: 'HS256', typ: 'JWT' };
    const payload = {
      ...claims,
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