package de.skyz.skyzgallery.image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Shared hard limits: network packets must never be able to relax these limits. */
public final class ImageSafety {

    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_PIXELS_PER_SIDE = 1024;
    public static final int MAX_BLOCKS_PER_SIDE = 8;

    private ImageSafety() {
    }

    public static boolean validHash(String hash) {
        return hash != null && hash.matches("[0-9a-f]{64}");
    }

    public static boolean validBlocks(int width, int height) {
        return width >= 1 && height >= 1
                && width <= MAX_BLOCKS_PER_SIDE && height <= MAX_BLOCKS_PER_SIDE;
    }

    public static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder text = new StringBuilder(64);
            for (byte value : digest) {
                text.append(Character.forDigit((value >>> 4) & 15, 16));
                text.append(Character.forDigit(value & 15, 16));
            }
            return text.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 ist nicht verfügbar", exception);
        }
    }

    public static void checkImage(byte[] data) throws IOException {
        if (data == null || data.length == 0 || data.length > MAX_FILE_BYTES) {
            throw new IOException("Das Bild muss zwischen 1 Byte und 2 MiB groß sein.");
        }
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (stream == null) {
                throw new IOException("Das Bild kann nicht gelesen werden.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new IOException("Nur PNG und JPEG werden unterstuetzt.");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!"png".equals(format) && !"jpeg".equals(format) && !"jpg".equals(format)) {
                    throw new IOException("Nur PNG und JPEG werden unterstuetzt.");
                }
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_PIXELS_PER_SIDE
                        || height > MAX_PIXELS_PER_SIDE) {
                    throw new IOException("Das Bild darf höchstens 1024 x 1024 Pixel groß sein.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
                    throw new IOException("Das Bild ist beschädigt.");
                }
            } finally {
                reader.dispose();
            }
        } catch (RuntimeException exception) {
            throw new IOException("Die Bilddatei ist ungültig oder beschädigt.", exception);
        }
    }
}
