'use strict';

const crypto = require('node:crypto');

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const SNOWFLAKE = /^[0-9]{17,20}$/;
const USERNAME = /^[a-z0-9._]{2,32}$/;
const MINECRAFT = /^[A-Za-z0-9_]{1,16}$/;

function validateRequest(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('BAD_REQUEST');
  const { id, mcUuid, mcName, discordUsername } = raw;
  if (typeof id !== 'string' || !UUID.test(id) || typeof mcUuid !== 'string' || !UUID.test(mcUuid)
      || typeof mcName !== 'string' || !MINECRAFT.test(mcName)
      || typeof discordUsername !== 'string' || !USERNAME.test(discordUsername)) {
    throw new Error('BAD_REQUEST');
  }
  return { id: id.toLowerCase(), mcUuid: mcUuid.toLowerCase(), mcName, discordUsername };
}

function authorised(provided, secret) {
  if (typeof provided !== 'string' || typeof secret !== 'string') return false;
  const a = Buffer.from(provided, 'utf8');
  const b = Buffer.from(secret, 'utf8');
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function canConfirm(entry, actorId, channelId, guildId, now = Date.now()) {
  return !!entry && entry.status === 'pending' && entry.expiresAt > now
    && entry.discordId === actorId && entry.channelId === channelId && entry.guildId === guildId;
}

module.exports = { validateRequest, authorised, canConfirm, SNOWFLAKE };
