'use strict';

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {
  ActionRowBuilder, ButtonBuilder, ButtonStyle, ChannelType, EmbedBuilder,
  MessageFlags, ModalBuilder, PermissionFlagsBits, TextInputBuilder, TextInputStyle,
} = require('discord.js');
const { SNOWFLAKE } = require('./core');

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MC_NAME = /^[A-Za-z0-9_]{1,16}$/;
const DISCORD_NAME = /^[a-z0-9._]{2,32}$/;
const TOPIC = 'SkyZVerify-Admin-Panel';
const PAGE_SIZE = 10;

function validateSnapshot(body) {
  if (!body || !Array.isArray(body.links) || body.links.length > 5000) throw new Error('BAD_SNAPSHOT');
  const uuids = new Set();
  const discordIds = new Set();
  const links = body.links.map(value => {
    if (!value || typeof value !== 'object' || !UUID.test(value.uuid)
        || !MC_NAME.test(value.mcName) || !SNOWFLAKE.test(value.discordId)
        || !DISCORD_NAME.test(value.discordUsername)) throw new Error('BAD_SNAPSHOT');
    if (uuids.has(value.uuid) || discordIds.has(value.discordId)) throw new Error('BAD_SNAPSHOT');
    uuids.add(value.uuid);
    discordIds.add(value.discordId);
    return { uuid: value.uuid.toLowerCase(), mcName: value.mcName,
      discordId: value.discordId, discordUsername: value.discordUsername };
  });
  links.sort((a, b) => a.mcName.localeCompare(b.mcName, 'de', { sensitivity: 'base' }));
  return links;
}

function findLink(links, query) {
  const target = query.trim().toLowerCase();
  const found = links.filter(link => link.uuid === target
    || link.mcName.toLowerCase() === target || link.discordId === target);
  if (found.length !== 1) throw new Error(found.length
    ? 'Name ist mehrdeutig. Bitte Minecraft-UUID angeben.'
    : 'Keine Verknüpfung gefunden.');
  return found[0];
}

function writeJson(file, data) {
  const temporary = file + '.tmp';
  fs.writeFileSync(temporary, JSON.stringify(data, null, 2), { mode: 0o600 });
  fs.renameSync(temporary, file);
}

class AdminPanel {
  constructor(root, guild, client, config) {
    this.guild = guild;
    this.client = client;
    this.config = config;
    this.file = path.join(root, 'data', 'admin-panel.json');
    this.state = fs.existsSync(this.file)
      ? JSON.parse(fs.readFileSync(this.file, 'utf8'))
      : { channelId: null, messageId: null, actions: [] };
    if (!Array.isArray(this.state.actions)) throw new Error('admin-panel.json ist ungültig');
    this.links = [];
    this.syncedAt = 0;
    this.page = 0;
    this.lastRender = '';
    this.renderChain = Promise.resolve();
    this.channel = null;
    this.message = null;
  }

  save() { writeJson(this.file, this.state); }
  isOnline() { return Date.now() - this.syncedAt < 15000; }

  async start() {
    let channel;
    if (SNOWFLAKE.test(this.state.channelId || '')) {
      channel = await this.guild.channels.fetch(this.state.channelId).catch(() => null);
      if (channel && channel.topic !== TOPIC) channel = null;
    }
    if (!channel) {
      const channels = await this.guild.channels.fetch();
      channel = [...channels.values()].find(item => item && item.topic === TOPIC);
    }
    const overwrites = [
      { id: this.guild.roles.everyone.id, deny: [PermissionFlagsBits.ViewChannel] },
      { id: this.client.user.id, allow: [PermissionFlagsBits.ViewChannel,
        PermissionFlagsBits.SendMessages, PermissionFlagsBits.ReadMessageHistory,
        PermissionFlagsBits.ManageChannels, PermissionFlagsBits.ManageRoles] },
    ];
    if (!channel) {
      channel = await this.guild.channels.create({
        name: 'skyzverify-admin', type: ChannelType.GuildText, topic: TOPIC,
        parent: this.config.categoryId || undefined, permissionOverwrites: overwrites,
        reason: 'Private SkyZVerify-Verwaltung',
      });
    } else {
      await channel.permissionOverwrites.set(overwrites, 'SkyZVerify-Admin-Kanal privat halten');
    }
    this.channel = channel;
    if (SNOWFLAKE.test(this.state.messageId || '')) {
      this.message = await channel.messages.fetch(this.state.messageId).catch(() => null);
      if (this.message && this.message.author.id !== this.client.user.id) this.message = null;
    }
    if (!this.message) {
      const recent = await channel.messages.fetch({ limit: 25 });
      this.message = recent.find(item => item.author.id === this.client.user.id
        && item.embeds[0]?.title === 'SkyZVerify · Freigaben') || null;
    }
    if (!this.message) this.message = await channel.send({ content: 'SkyZVerify · Admin',
      allowedMentions: { parse: [] } });
    this.state.channelId = channel.id;
    this.state.messageId = this.message.id;
    this.save();
    await this.render();
    setInterval(() => this.render().catch(error =>
      console.error('[SkyZVerify] Admin-Panel:', error)), 5000).unref();
  }

