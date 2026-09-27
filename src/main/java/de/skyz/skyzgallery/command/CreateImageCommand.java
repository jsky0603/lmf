package de.skyz.skyzgallery.command;

import de.skyz.skyzgallery.image.ImageLibrary;
import de.skyz.skyzgallery.image.ImageReference;
import de.skyz.skyzgallery.image.ImageSafety;
import de.skyz.skyzgallery.item.ImageItem;
import java.io.IOException;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public final class CreateImageCommand extends CommandBase {

    @Override
    public String getName() {
        return "createimage";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/createimage <datei.png|jpg> <Breite 1-8> <Höhe 1-8> <Bildname...>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] arguments)
            throws CommandException {
        if (arguments.length < 4) {
            throw new CommandException(getUsage(sender));
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        int width = parseInt(arguments[1]);
        int height = parseInt(arguments[2]);
        if (!ImageSafety.validBlocks(width, height)) {
            throw new CommandException("Breite und Höhe müssen zwischen 1 und 8 Blöcken liegen.");
        }
        String title = buildString(arguments, 3).trim();
        if (title.isEmpty() || title.length() > 80) {
            throw new CommandException("Der Bildname muss 1 bis 80 Zeichen lang sein.");
        }
        try {
            ImageLibrary.ImageFile file = ImageLibrary.load(arguments[0]);
            ImageReference reference = new ImageReference(file.fileName, file.hash, title, width, height);
            ItemStack painting = new ItemStack(ImageItem.INSTANCE);
            reference.writeToStack(painting);
            if (!player.inventory.addItemStackToInventory(painting)) {
                player.dropItem(painting, false);
            }
            player.inventoryContainer.detectAndSendChanges();
            player.sendMessage(new TextComponentString("Bild erstellt: " + title + " ("
                    + width + " x " + height + " Blöcke)."));
        } catch (IOException exception) {
            throw new CommandException(exception.getMessage());
        }
    }
}
