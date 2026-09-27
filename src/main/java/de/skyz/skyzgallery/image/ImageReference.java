package de.skyz.skyzgallery.image;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** All item and painting metadata is preserved in NBT, including the original file hash. */
public final class ImageReference {

    public final String fileName;
    public final String hash;
    public final String title;
    public final int width;
    public final int height;

    public ImageReference(String fileName, String hash, String title, int width, int height) {
        this.fileName = fileName;
        this.hash = hash;
        this.title = title;
        this.width = width;
        this.height = height;
    }

    public ImageReference resize(int newWidth, int newHeight) {
        return new ImageReference(fileName, hash, title, newWidth, newHeight);
    }

    public void writeToStack(ItemStack stack) {
        NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        root.setTag("SkyZGallery", write());
        stack.setTagCompound(root);
        stack.setStackDisplayName(title);
    }

    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("file", fileName);
        data.setString("sha256", hash);
        data.setString("title", title);
        data.setInteger("width", width);
        data.setInteger("height", height);
        return data;
    }

    public static ImageReference fromStack(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()
                || !stack.getTagCompound().hasKey("SkyZGallery", 10)) {
            return null;
        }
        return read(stack.getTagCompound().getCompoundTag("SkyZGallery"));
    }

    public static ImageReference read(NBTTagCompound data) {
        if (data == null || !data.hasKey("file", 8) || !data.hasKey("sha256", 8)
                || !data.hasKey("title", 8) || !data.hasKey("width", 3)
                || !data.hasKey("height", 3)) {
            return null;
        }
        String file = data.getString("file");
        String hash = data.getString("sha256");
        String title = data.getString("title");
        int width = data.getInteger("width");
        int height = data.getInteger("height");
        if (file.length() > 110 || !ImageSafety.validHash(hash)
                || title.trim().isEmpty() || title.length() > 80
                || !ImageSafety.validBlocks(width, height)) {
            return null;
        }
        return new ImageReference(file, hash, title, width, height);
    }
}
