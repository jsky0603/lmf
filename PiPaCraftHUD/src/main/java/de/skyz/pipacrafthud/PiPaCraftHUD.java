package de.skyz.pipacrafthud;

import io.netty.buffer.Unpooled;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.*;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatList;
import net.minecraft.util.text.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.*;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Mod(modid = "pipacrafthud", name = "PiPaCraft HUD", version = "1.0.0",
        acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "*", serverSideOnly = true)
public final class PiPaCraftHUD {
    private HudConfig config;
    private File configFile;
    private Logger log;
    private MinecraftServer server;
    private long ticks;
    private final Set<UUID> live = new HashSet<>(), hidden = new HashSet<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, View> views = new HashMap<>();

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        log = event.getModLog();
        configFile = new File(event.getModConfigurationDirectory(), "pipacrafthud.json");
        try { config = HudConfig.load(configFile); }
        catch (Exception ex) {
            log.error("PiPaCraft HUD: Config ungueltig. Datei bleibt erhalten; nutze Defaults.", ex);
            config = new HudConfig();
        }
        MinecraftForge.EVENT_BUS.register(this);
    }
    @Mod.EventHandler public void starting(FMLServerStartingEvent event) {
        server = event.getServer(); ticks = 0;
        event.registerServerCommand(new LiveCommand());
        event.registerServerCommand(new HudCommand());
    }
    @Mod.EventHandler public void stopping(FMLServerStoppingEvent event) {
        for (EntityPlayerMP p : players()) {
            View view = views.get(p.getUniqueID());
            if (view != null) view.clear(p);
        }
        views.clear(); live.clear(); hidden.clear(); cooldowns.clear(); server = null;
    }
    private List<EntityPlayerMP> players() {
        return server == null ? Collections.emptyList() : server.getPlayerList().getPlayers();
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (server == null || event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (ticks % config.updateTicks != 0) return;
        for (EntityPlayerMP p : players()) {
            try { views.computeIfAbsent(p.getUniqueID(), id -> new View()).update(p); }
            catch (RuntimeException ex) { log.error("PiPaCraft HUD: Anzeige fuer {} fehlgeschlagen.", p.getName(), ex); }
        }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.player.getUniqueID();
        live.remove(id); hidden.remove(id); cooldowns.remove(id); views.remove(id);
        for (View view : views.values()) view.lastTabNames.remove(id);
    }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { invalidate(event.player); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { invalidate(event.player); }
    private void invalidate(net.minecraft.entity.player.EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        View view = views.remove(player.getUniqueID());
        if (view != null) view.clear((EntityPlayerMP)player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void chat(ServerChatEvent event) {
        if (config.liveEnabled && config.chatPrefixEnabled && live.contains(event.getPlayer().getUniqueID()))
            event.setComponent(new TextComponentString(render(config.livePrefix, event.getPlayer())).appendSibling(event.getComponent()));
    }
    private Map<String, String> values(EntityPlayerMP p) {
        Map<String, String> vars = new LinkedHashMap<>();
        long nanos = 0; int count = 0;
        for (long time : server.tickTimeArray) if (time > 0) { nanos += time; count++; }
        double ms = count == 0 ? 50.0 : nanos / (double)count / 1000000.0;
        vars.put("tps", String.format(Locale.ROOT, "%.1f", Math.min(20, 1000.0 / Math.max(1, ms))));
        vars.put("mspt", String.format(Locale.ROOT, "%.1f", ms));
        vars.put("server", config.serverName); vars.put("player", p.getName());
        vars.put("online", Integer.toString(players().size()));
        vars.put("max_players", Integer.toString(server.getMaxPlayers()));
        vars.put("live_count", Integer.toString(live.size()));
        vars.put("live", live.contains(p.getUniqueID()) ? config.liveOnLabel : config.liveOffLabel);
        vars.put("live_prefix", live.contains(p.getUniqueID()) ? config.livePrefix : "");
        vars.put("ping", Integer.toString(Math.max(0, p.ping)));
        vars.put("health", String.format(Locale.ROOT, "%.0f", p.getHealth()));
        vars.put("food", Integer.toString(p.getFoodStats().getFoodLevel()));
        vars.put("level", Integer.toString(p.experienceLevel));
        vars.put("deaths", Integer.toString(p.getStatFile().readStat(StatList.DEATHS)));
        vars.put("playtime", HudText.playtime(p.getStatFile().readStat(StatList.PLAY_ONE_MINUTE)));
        vars.put("dimension", Integer.toString(p.dimension));
        vars.put("world", config.dimensionNames.getOrDefault(Integer.toString(p.dimension), p.world.provider.getDimensionType().getName()));
        vars.put("x", Integer.toString(p.getPosition().getX()));
        vars.put("y", Integer.toString(p.getPosition().getY()));
        vars.put("z", Integer.toString(p.getPosition().getZ()));
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(config.timezone));
        vars.put("time", now.format(DateTimeFormatter.ofPattern("HH:mm")));
        vars.put("date", now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        for (Map.Entry<String, String[]> e : config.animations.entrySet())
            vars.put("anim:" + e.getKey(), HudText.frame(e.getValue(), ticks / config.animationTicks));
        return vars;
    }
    private String render(String text, EntityPlayerMP p) { return HudText.render(text, values(p)); }
    private void message(EntityPlayerMP p, String text) { p.sendMessage(new TextComponentString(render(text, p))); }
    private void setLive(EntityPlayerMP p, boolean enabled) {
        if (enabled) live.add(p.getUniqueID()); else live.remove(p.getUniqueID());
        message(p, enabled ? config.livePersonalOn : config.livePersonalOff);
        if (config.liveAnnouncements)
            server.getPlayerList().sendMessage(new TextComponentString(render(enabled ? config.liveOnMessage : config.liveOffMessage, p)));
        for (EntityPlayerMP viewer : players()) views.computeIfAbsent(viewer.getUniqueID(), id -> new View()).update(viewer);
    }
    private void reload() throws Exception {
        HudConfig next = HudConfig.load(configFile); // Validate before replacing a working configuration.
        for (EntityPlayerMP p : players()) { View v = views.get(p.getUniqueID()); if (v != null) v.clear(p); }
        config = next; views.clear();
        if (!config.liveEnabled) live.clear();
    }
    private static void sendTabName(EntityPlayerMP viewer, UUID id, String text) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            buffer.writeEnumValue(SPacketPlayerListItem.Action.UPDATE_DISPLAY_NAME);
            buffer.writeVarInt(1); buffer.writeUniqueId(id); buffer.writeBoolean(text != null);
            if (text != null) buffer.writeTextComponent(new TextComponentString(text));
            SPacketPlayerListItem packet = new SPacketPlayerListItem(); packet.readPacketData(buffer);
            viewer.connection.sendPacket(packet);
        } catch (IOException ex) { throw new IllegalStateException(ex); }
        finally { buffer.release(); }
    }
    private static void sendHeader(EntityPlayerMP p, String header, String footer) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            buffer.writeTextComponent(new TextComponentString(header));
            buffer.writeTextComponent(new TextComponentString(footer));
            SPacketPlayerListHeaderFooter packet = new SPacketPlayerListHeaderFooter(); packet.readPacketData(buffer);
            p.connection.sendPacket(packet);
        } catch (IOException ex) { throw new IllegalStateException(ex); }
        finally { buffer.release(); }
    }
    private static ScoreObjective actualSidebar(EntityPlayerMP p) {
        Scoreboard board = p.getWorldScoreboard();
        ScorePlayerTeam team = board.getPlayersTeam(p.getName());
        if (team != null && team.getColor().getColorIndex() >= 0) {
            ScoreObjective colored = board.getObjectiveInDisplaySlot(3 + team.getColor().getColorIndex());
            if (colored != null) return colored;
        }
        return board.getObjectiveInDisplaySlot(1);
    }
    private final class View {
        private final Scoreboard board = new Scoreboard();
        private final ScoreObjective objective = board.addScoreObjective("pipa_hud", IScoreCriteria.DUMMY);
        private final List<ScorePlayerTeam> rows = new ArrayList<>();
        private final Map<UUID, ScorePlayerTeam> nameTeams = new HashMap<>();
        private final Map<UUID, String> lastTabNames = new HashMap<>();
        private boolean sidebarShown;
        private String lastHeader, lastFooter, lastTitle;

        void update(EntityPlayerMP p) {
            long frame = ticks / config.animationTicks;
            if (config.tabEnabled) {
                String header = render(HudText.frame(config.headerFrames, frame), p);
                String footer = render(HudText.frame(config.footerFrames, frame), p);
                if (!Objects.equals(header, lastHeader) || !Objects.equals(footer, lastFooter)) {
                    sendHeader(p, header, footer); lastHeader = header; lastFooter = footer;
                }
                for (EntityPlayerMP target : players()) {
                    String name = HudText.cut(render(config.tabPlayerFormat, target), 256);
                    if (!Objects.equals(name, lastTabNames.get(target.getUniqueID()))) {
                        sendTabName(p, target.getUniqueID(), name); lastTabNames.put(target.getUniqueID(), name);
                    }
                }
            }
            updateNameTags(p);
            boolean show = config.scoreboardEnabled && !hidden.contains(p.getUniqueID()) &&
                    (!config.protectExistingSidebar || actualSidebar(p) == null);
            if (!show) { hideSidebar(p); return; }
            String title = HudText.cut(render(HudText.frame(config.titleFrames, frame), p), 32);
            objective.setDisplayName(title);
            if (!sidebarShown) {
                p.connection.sendPacket(new SPacketScoreboardObjective(objective, 0));
                p.connection.sendPacket(new SPacketDisplayObjective(1, objective));
                sidebarShown = true; lastTitle = title;
            } else if (!title.equals(lastTitle)) {
                p.connection.sendPacket(new SPacketScoreboardObjective(objective, 2)); lastTitle = title;
            }
            for (int i = 0; i < config.scoreboardLines.length; i++) {
                String[] text = HudText.split(render(config.scoreboardLines[i], p));
                if (i >= rows.size()) {
                    ScorePlayerTeam row = board.createTeam("pipa_line_" + i);
                    String entry = "\u00a7" + Integer.toHexString(i) + "\u00a7r";
                    board.addPlayerToTeam(entry, row.getName()); row.setPrefix(text[0]); row.setSuffix(text[1]);
                    rows.add(row); p.connection.sendPacket(new SPacketTeams(row, 0));
                    Score score = board.getOrCreateScore(entry, objective);
                    score.setScorePoints(config.scoreboardLines.length - i);
                    p.connection.sendPacket(new SPacketUpdateScore(score));
                } else {
                    ScorePlayerTeam row = rows.get(i);
                    if (!row.getPrefix().equals(text[0]) || !row.getSuffix().equals(text[1])) {
                        row.setPrefix(text[0]); row.setSuffix(text[1]);
                        p.connection.sendPacket(new SPacketTeams(row, 2));
                    }
                }
            }
        }
        private void updateNameTags(EntityPlayerMP viewer) {
            Set<UUID> desired = new HashSet<>();
            for (EntityPlayerMP target : players()) {
                // One vanilla team per player: respect real server teams, including claim/PvP settings.
                if (!config.nameTagsEnabled || !live.contains(target.getUniqueID()) ||
                        target.getWorldScoreboard().getPlayersTeam(target.getName()) != null) continue;
                UUID id = target.getUniqueID(); desired.add(id);
                String prefix = HudText.cut(render(config.livePrefix, target), 16);
                ScorePlayerTeam team = nameTeams.get(id);
                if (team == null) {
                    team = board.createTeam("ph_" + id.toString().replace("-", "").substring(0, 12));
                    team.setPrefix(prefix); team.setAllowFriendlyFire(true); team.setSeeFriendlyInvisiblesEnabled(false);
                    board.addPlayerToTeam(target.getName(), team.getName()); nameTeams.put(id, team);
                    viewer.connection.sendPacket(new SPacketTeams(team, 0));
                } else if (!prefix.equals(team.getPrefix())) {
                    team.setPrefix(prefix); viewer.connection.sendPacket(new SPacketTeams(team, 2));
                }
            }
            Iterator<Map.Entry<UUID, ScorePlayerTeam>> it = nameTeams.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, ScorePlayerTeam> e = it.next();
                if (desired.contains(e.getKey())) continue;
                viewer.connection.sendPacket(new SPacketTeams(e.getValue(), 1));
                board.removeTeam(e.getValue()); it.remove();
                EntityPlayerMP target = server.getPlayerList().getPlayerByUUID(e.getKey());
                if (target != null) {
                    ScorePlayerTeam real = target.getWorldScoreboard().getPlayersTeam(target.getName());
                    if (real != null) viewer.connection.sendPacket(new SPacketTeams(real, Collections.singleton(target.getName()), 3));
                }
            }
        }
        private void hideSidebar(EntityPlayerMP p) {
            if (!sidebarShown) return;
            p.connection.sendPacket(new SPacketDisplayObjective(1, actualSidebar(p)));
            p.connection.sendPacket(new SPacketScoreboardObjective(objective, 1));
            for (ScorePlayerTeam row : rows) { p.connection.sendPacket(new SPacketTeams(row, 1)); board.removeTeam(row); }
            rows.clear(); sidebarShown = false; lastTitle = null;
        }
        void clear(EntityPlayerMP p) {
            hideSidebar(p);
            if (lastHeader != null) sendHeader(p, "", "");
            for (EntityPlayerMP target : players()) {
                if (lastTabNames.containsKey(target.getUniqueID())) sendTabName(p, target.getUniqueID(), null);
            }
            for (ScorePlayerTeam team : nameTeams.values()) p.connection.sendPacket(new SPacketTeams(team, 1));
            nameTeams.clear(); lastTabNames.clear();
        }
    }
    private final class LiveCommand extends CommandBase {
        public String getName() { return "live"; }
        public String getUsage(ICommandSender sender) { return "/live [on|off|status]"; }
        public int getRequiredPermissionLevel() { return 0; }
        public boolean checkPermission(MinecraftServer srv, ICommandSender sender) { return true; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            EntityPlayerMP p = getCommandSenderAsPlayer(sender);
            if (!config.liveEnabled) { message(p, config.liveDisabledMessage); return; }
            if (args.length > 1) throw new WrongUsageException(getUsage(sender));
            String arg = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);
            if (arg.equals("status")) { message(p, config.liveStatusMessage); return; }
            if (!Arrays.asList("toggle", "on", "off").contains(arg)) throw new WrongUsageException(getUsage(sender));
            boolean enabled = arg.equals("toggle") ? !live.contains(p.getUniqueID()) : arg.equals("on");
            if (enabled == live.contains(p.getUniqueID())) { message(p, config.liveStatusMessage); return; }
            long now = System.currentTimeMillis(), remaining = cooldowns.getOrDefault(p.getUniqueID(), 0L) - now;
            if (remaining > 0) { message(p, config.cooldownMessage.replace("{seconds}", Long.toString((remaining + 999) / 1000))); return; }
            cooldowns.put(p.getUniqueID(), now + config.liveCommandCooldownSeconds * 1000L); setLive(p, enabled);
        }
        public List<String> getTabCompletions(MinecraftServer srv, ICommandSender sender, String[] args, net.minecraft.util.math.BlockPos pos) {
            return args.length == 1 ? getListOfStringsMatchingLastWord(args, "on", "off", "status") : Collections.emptyList();
        }
    }
    private final class HudCommand extends CommandBase {
        public String getName() { return "pipahud"; }
        public String getUsage(ICommandSender sender) { return "/pipahud [on|off|reload]"; }
        public int getRequiredPermissionLevel() { return 0; }
        public boolean checkPermission(MinecraftServer srv, ICommandSender sender) { return true; }
        public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
            if (args.length > 1) throw new WrongUsageException(getUsage(sender));
            String arg = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);
            if (arg.equals("reload")) {
                if (!sender.canUseCommand(2, "pipahud")) throw new CommandException("commands.generic.permission");
                try { reload(); sender.sendMessage(new TextComponentString(HudText.color(config.reloadMessage))); }
                catch (Exception ex) { throw new CommandException("Config ungueltig; alte Config bleibt aktiv: " + ex.getMessage()); }
                return;
            }
            if (!Arrays.asList("toggle", "on", "off").contains(arg)) throw new WrongUsageException(getUsage(sender));
            EntityPlayerMP p = getCommandSenderAsPlayer(sender);
            boolean enabled = arg.equals("toggle") ? hidden.contains(p.getUniqueID()) : arg.equals("on");
            if (enabled) hidden.remove(p.getUniqueID()); else hidden.add(p.getUniqueID());
            message(p, enabled ? config.hudOnMessage : config.hudOffMessage);
            views.computeIfAbsent(p.getUniqueID(), id -> new View()).update(p);
        }
        public List<String> getTabCompletions(MinecraftServer srv, ICommandSender sender, String[] args, net.minecraft.util.math.BlockPos pos) {
            if (args.length != 1) return Collections.emptyList();
            return sender.canUseCommand(2, "pipahud") ? getListOfStringsMatchingLastWord(args, "on", "off", "reload") : getListOfStringsMatchingLastWord(args, "on", "off");
        }
    }
}
