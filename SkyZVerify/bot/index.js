'use strict';

const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const {
  Client, Events, GatewayIntentBits, ChannelType, PermissionFlagsBits,
  ButtonBuilder, ButtonStyle, ActionRowBuilder,
} = require('discord.js');
const { validateRequest, authorised, canConfirm, SNOWFLAKE } = require('./core');
const { AdminPanel } = require('./admin');

const ROOT = __dirname;
const DATA = path.join(ROOT, 'data');
const STATE = path.join(DATA, 'requests.json');
const TTL = 5 * 60 * 1000;
const config = JSON.parse(fs.readFileSync(path.join(ROOT, 'config.json'), 'utf8'));

if (typeof config.token !== 'string' || config.token.length < 30
    || !SNOWFLAKE.test(config.guildId) || !/^[a-f0-9]{64}$/i.test(config.bridgeSecret)
    || (config.categoryId && !SNOWFLAKE.test(config.categoryId))
    || !Number.isInteger(config.listenPort) || config.listenPort < 1 || config.listenPort > 65535
    || typeof config.listenHost !== 'string' || !config.listenHost) {
  throw new Error('config.json: token, guildId, bridgeSecret und Host/Port prüfen.');
}

fs.mkdirSync(DATA, { recursive: true });
const requests = new Map();
if (fs.existsSync(STATE)) {
  for (const entry of JSON.parse(fs.readFileSync(STATE, 'utf8'))) {
    if (entry && /^[0-9a-f-]{36}$/i.test(entry.id) && SNOWFLAKE.test(entry.channelId)
        && SNOWFLAKE.test(entry.discordId) && entry.guildId === config.guildId) {
      requests.set(entry.id, entry);
    }
  }
}

function persist() {
  const tmp = STATE + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify([...requests.values()], null, 2), { mode: 0o600 });
  fs.renameSync(tmp, STATE);
}

class HttpError extends Error {
  constructor(status, code) { super(code); this.status = status; this.code = code; }
}

const client = new Client({ intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMembers] });
let guild;
let admin;
const attempts = new Map();
let sequence = Promise.resolve();
function serial(job) {
  const next = sequence.then(job, job);
  sequence = next.catch(error => console.error('[SkyZVerify] Bridge:', error.message));
  return next;
}

async function deleteChannel(entry) {
  try {
    const channel = await guild.channels.fetch(entry.channelId);
    if (channel && channel.topic === `SkyZVerify ${entry.id}` && channel.guildId === guild.id) {
      await channel.delete('SkyZVerify: bestätigt, abgelaufen oder abgebrochen');
    }
  } catch (error) {
    console.error('[SkyZVerify] Kanal konnte nicht entfernt werden:', error.message);
  }
}

async function remove(entry) {
  if (!entry) return;
  requests.delete(entry.id);
  persist();
  await deleteChannel(entry);
}

async function cleanup(orphans = false) {
  const now = Date.now();
  for (const entry of [...requests.values()]) {
    if (entry.expiresAt <= now) await remove(entry);
  }
  // Find orphaned verification channels from a crash between channel creation and state save.
  if (orphans) {
    const channels = await guild.channels.fetch();
    for (const channel of channels.values()) {
      if (!channel || !channel.topic || !channel.topic.startsWith('SkyZVerify ')) continue;
      if (config.categoryId && channel.parentId !== config.categoryId) continue;
      const id = channel.topic.slice('SkyZVerify '.length);
      if (!requests.has(id)) {
        try { await channel.delete('SkyZVerify: verwaister Kanal'); }
        catch (error) { console.error('[SkyZVerify] Verwaister Kanal:', error.message); }
      }
    }
  }
  for (const [key, when] of attempts) if (now - when > 60_000) attempts.delete(key);
}

async function resolveMember(username) {
  // Discord's search uses prefixes (including nicknames). Only the exact global username counts.
  const found = await guild.members.search({ query: username, limit: 1000 });
  const exact = [...found.values()].filter(m => m.user.username.toLowerCase() === username);
  if (!exact.length) throw new HttpError(404, 'USER_NOT_FOUND');
  if (exact.length !== 1) throw new HttpError(409, 'AMBIGUOUS_USERNAME');
  return exact[0];
}