  async receiveSnapshot(body) {
    this.links = validateSnapshot(body);
    this.syncedAt = Date.now();
    this.render().catch(error => console.error('[SkyZVerify] Admin-Panel:', error));
  }

  pending() {
    // Acknowledge one operation before dispatching the next. A retried unlink
    // must never run after a newly queued link for the same Minecraft account.
    return { actions: this.state.actions.slice(0, 1) };
  }

  async finish(body) {
    if (!body || !UUID.test(body.id) || typeof body.ok !== 'boolean'
        || typeof body.message !== 'string' || body.message.length > 250) {
      throw new Error('BAD_RESULT');
    }
    const index = this.state.actions.findIndex(action => action.id === body.id);
    if (index < 0) return;
    const [action] = this.state.actions.splice(index, 1);
    this.save();
    this.render().catch(error => console.error('[SkyZVerify] Admin-Panel:', error));
    const label = action.type === 'link' ? 'Verknüpfen' : 'Trennen';
    await this.channel.send({
      content: `${body.ok ? '✅' : '❌'} ${label} · \`${action.mc}\` · Admin <@${action.actorId}>: ${body.message}`,
      allowedMentions: { parse: [] },
    }).catch(error => console.error('[SkyZVerify] Admin-Protokoll:', error));
  }

  render() {
    this.renderChain = this.renderChain.catch(() => {}).then(() => this.renderNow());
    return this.renderChain;
  }

  async renderNow() {
    if (!this.message) return;
    const online = this.isOnline();
    const links = online ? this.links : [];
    const pages = Math.max(1, Math.ceil(links.length / PAGE_SIZE));
    this.page = Math.min(this.page, pages - 1);
    const signature = JSON.stringify({ online, links, page: this.page,
      pending: this.state.actions.length });
    if (signature === this.lastRender) return;
    const shown = links.slice(this.page * PAGE_SIZE, (this.page + 1) * PAGE_SIZE);
    const description = !online ? 'Minecraft-Server nicht verbunden. Die Liste wird nach der nächsten Synchronisierung angezeigt.'
      : shown.length ? shown.map((entry, i) =>
        `**${this.page * PAGE_SIZE + i + 1}. ${entry.mcName}** · @${entry.discordUsername}\n`
        + `Minecraft-UUID: \`${entry.uuid}\`\nDiscord-ID: \`${entry.discordId}\``).join('\n\n')
        : 'Noch keine Verknüpfungen vorhanden.';
    const embed = new EmbedBuilder()
      .setTitle('SkyZVerify · Freigaben')
      .setColor(online ? 0x9b80e8 : 0xe2a346)
      .setDescription(description)
      .setFooter({ text: `${online ? links.length + ' verknüpft' : 'Server offline'} · Seite ${this.page + 1}/${pages} · ${this.state.actions.length} offen` });
    const row = new ActionRowBuilder().addComponents(
      new ButtonBuilder().setCustomId('svadmin:prev').setLabel('Zurück')
        .setStyle(ButtonStyle.Secondary).setDisabled(this.page === 0),
      new ButtonBuilder().setCustomId('svadmin:next').setLabel('Weiter')
        .setStyle(ButtonStyle.Secondary).setDisabled(this.page >= pages - 1),
      new ButtonBuilder().setCustomId('svadmin:add').setLabel('Hinzufügen')
        .setStyle(ButtonStyle.Success).setDisabled(!online),
      new ButtonBuilder().setCustomId('svadmin:remove').setLabel('Trennen')
        .setStyle(ButtonStyle.Danger).setDisabled(!online || links.length === 0),
    );
    await this.message.edit({ content: '', embeds: [embed], components: [row],
      allowedMentions: { parse: [] } });
    this.lastRender = signature;
  }

