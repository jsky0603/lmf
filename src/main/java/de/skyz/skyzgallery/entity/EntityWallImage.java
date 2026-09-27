package de.skyz.skyzgallery.entity;

import de.skyz.skyzgallery.image.ImageReference;
import de.skyz.skyzgallery.item.ImageItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

/** Behaves like a vanilla painting, but stores the server image and size in NBT. */
public final class EntityWallImage extends EntityHanging implements IEntityAdditionalSpawnData {

    private ImageReference reference;

    public EntityWallImage(World world) {
        super(world);
    }

    public EntityWallImage(World world, BlockPos position, EnumFacing facing, ImageReference reference) {
        super(world, position);
        this.reference = reference;
        updateFacingWithBoundingBox(facing);
    }

    public ImageReference getReference() {
        return reference;
    }

    @Override
    public int getWidthPixels() {
        return reference == null ? 16 : reference.width * 16;
    }

    @Override
    public int getHeightPixels() {
        return reference == null ? 16 : reference.height * 16;
    }

    @Override
    public void onBroken(Entity breaker) {
        playSound(SoundEvents.ENTITY_PAINTING_BREAK, 1.0F, 1.0F);
        if (world.isRemote || reference == null
                || (breaker instanceof EntityPlayer && ((EntityPlayer) breaker).capabilities.isCreativeMode)) {
            return;
        }
        ItemStack item = new ItemStack(ImageItem.INSTANCE);
        reference.writeToStack(item);
        entityDropItem(item, 0.0F);
    }

    @Override
    public void playPlaceSound() {
        playSound(SoundEvents.ENTITY_PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        if (reference != null) {
            tag.setTag("SkyZGallery", reference.write());
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        reference = ImageReference.read(tag.getCompoundTag("SkyZGallery"));
        super.readEntityFromNBT(tag);
        if (facingDirection != null) {
            updateFacingWithBoundingBox(facingDirection);
        }
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        BlockPos position = getHangingPosition();
        buffer.writeInt(position.getX());
        buffer.writeInt(position.getY());
        buffer.writeInt(position.getZ());
        buffer.writeByte(facingDirection == null ? EnumFacing.NORTH.getIndex() : facingDirection.getIndex());
        ByteBufUtils.writeTag(buffer, reference == null ? new NBTTagCompound() : reference.write());
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        hangingPosition = new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt());
        EnumFacing direction = EnumFacing.byIndex(buffer.readUnsignedByte());
        reference = ImageReference.read(ByteBufUtils.readTag(buffer));
        updateFacingWithBoundingBox(direction.getAxis().isHorizontal() ? direction : EnumFacing.NORTH);
    }
}
