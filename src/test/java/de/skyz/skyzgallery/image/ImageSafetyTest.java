package de.skyz.skyzgallery.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Self-contained safety checks; no Minecraft/Forge dependency needed. */
public final class ImageSafetyTest {

    private ImageSafetyTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("skyzgallery-test-");
        ImageLibrary.initialize(root);
        byte[] png = png(32, 24);
        Files.write(ImageLibrary.directory().resolve("test.png"), png);
        ImageLibrary.ImageFile good = ImageLibrary.load("test.png");
        require(good.hash.equals(ImageSafety.sha256(png)) && Arrays.equals(good.bytes, png),
                "PNG import/checksum");
        require(ImageLibrary.loadMatching("test.png", good.hash).hash.equals(good.hash),
                "matching picture can be served");
        try {
            ImageLibrary.loadMatching("test.png", ImageSafety.sha256(new byte[] { 12 }));
            throw new AssertionError("stale picture accepted");
        } catch (IOException expected) {
            // expected
        }
        require(ImageSafety.validBlocks(8, 8) && !ImageSafety.validBlocks(0, 1)
                && !ImageSafety.validBlocks(9, 1), "block dimensions");
        try {
            ImageLibrary.load("../test.png");
            throw new AssertionError("relative path accepted");
        } catch (IOException expected) {
            // expected
        }
        try {
            ImageSafety.checkImage(png(1025, 16));
            throw new AssertionError("oversized image accepted");
        } catch (IOException expected) {
            // expected
        }
        try {
            ImageSafety.checkImage(new byte[ImageSafety.MAX_FILE_BYTES + 1]);
            throw new AssertionError("oversized file accepted");
        } catch (IOException expected) {
            // expected
        }
        byte[] changed = Arrays.copyOf(png, png.length);
        changed[changed.length - 1] ^= 1;
        require(!ImageSafety.sha256(png).equals(ImageSafety.sha256(changed)), "changed content hash");
        byte[] invalid = { 1, 2, 3 };
        try {
            ImageSafety.checkImage(invalid);
            throw new AssertionError("non-image accepted");
        } catch (IOException expected) {
            // expected
        }
        System.out.println("SkyZGallery: Bildpruefungen bestanden.");
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output);
        return output.toByteArray();
    }

    private static void require(boolean value, String description) {
        if (!value) {
            throw new AssertionError(description);
        }
    }
}
