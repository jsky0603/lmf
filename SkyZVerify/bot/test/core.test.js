'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { validateRequest, authorised, canConfirm } = require('../core');

const input = {
  id: '11111111-1111-4111-8111-111111111111',
  mcUuid: '22222222-2222-4222-8222-222222222222',
  mcName: 'Test_1',
  discordUsername: 'jsky',
};

test('nur enge, gültige Anfragen akzeptieren', () => {
  assert.deepEqual(validateRequest(input), input);
  for (const bad of [
    { ...input, id: '../../etc/passwd' },
    { ...input, mcName: '@everyone' },
    { ...input, discordUsername: 'jsky\n@everyone' },
    { ...input, discordUsername: 'jsky#1234' },
  ]) assert.throws(() => validateRequest(bad), /BAD_REQUEST/);
});

test('nur das angesprochene Discord-Konto im richtigen Kanal darf bestätigen', () => {
  const entry = { status: 'pending', expiresAt: 1000, discordId: '123456789012345678',
    channelId: '333333333333333333', guildId: '444444444444444444' };
  assert.equal(canConfirm(entry, entry.discordId, entry.channelId, entry.guildId, 500), true);
  assert.equal(canConfirm(entry, '999999999999999999', entry.channelId, entry.guildId, 500), false);
  assert.equal(canConfirm(entry, entry.discordId, '111111111111111111', entry.guildId, 500), false);
  assert.equal(canConfirm(entry, entry.discordId, entry.channelId, entry.guildId, 1000), false);
  assert.equal(canConfirm({ ...entry, status: 'confirmed' }, entry.discordId, entry.channelId, entry.guildId, 500), false);
});

test('gemeinsamer Schlüssel muss exakt übereinstimmen', () => {
  assert.equal(authorised('a'.repeat(64), 'a'.repeat(64)), true);
  assert.equal(authorised('a'.repeat(63), 'a'.repeat(64)), false);
  assert.equal(authorised('b'.repeat(64), 'a'.repeat(64)), false);
});