  async interact(interaction) {
    if (interaction.guildId !== this.guild.id || interaction.channelId !== this.channel?.id
        || !interaction.customId?.startsWith('svadmin:')) return false;
    if (!interaction.memberPermissions?.has(PermissionFlagsBits.Administrator)) {
      await interaction.reply({ content: 'Nur Discord-Administratoren können Verknüpfungen verwalten.',
        flags: MessageFlags.Ephemeral });
      return true;
    }
    if (interaction.isButton()) {
      if (interaction.message.id !== this.message.id) {
        await interaction.reply({ content: 'Dieses Panel ist veraltet.',
          flags: MessageFlags.Ephemeral });
        return true;
      }
      const operation = interaction.customId.slice('svadmin:'.length);
      if (operation === 'prev' || operation === 'next') {
        this.page += operation === 'prev' ? -1 : 1;
        this.page = Math.max(0, this.page);
        await interaction.deferUpdate();
        await this.render();
      } else if (operation === 'add' || operation === 'remove') {
        if (!this.isOnline()) {
          await interaction.reply({ content: 'Minecraft-Server ist nicht erreichbar.',
            flags: MessageFlags.Ephemeral });
        } else {
          const modal = new ModalBuilder().setCustomId('svadmin:submit:' + operation)
            .setTitle(operation === 'add' ? 'Verknüpfung hinzufügen' : 'Verknüpfung trennen');
          modal.addComponents(new ActionRowBuilder().addComponents(
            new TextInputBuilder().setCustomId('mc')
              .setLabel(operation === 'add' ? 'Minecraft-Name oder UUID' : 'Minecraft-Name, UUID oder Discord-ID')
              .setStyle(TextInputStyle.Short).setRequired(true).setMaxLength(64)));
          if (operation === 'add') modal.addComponents(new ActionRowBuilder().addComponents(
            new TextInputBuilder().setCustomId('discord')
              .setLabel('Discord-Benutzer-ID (im Server)')
              .setStyle(TextInputStyle.Short).setRequired(true).setMaxLength(20)));
          await interaction.showModal(modal);
        }
      }
      return true;
    }
    if (!interaction.isModalSubmit() || !interaction.customId.startsWith('svadmin:submit:')) return true;
    await interaction.deferReply({ flags: MessageFlags.Ephemeral });
    try {
      if (!this.isOnline()) throw new Error('Minecraft-Server ist nicht erreichbar.');
      if (this.state.actions.length >= 30) throw new Error('Zu viele offene Aufträge.');
      const operation = interaction.customId.slice('svadmin:submit:'.length);
      const query = interaction.fields.getTextInputValue('mc').trim();
      let action;
      if (operation === 'remove') {
        const linked = findLink(this.links, query);
        action = { type: 'unlink', mc: linked.uuid, discordId: linked.discordId };
      } else if (operation === 'add') {
        if (!MC_NAME.test(query) && !UUID.test(query)) {
          throw new Error('Minecraft-Name oder UUID ist ungültig.');
        }
        const discordId = interaction.fields.getTextInputValue('discord').trim();
        if (!SNOWFLAKE.test(discordId)) throw new Error('Discord-ID ist ungültig.');
        const member = await this.guild.members.fetch(discordId)
          .catch(() => { throw new Error('Discord-Konto ist nicht auf diesem Server.'); });
        action = { type: 'link', mc: query, discordId, discordUsername: member.user.username };
      } else throw new Error('Unbekannte Aktion.');
      this.state.actions.push({ id: crypto.randomUUID(), actorId: interaction.user.id, ...action });
      this.save();
      this.render().catch(error => console.error('[SkyZVerify] Admin-Panel:', error));
      await interaction.editReply('Auftrag angenommen. Der Minecraft-Server verarbeitet ihn in wenigen Sekunden.');
    } catch (error) {
      await interaction.editReply(error.message);
    }
    return true;
  }
}

module.exports = { AdminPanel, validateSnapshot, findLink };
