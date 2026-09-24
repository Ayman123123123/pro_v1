'use strict';

/** Fail closed before starting mediasoup. A regular access JWT must never double as a media ticket. */
function requireDedicatedSfuSecret(jwtSecret, ticketSecret) {
  if (!ticketSecret || ticketSecret.length < 32 || ticketSecret === 'change-me-in-production-please') {
    throw new Error('SFU_TICKET_SECRET must contain at least 32 non-placeholder characters');
  }
  if (ticketSecret === jwtSecret) {
    throw new Error('SFU_TICKET_SECRET must differ from JWT_SECRET');
  }
  return ticketSecret;
}

module.exports = { requireDedicatedSfuSecret };
