package de.skyz.skyzgallery.client;

import de.skyz.skyzgallery.image.ImageSafety;
import de.skyz.skyzgallery.network.ImageNetwork;
import de.skyz.skyzgallery.network.ImageTransferServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

/** Bounded in-memory cache; every image is hash-checked before decoding. */
public final class ClientImageCache {

    public static final ClientImageCache INSTANCE = new ClientImageCache();
    private static final int MAX_TEXTURES = 24;
    private static final int MAX_INFLIGHT = 6;
    private static final long RETRY_MILLISECONDS = 5000L;

    private final Map<String, Assembly> inflight = new HashMap<String, Assembly>();
    private final Map<String, Long> requested = new HashMap<String, Long>();
    private final LinkedHashMap<String, ResourceLocation> textures =
            new LinkedHashMap<String, ResourceLocation>(32, 0.75F, true);

    private ClientImageCache() {
    }

    public ResourceLocation get(String hash) {
        if (!ImageSafety.validHash(hash)) {
            return null;
        }
        ResourceLocation texture = textures.get(hash);
        if (texture != null) {
            return texture;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || minecraft.world == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        Long previous = requested.get(hash);
        if (previous == null || now - previous >= RETRY_MILLISECONDS || now < previous) {
            requested.put(hash, now);
            ImageNetwork.CHANNEL.sendToServer(new ImageNetwork.Request(hash));
        }
        return null;
    }

    /** Always called on the Minecraft client thread, never on Netty's network thread. */
    public void receive(ImageNetwork.Chunk message) {
        if (!ImageSafety.validHash(message.hash) || message.byteCount < 1
                || message.byteCount > ImageSafety.MAX_FILE_BYTES || message.total < 1
                || message.total != (message.byteCount + ImageTransferServer.CHUNK_BYTES - 1)
                        / ImageTransferServer.CHUNK_BYTES
                || message.index < 0 || message.index >= message.total || message.bytes == null
                || message.bytes.length != Math.min(ImageTransferServer.CHUNK_BYTES,
                        message.byteCount - message.index * ImageTransferServer.CHUNK_BYTES)
                || textures.containsKey(message.hash)) {
            return;
        }
        Assembly job = inflight.get(message.hash);
        if (job == null) {
            if (inflight.size() >= MAX_INFLIGHT) {
                return;
            }
            job = new Assembly(message.byteCount, message.total);
            inflight.put(message.hash, job);
        } else if (job.length != message.byteCount || job.parts.length != message.total) {
            inflight.remove(message.hash);
            return;
        }
        if (job.parts[message.index] != null) {
            return;
        }
        job.parts[message.index] = message.bytes;
        if (++job.received != job.parts.length) {
            return;
        }
        inflight.remove(message.hash);
        byte[] combined = new byte[job.length];
        int offset = 0;
        for (byte[] part : job.parts) {
            System.arraycopy(part, 0, combined, offset, part.length);
            offset += part.length;
        }
        if (offset != combined.length || !ImageSafety.sha256(combined).equals(message.hash)) {
            return;
        }
        try {
            ImageSafety.checkImage(combined);
            BufferedImage picture = ImageIO.read(new ByteArrayInputStream(combined));
            if (picture == null || picture.getWidth() > ImageSafety.MAX_PIXELS_PER_SIDE
                    || picture.getHeight() > ImageSafety.MAX_PIXELS_PER_SIDE) {
                return;
            }
            TextureManager manager = Minecraft.getMinecraft().getTextureManager();
            ResourceLocation location = manager.getDynamicTextureLocation(
                    "skyzgallery_" + message.hash, new DynamicTexture(picture));
            textures.put(message.hash, location);
            requested.remove(message.hash);
            if (textures.size() > MAX_TEXTURES) {
                String oldest = textures.keySet().iterator().next();
                manager.deleteTexture(textures.remove(oldest));
            }
        } catch (IOException ignored) {
            // Unreadable images are treated like unavailable images, not as game crashes.
        }
    }

    public void clear() {
        TextureManager manager = Minecraft.getMinecraft().getTextureManager();
        for (ResourceLocation location : textures.values()) {
            manager.deleteTexture(location);
        }
        textures.clear();
        inflight.clear();
        requested.clear();
    }

    private static final class Assembly {
        private final byte[][] parts;
        private final int length;
        private int received;

        private Assembly(int length, int total) {
            this.parts = new byte[total][];
            this.length = length;
        }
    }
}