async function create(raw) {
  let request;
  try { request = validateRequest(raw); }
  catch { throw new HttpError(400, 'BAD_REQUEST'); }
  const old = requests.get(request.id);
  if (old) {
    if (old.mcUuid !== request.mcUuid || old.discordUsername !== request.discordUsername) {
      throw new HttpError(409, 'ID_ALREADY_USED');
    }
    return { status: old.status, discordId: old.discordId, channelId: old.channelId };
  }
  if (requests.size >= 30) throw new HttpError(429, 'TOO_MANY_REQUESTS');
  const previous = attempts.get(request.mcUuid) || 0;
  if (Date.now() - previous < 15_000) throw new HttpError(429, 'TOO_MANY_REQUESTS');
  attempts.set(request.mcUuid, Date.now());
  const member = await resolveMember(request.discordUsername);
  for (const entry of requests.values()) {
    if (entry.discordId === member.id || entry.mcUuid === request.mcUuid) {
      throw new HttpError(409, 'ALREADY_PENDING');
    }
  }
  const channel = await guild.channels.create({
    name: 'verify-' + request.id.slice(0, 8),
    type: ChannelType.GuildText,
    parent: config.categoryId || undefined,
    topic: 'SkyZVerify ' + request.id,
    permissionOverwrites: [
      { id: guild.roles.everyone.id, deny: [PermissionFlagsBits.ViewChannel] },
      { id: member.id, allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.ReadMessageHistory] },
      { id: client.user.id, allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages,
        PermissionFlagsBits.ManageChannels] },
    ],
    reason: 'SkyZVerify für ' + request.mcName,
  });
  const entry = {
    ...request,
    guildId: guild.id,
    discordId: member.id,
    channelId: channel.id,
    status: 'pending',
    expiresAt: Date.now() + TTL,
  };
  try {
    requests.set(entry.id, entry);
    persist();
    const button = new ButtonBuilder().setCustomId('skyverify:' + entry.id)
      .setLabel('Ja, ich bin das').setStyle(ButtonStyle.Success);
    await channel.send({
      content: `<@${member.id}> Möchtest du dein Discord-Konto mit dem Minecraft-Konto **${request.mcName}** verbinden? `
        + 'Klicke nur, wenn du die Anfrage selbst in Minecraft gestartet hast. Gültig für 5 Minuten.',
      components: [new ActionRowBuilder().addComponents(button)],
      allowedMentions: { users: [member.id] },
    });
    return { status: entry.status, discordId: entry.discordId, channelId: entry.channelId };
  } catch (error) {
    requests.delete(entry.id);
    try { persist(); } catch (saveError) { console.error('[SkyZVerify] Anfragespeicher:', saveError.message); }
    await deleteChannel(entry);
    throw error;
  }
}

function reply(res, code, body) {
  const json = JSON.stringify(body);
  res.writeHead(code, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store',
    'x-content-type-options': 'nosniff' });
  res.end(json);
}

async function body(req, limit = 4096) {
  let total = 0;
  const parts = [];
  for await (const part of req) {
    total += part.length;
    if (total > limit) throw new HttpError(413, 'TOO_LARGE');
    parts.push(part);
  }
  try { return JSON.parse(Buffer.concat(parts).toString('utf8')); }
  catch { throw new HttpError(400, 'BAD_JSON'); }
}

