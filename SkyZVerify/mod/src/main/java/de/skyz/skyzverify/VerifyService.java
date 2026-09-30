package de.skyz.skyzverify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/** All map and player access stays on the Minecraft server thread. */
public final class VerifyService {
    private static final long REQUEST_MS = 5L * 60L * 1000L;
    private static final long COOLDOWN_MS = 20L * 1000L;
    private final MinecraftServer server;
    private final LinkStore store;
    private final BridgeClient bridge;
    private final VerifyConfig config;
    private final ExecutorService worker = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "SkyZVerify-Bridge");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<UUID, Pending> pending = new HashMap<UUID, Pending>();
    private final Map<UUID, Long> lastAttempt = new HashMap<UUID, Long>();
    private long ticks;
    private boolean adminSyncing;

    private static final class AdminResult {
        final String id;
        final boolean ok;
        final String message;
        AdminResult(String id, boolean ok, String message) {
            this.id = id;
            this.ok = ok;
            this.message = message;
        }
    }

    private static final class Pending {
        final UUID playerId;
        final String requestId = UUID.randomUUID().toString();
        final String discordUsername;
        final long expires = System.currentTimeMillis() + REQUEST_MS;
        String discordId;
        boolean polling;

        Pending(UUID playerId, String discordUsername) {
            this.playerId = playerId;
            this.discordUsername = discordUsername;
        }
    }

    public VerifyService(MinecraftServer server, LinkStore store, VerifyConfig config) {
        this.server = server;
        this.store = store;
        this.config = config;
        this.bridge = new BridgeClient(config);
    }

    public LinkStore store() {
        return store;
    }

    public boolean locked(EntityPlayerMP player) {
        return !store.isLinked(player.getUniqueID());
    }

    public void joined(EntityPlayerMP player) {
        try {
            store.remember(player.getUniqueID(), player.getName());
        } catch (IOException exception) {
            System.err.println("[SkyZVerify] Spielernamen konnten nicht gespeichert werden: " + exception.getMessage());
            tell(player, "Speicherfehler. Bitte Admin kontaktieren.");
        }
        if (locked(player)) prompt(player);
    }

    public void prompt(EntityPlayerMP player) {
        showTitle(player);
        tell(player, "Willkommen! Gib /verify <Discord-Username> ein, zum Beispiel /verify jsky.");
        tell(player, "Der Bot erstellt einen privaten Kanal auf unserem Discord und markiert dein Konto.");
        tell(player, "Klicke dort selbst auf 'Ja, ich bin das'. Danach wirst du hier freigeschaltet.");
        if (!config.discordInvite.isEmpty()) tell(player, "Discord: " + config.discordInvite);
        tell(player, "Bis dahin sind Bewegung und Aktionen gesperrt. Die Anfrage gilt 5 Minuten.");
    }

    public void showTitle(EntityPlayerMP player) {
        player.connection.sendPacket(new SPacketTitle(10, 100, 10));
        player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TITLE,
                new TextComponentString("Discord-Verifizierung")));
        player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.SUBTITLE,
                new TextComponentString("/verify <Discord-Username>")));
    }

    public void verify(EntityPlayerMP player, String requestedUser) {
        if (!locked(player)) {
            tell(player, "Du bist bereits verifiziert.");
            return;
        }
        String username = requestedUser.toLowerCase(Locale.ROOT);
        if (!LinkStore.validDiscordUsername(username)) {
            tell(player, "Bitte deinen exakten Discord-Username ohne @ angeben.");
            return;
        }
        if (pending.containsKey(player.getUniqueID())) {
            tell(player, "Es läuft bereits eine Anfrage. Prüfe deinen privaten Discord-Kanal.");
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastAttempt.get(player.getUniqueID());
        if (last != null && now - last < COOLDOWN_MS) {
            tell(player, "Bitte warte kurz vor der nächsten Anfrage.");
            return;
        }
        lastAttempt.put(player.getUniqueID(), now);
        final Pending request = new Pending(player.getUniqueID(), username);
        final String minecraftName = player.getName();
        pending.put(player.getUniqueID(), request);
        tell(player, "Anfrage wird an Discord gesendet ...");
        worker.execute(() -> {
            try {
                JsonObject result = bridge.create(request.requestId, request.playerId.toString(),
                        minecraftName, username);
                String status = BridgeClient.field(result, "status");
                String discordId = BridgeClient.field(result, "discordId");
                if (!("pending".equals(status) || "confirmed".equals(status))
                        || !LinkStore.validDiscordId(discordId)) {
                    throw new IOException("Bot hat die Anfrage nicht bestätigt");
                }
                server.addScheduledTask(() -> {
                    if (pending.get(request.playerId) != request) {
                        dismiss(request);
                        return;
                    }
                    if (store.isLinked(request.playerId)) {
                        cancel(request.playerId);
                        return;
                    }
                    request.discordId = discordId;
                    EntityPlayerMP current = server.getPlayerList().getPlayerByUUID(request.playerId);
                    if ("confirmed".equals(status)) {
                        onStatus(request, status, discordId);
                    } else if (current != null) {
                        tell(current, "Der Bot hat dein Discord-Konto gefunden. Bestätige im privaten Kanal die Anfrage.");
                    }
                });
            } catch (IOException exception) {
                String message = exception.getMessage();
                try { bridge.dismiss(request.requestId); } catch (IOException ignored) { /* Bot TTL cleans up. */ }
                server.addScheduledTask(() -> {
                    if (pending.remove(request.playerId, request)) {
                        EntityPlayerMP current = server.getPlayerList().getPlayerByUUID(request.playerId);
                        if (current != null) tell(current, "Verifizierung fehlgeschlagen: " + message);
                    }
                });
            }
        });
    }

    public void tick() {
        ticks++;
        if (ticks % 20 == 0 && !adminSyncing) syncAdmin(ticks % 40 == 0);
        if (ticks % 40 != 0) return;
        for (Pending request : new ArrayList<Pending>(pending.values())) {
            if (request.expires < System.currentTimeMillis()) {
                EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(request.playerId);
                if (player != null) tell(player, "Anfrage abgelaufen. Du kannst /verify erneut nutzen.");
                cancel(request.playerId);
            } else if (request.discordId != null && !request.polling) {
                request.polling = true;
                worker.execute(() -> {
                    try {
                        JsonObject response = bridge.status(request.requestId);
                        String state = BridgeClient.field(response, "status");
                        String discordId = BridgeClient.field(response, "discordId");
                        server.addScheduledTask(() -> onStatus(request, state, discordId));
                    } catch (IOException exception) {
                        server.addScheduledTask(() -> {
                            if (pending.get(request.playerId) == request) request.polling = false;
                        });
                    }
                });
            }
        }
    }

    private void syncAdmin(boolean includeSnapshot) {
        adminSyncing = true;
        final List<LinkStore.Result> snapshot = includeSnapshot ? store.allLinks() : null;
        worker.execute(() -> {
            if (snapshot != null) {
                try { bridge.adminSnapshot(snapshot); }
                catch (IOException exception) { System.err.println("[SkyZVerify] Admin-Übersicht: " + exception.getMessage()); }
            }
            JsonObject actions = null;
            try { actions = bridge.adminActions(); }
            catch (IOException exception) { System.err.println("[SkyZVerify] Admin-Aktionen: " + exception.getMessage()); }
            final JsonObject received = actions;
            server.addScheduledTask(() -> {
                List<AdminResult> results = new ArrayList<AdminResult>();
                if (received != null) {
                    try {
                        JsonArray list = received.getAsJsonArray("actions");
                        if (list == null || list.size() > 10) throw new IllegalArgumentException("Ungueltige Aktionen");
                        for (JsonElement element : list) {
                            if (element.isJsonObject()) results.add(applyAdminAction(element.getAsJsonObject()));
                        }
                    } catch (RuntimeException exception) {
                        System.err.println("[SkyZVerify] Admin-Aktionen ungueltig: " + exception.getMessage());
                    }
                }
                worker.execute(() -> {
                    for (AdminResult result : results) {
                        try { bridge.adminResult(result.id, result.ok, result.message); }
                        catch (IOException exception) {
                            System.err.println("[SkyZVerify] Admin-Antwort: " + exception.getMessage());
                        }
                    }
                    server.addScheduledTask(() -> adminSyncing = false);
                });
            });
        });
    }

    private AdminResult applyAdminAction(JsonObject action) {
        String id = null;
        try {
            id = BridgeClient.field(action, "id");
            UUID.fromString(id);
            String type = BridgeClient.field(action, "type");
            String target = BridgeClient.field(action, "mc");
            String discordId = BridgeClient.field(action, "discordId");
            String actor = BridgeClient.field(action, "actorId");
            if (!LinkStore.validDiscordId(discordId) || !LinkStore.validDiscordId(actor)) {
                throw new IllegalArgumentException("Ungueltige Discord-ID");
            }
            LinkStore.Result found = store.findOne(target);
            if ("link".equals(type)) {
                String username = BridgeClient.field(action, "discordUsername");
                if (found == null) throw new IllegalArgumentException("Minecraft-Spieler muss einmal beigetreten sein");
                if (found.link != null) {
                    if (!found.link.discordId.equals(discordId)) throw new IllegalArgumentException("Spieler ist bereits verknuepft");
                    return new AdminResult(id, true, found.playerName + " ist bereits verknuepft.");
                }
                store.link(found.uuid, discordId, username);
                onAdminChanged(found.uuid);
                System.out.println("[SkyZVerify] Discord-Admin " + actor + " verknuepfte " + found.uuid + " mit " + discordId);
                return new AdminResult(id, true, found.playerName + " wurde verknuepft.");
            }
            if ("unlink".equals(type)) {
                if (found == null || found.link == null) return new AdminResult(id, true, "Verknuepfung bereits entfernt.");
                if (!found.link.discordId.equals(discordId)) {
                    throw new IllegalArgumentException("Verknuepfung hat sich inzwischen geaendert");
                }
                store.unlink(found.uuid.toString());
                onAdminUnlinked(found.uuid);
                System.out.println("[SkyZVerify] Discord-Admin " + actor + " loeschte Verknuepfung " + found.uuid);
                return new AdminResult(id, true, found.playerName + " wurde getrennt und im Spiel gesperrt.");
            }
            throw new IllegalArgumentException("Unbekannte Aktion");
        } catch (IOException | IllegalArgumentException exception) {
            return new AdminResult(id, false, exception.getMessage());
        }
    }

    private void onStatus(Pending request, String state, String discordId) {
        if (pending.get(request.playerId) != request) return;
        request.polling = false;
        if (!discordId.equals(request.discordId)) {
            cancel(request.playerId);
            return;
        }
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(request.playerId);
        if (player == null) {
            cancel(request.playerId);
            return;
        }
        if ("confirmed".equals(state)) {
            try {
                store.link(request.playerId, discordId, request.discordUsername);
                cancel(request.playerId);
                unlocked(player);
            } catch (IOException | IllegalArgumentException exception) {
                tell(player, "Verknüpfung konnte nicht gespeichert werden: " + exception.getMessage());
                cancel(request.playerId);
            }
        } else if ("expired".equals(state) || "missing".equals(state)) {
            tell(player, "Die Discord-Anfrage ist abgelaufen. Bitte /verify erneut nutzen.");
            cancel(request.playerId);
        }
    }

    public void onAdminChanged(UUID uuid) {
        cancel(uuid);
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(uuid);
        if (player == null) return;
        if (locked(player)) prompt(player);
        else unlocked(player);
    }

    public void onAdminUnlinked(UUID uuid) {
        cancel(uuid);
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(uuid);
        if (player != null) {
            player.connection.disconnect(new TextComponentString(
                    "Deine Discord-Verknüpfung wurde entfernt. Verbinde dich erneut und nutze /verify."));
        }
    }

    private void unlocked(EntityPlayerMP player) {
        player.removePotionEffect(MobEffects.BLINDNESS);
        player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TITLE,
                new TextComponentString("Verifiziert!")));
        player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.SUBTITLE,
                new TextComponentString("Viel Spaß auf dem Server")));
        tell(player, "Dein Discord-Konto ist dauerhaft verknüpft. Viel Spaß!");
    }

    public void left(UUID uuid) {
        cancel(uuid);
        lastAttempt.remove(uuid);
    }

    private void cancel(UUID uuid) {
        Pending request = pending.remove(uuid);
        if (request != null) dismiss(request);
    }

    private void dismiss(Pending request) {
        worker.execute(() -> {
            try { bridge.dismiss(request.requestId); } catch (IOException ignored) { /* Bot TTL cleans up. */ }
        });
    }

    public void stop() {
        worker.shutdownNow();
        pending.clear();
    }

    public static void tell(EntityPlayerMP player, String text) {
        player.sendMessage(new TextComponentString("[SkyZVerify] " + text));
    }
}
