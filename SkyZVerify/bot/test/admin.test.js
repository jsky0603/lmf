'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { AdminPanel, validateSnapshot, findLink } = require('../admin');

const first = { uuid: '11111111-1111-4111-8111-111111111111', mcName: 'Nou',
  discordId: '123456789012345678', discordUsername: 'nou' };
const second = { uuid: '22222222-2222-4222-8222-222222222222', mcName: 'Sky',
  discordId: '234567890123456789', discordUsername: 'jsky' };

test('Admin-Liste enthält nur gültige, eindeutige Verknüpfungen', () => {
  assert.deepEqual(validateSnapshot({ links: [second, first] }), [first, second]);
  assert.equal(findLink([first, second], 'SKY').uuid, second.uuid);
  assert.equal(findLink([first, second], first.discordId).uuid, first.uuid);
  assert.throws(() => validateSnapshot({ links: [first, first] }), /BAD_SNAPSHOT/);
  assert.throws(() => validateSnapshot({ links: [{ ...first, mcName: '@everyone' }] }), /BAD_SNAPSHOT/);
  assert.throws(() => findLink([first], 'unknown'), /Keine Verknüpfung/);
});

test('Admin-Auftrag wird dauerhaft gespeichert und nach Ergebnis entfernt', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'skyzverify-admin-'));
  fs.mkdirSync(path.join(root, 'data'));
  const guild = { id: '345678901234567890' };
  const panel = new AdminPanel(root, guild, { user: { id: '456789012345678901' } }, {});
  panel.render = async () => {};
  panel.channel = { id: '567890123456789012', send: async () => {} };
  const action = { id: '33333333-3333-4333-8333-333333333333', actorId: '678901234567890123',
    type: 'unlink', mc: first.uuid, discordId: first.discordId };
  panel.state.actions.push(action);
  panel.save();
  const restored = new AdminPanel(root, guild, { user: { id: '456789012345678901' } }, {});
  assert.deepEqual(restored.pending().actions, [action]);
  await panel.finish({ id: action.id, ok: true, message: 'Getrennt.' });
  assert.deepEqual(new AdminPanel(root, guild, {}, {}).pending().actions, []);
  fs.rmSync(root, { recursive: true, force: true });
});

test('Ohne Administratorrecht führt ein Button keinen Auftrag aus', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'skyzverify-admin-'));
  fs.mkdirSync(path.join(root, 'data'));
  const panel = new AdminPanel(root, { id: '345678901234567890' }, {}, {});
  panel.channel = { id: '567890123456789012' };
  let refused;
  const handled = await panel.interact({ guildId: panel.guild.id, channelId: panel.channel.id,
    customId: 'svadmin:add', memberPermissions: { has: () => false },
    reply: async value => { refused = value; } });
  assert.equal(handled, true);
  assert.match(refused.content, /Administratoren/);
  assert.deepEqual(panel.pending().actions, []);
  fs.rmSync(root, { recursive: true, force: true });
});