async function route(req, res) {
  if (!authorised(req.headers['x-skyz-secret'], config.bridgeSecret)) {
    reply(res, 401, { code: 'UNAUTHORIZED' });
    return;
  }
  if (!client.isReady() || !guild) {
    reply(res, 503, { code: 'BOT_NOT_READY' });
    return;
  }
  try {
    if (req.method === 'GET' && req.url === '/admin/actions') {
      reply(res, 200, admin.pending());
    } else if (req.method === 'POST' && req.url === '/admin/snapshot') {
      try { await admin.receiveSnapshot(await body(req, 1024 * 1024)); }
      catch (error) { throw new HttpError(400, 'BAD_SNAPSHOT'); }
      reply(res, 200, { status: 'ok' });
    } else if (req.method === 'POST' && req.url === '/admin/result') {
      try { await admin.finish(await body(req)); }
      catch (error) { throw new HttpError(400, 'BAD_RESULT'); }
      reply(res, 200, { status: 'ok' });
    } else if (req.method === 'POST' && req.url === '/requests') {
      const payload = await body(req);
      reply(res, 200, await serial(() => create(payload)));
    } else {
      const match = /^\/requests\/([0-9a-f-]{36})$/.exec(req.url || '');
      if (!match) throw new HttpError(404, 'NOT_FOUND');
      const id = match[1];
      if (req.method === 'GET') {
        const entry = requests.get(id);
        if (!entry) throw new HttpError(404, 'NOT_FOUND');
        reply(res, 200, { status: entry.expiresAt > Date.now() ? entry.status : 'expired',
          discordId: entry.discordId });
      } else if (req.method === 'DELETE') {
        await serial(() => remove(requests.get(id)));
        reply(res, 200, { status: 'gone' });
      } else throw new HttpError(405, 'METHOD_NOT_ALLOWED');
    }
  } catch (error) {
    if (!(error instanceof HttpError)) console.error('[SkyZVerify] Anfrage:', error);
    reply(res, error.status || 500, { code: error.code || 'INTERNAL_ERROR' });
  }
}

client.on(Events.InteractionCreate, async interaction => {
  if (interaction.customId?.startsWith('svadmin:')) {
    try { if (admin) await admin.interact(interaction); }
    catch (error) {
      console.error('[SkyZVerify] Admin-Interaktion:', error);
      if (!interaction.replied && !interaction.deferred) {
        await interaction.reply({ content: 'Aktion fehlgeschlagen. Bitte erneut versuchen.',
          ephemeral: true }).catch(() => {});
      }
    }
    return;
  }
  if (!interaction.isButton() || !interaction.customId.startsWith('skyverify:')) return;
  const entry = requests.get(interaction.customId.slice('skyverify:'.length));
  if (!canConfirm(entry, interaction.user.id, interaction.channelId, interaction.guildId)) {
    await interaction.reply({ content: 'Diese Anfrage gehört nicht zu dir oder ist abgelaufen.', ephemeral: true });
    return;
  }
  try {
    entry.status = 'confirmed';
    persist();
  } catch (error) {
    entry.status = 'pending';
    console.error('[SkyZVerify] Bestätigung:', error);
    await interaction.reply({ content: 'Speicherfehler. Bitte erneut versuchen.', ephemeral: true });
    return;
  }
  try { await interaction.reply({ content: 'Bestätigt! Minecraft schaltet dich in wenigen Sekunden frei.', ephemeral: true }); }
  catch (error) { console.error('[SkyZVerify] Discord-Antwort:', error.message); }
});

client.once(Events.ClientReady, async ready => {
  try {
    guild = await ready.guilds.fetch(config.guildId);
    await cleanup(true);
    admin = new AdminPanel(ROOT, guild, client, config);
    await admin.start();
    setInterval(() => cleanup().catch(error => console.error('[SkyZVerify] Aufräumen:', error)), 30_000).unref();
    http.createServer((req, res) => route(req, res).catch(error => {
      console.error('[SkyZVerify] HTTP:', error);
      if (!res.headersSent) reply(res, 500, { code: 'INTERNAL_ERROR' });
    })).listen(config.listenPort, config.listenHost, () => {
      console.log(`[SkyZVerify] Bot bereit in ${guild.name}, Bridge auf ${config.listenHost}:${config.listenPort}`);
    });
  } catch (error) {
    console.error('[SkyZVerify] Start fehlgeschlagen:', error);
    process.exitCode = 1;
    client.destroy();
  }
});

client.on(Events.Error, error => console.error('[SkyZVerify] Discord:', error));
client.login(config.token).catch(error => { console.error('[SkyZVerify] Anmeldung fehlgeschlagen:', error); process.exitCode = 1; });
