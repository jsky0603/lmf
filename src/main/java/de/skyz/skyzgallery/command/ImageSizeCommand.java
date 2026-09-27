package de.skyz.skyzgallery.command;

import de.skyz.skyzgallery.image.ImageReference;
import de.skyz.skyzgallery.image.ImageSafety;
import de.skyz.skyzgallery.item.ImageItem;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;

public final class ImageSizeCommand extends CommandBase {

    @Override
    public String getName() {
        return "imagesize";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/imagesize <Breite 1-8> <Höhe 1-8> (Galeriebild in der Haupthand halten)";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] arguments)
            throws CommandException {
        if (arguments.length != 2) {
            throw new CommandException(getUsage(sender));
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        ItemStack held = player.getHeldItemMainhand();
        ImageReference reference = ImageReference.fromStack(held);
        if (held.getItem() != ImageItem.INSTANCE || reference == null) {
            throw new CommandException("Halte ein gültiges Galeriebild in der Haupthand.");
        }
        int width = parseInt(arguments[0]);
        int height = parseInt(arguments[1]);
        if (!ImageSafety.validBlocks(width, height)) {
            throw new CommandException("Breite und Höhe müssen zwischen 1 und 8 Blöcken liegen.");
        }
        reference.resize(width, height).writeToStack(held);
        player.setHeldItem(EnumHand.MAIN_HAND, held);
        player.inventoryContainer.detectAndSendChanges();
        player.sendMessage(new TextComponentString("Bildgröße: " + width + " x " + height + " Blöcke."));
    }
}
