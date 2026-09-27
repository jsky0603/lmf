package de.skyz.skyzgallery.item;

import de.skyz.skyzgallery.SkyZGallery;
import de.skyz.skyzgallery.entity.EntityWallImage;
import de.skyz.skyzgallery.image.ImageLibrary;
import de.skyz.skyzgallery.image.ImageReference;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

public final class ImageItem extends Item {

    public static final ImageItem INSTANCE = new ImageItem();

    private ImageItem() {
        setRegistryName(SkyZGallery.MODID, "image");
        setUnlocalizedName(SkyZGallery.MODID + ".image");
        setMaxStackSize(1);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
            EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!facing.getAxis().isHorizontal()) {
            return EnumActionResult.FAIL;
        }
        ItemStack stack = player.getHeldItem(hand);
        ImageReference reference = ImageReference.fromStack(stack);
        BlockPos picturePos = pos.offset(facing);
        if (reference == null || !player.canPlayerEdit(picturePos, facing, stack)) {
            return EnumActionResult.FAIL;
        }
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        try {
            ImageLibrary.loadMatching(reference.fileName, reference.hash);
        } catch (IOException exception) {
            player.sendStatusMessage(new TextComponentString(exception.getMessage()), true);
            return EnumActionResult.FAIL;
        }
        EntityWallImage image = new EntityWallImage(world, picturePos, facing, reference);
        if (!image.onValidSurface() || !world.spawnEntity(image)) {
            player.sendStatusMessage(new TextComponentString("Hier ist nicht genug Platz oder keine feste Wand."), true);
            return EnumActionResult.FAIL;
        }
        image.playPlaceSound();
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world,
            List<String> tooltip, ITooltipFlag flag) {
        ImageReference reference = ImageReference.fromStack(stack);
        if (reference == null) {
            tooltip.add("Noch kein Bild zugewiesen");
        } else {
            tooltip.add(reference.width + " x " + reference.height + " Blöcke");
            tooltip.add("Datei: " + reference.fileName);
            tooltip.add("/imagesize <Breite> <Höhe> zum Anpassen");
        }
    }
}
