package de.skyz.skyzverify;

import java.io.IOException;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public final class VerifyCommands {
    private VerifyCommands() { }

    private static void say(ICommandSender sender, String text) {
        sender.sendMessage(new TextComponentString("[SkyZVerify] " + text));
    }

    public static final class Verify extends CommandBase {
        private final VerifyService service;
        public Verify(VerifyService service) { this.service = service; }
        @Override public String getName() { return "verify"; }
        @Override public String getUsage(ICommandSender sender) { return "/verify <Discord-Username>"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) throw new CommandException(getUsage(sender));
            service.verify(getCommandSenderAsPlayer(sender), args[0]);
        }
    }

    public static final class Search extends CommandBase {
        private final VerifyService service;
        public Search(VerifyService service) { this.service = service; }
        @Override public String getName() { return "search"; }
        @Override public String getUsage(ICommandSender sender) { return "/search <Discord-ID|Minecraft-Name|UUID>"; }
        @Override public int getRequiredPermissionLevel() { return 2; }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) throw new CommandException(getUsage(sender));
            List<LinkStore.Result> found = service.store().search(args[0]);
            if (found.isEmpty()) { say(sender, "Keine Person gefunden."); return; }
            for (LinkStore.Result result : found) {
                say(sender, result.playerName + " | UUID " + result.uuid + " | "
                        + (result.link == null ? "nicht verknüpft"
                        : "Discord " + result.link.discordUsername + " (" + result.link.discordId + ")"));
            }
        }
    }

    public static final class Link extends CommandBase {
        private final VerifyService service;
        public Link(VerifyService service) { this.service = service; }
        @Override public String getName() { return "verifylink"; }
        @Override public String getUsage(ICommandSender sender) {
            return "/verifylink <Minecraft-Name|UUID> <Discord-ID> [Discord-Username]";
        }
        @Override public int getRequiredPermissionLevel() { return 2; }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length < 2 || args.length > 3) throw new CommandException(getUsage(sender));
            LinkStore.Result found;
            try { found = service.store().findOne(args[0]); }
            catch (IllegalArgumentException exception) { throw new CommandException(exception.getMessage()); }
            if (found == null) throw new CommandException("Spieler unbekannt; er muss einmal dem Server beitreten.");
            try {
                service.store().link(found.uuid, args[1], args.length == 3 ? args[2] : "manuell");
            } catch (IOException | IllegalArgumentException exception) {
                throw new CommandException(exception.getMessage());
            }
            service.onAdminChanged(found.uuid);
            say(sender, "Verknüpft: " + found.playerName + " mit Discord-ID " + args[1]);
            System.out.println("[SkyZVerify] Admin " + sender.getName() + " verknuepfte " + found.uuid + " mit " + args[1]);
        }
    }

    public static final class Unlink extends CommandBase {
        private final VerifyService service;
        public Unlink(VerifyService service) { this.service = service; }
        @Override public String getName() { return "verifyunlink"; }
        @Override public String getUsage(ICommandSender sender) { return "/verifyunlink <Minecraft-Name|UUID|Discord-ID>"; }
        @Override public int getRequiredPermissionLevel() { return 2; }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) throw new CommandException(getUsage(sender));
            LinkStore.Result found;
            try { found = service.store().unlink(args[0]); }
            catch (IOException | IllegalArgumentException exception) { throw new CommandException(exception.getMessage()); }
            service.onAdminChanged(found.uuid);
            say(sender, "Verknüpfung von " + found.playerName + " entfernt.");
            System.out.println("[SkyZVerify] Admin " + sender.getName() + " loeschte Verknuepfung von " + found.uuid);
        }
    }
}
