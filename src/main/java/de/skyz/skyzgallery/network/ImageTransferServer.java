package de.skyz.skyzgallery.network;

import de.skyz.skyzgallery.entity.EntityWallImage;
import de.skyz.skyzgallery.image.ImageLibrary;
import de.skyz.skyzgallery.image.ImageReference;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ImageTransferServer {

    public static final ImageTransferServer INSTANCE = new ImageTransferServer();
    public static final int CHUNK_BYTES = 24 * 1024;
    private static final int CHUNKS_PER_TICK = 8;
    private static final int MIN_REQUEST_TICKS = 4;

    private final Deque<Job> jobs = new ArrayDeque<Job>();
    private final Map<UUID, Long> lastRequest = new HashMap<UUID, Long>();

    private ImageTransferServer() {
    }

    /** Called exclusively on the main server thread. */
    public void queue(EntityPlayerMP player, String hash) {
        long tick = player.world.getTotalWorldTime();
        Long previous = lastRequest.get(player.getUniqueID());
        if (previous != null && tick >= previous && tick - previous < MIN_REQUEST_TICKS) {
            return;
        }
        lastRequest.put(player.getUniqueID(), tick);

        int active = 0;
        for (Job job : jobs) {
            if (job.player == player) {
                active++;
                if (job.hash.equals(hash)) {
                    return;
                }
            }
        }
        if (active >= 2) {
            return;
        }

        // Only serve files belonging to a real, nearby painting in loaded chunks.
        List<EntityWallImage> images = player.world.getEntitiesWithinAABB(EntityWallImage.class,
                player.getEntityBoundingBox().grow(80.0D));
        for (EntityWallImage image : images) {
            ImageReference reference = image.getReference();
            if (reference != null && hash.equals(reference.hash)
                    && image.getDistanceSq(player) <= 80.0D * 80.0D) {
                try {
                    ImageLibrary.ImageFile file = ImageLibrary.loadMatching(reference.fileName, reference.hash);
                    jobs.addLast(new Job(player, file));
                } catch (IOException ignored) {
                    // The file has been removed or changed; never send unverified content.
                }
                return;
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        int budget = CHUNKS_PER_TICK;
        while (budget-- > 0 && !jobs.isEmpty()) {
            Job job = jobs.removeFirst();
            if (job.player.connection == null) {
                continue;
            }
            try {
                job.sendNext();
                if (!job.finished()) {
                    jobs.addLast(job);
                }
            } catch (RuntimeException ignored) {
                // A disconnected client must not interrupt the server tick.
            }
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        jobs.removeIf(job -> job.player == event.player);
        lastRequest.remove(event.player.getUniqueID());
    }

    private static final class Job {
        private final EntityPlayerMP player;
        private final String hash;
        private final byte[] bytes;
        private final int total;
        private int nextIndex;

        private Job(EntityPlayerMP player, ImageLibrary.ImageFile file) {
            this.player = player;
            this.hash = file.hash;
            this.bytes = file.bytes;
            this.total = (bytes.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        }

        private void sendNext() {
            int offset = nextIndex * CHUNK_BYTES;
            int length = Math.min(CHUNK_BYTES, bytes.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(bytes, offset, chunk, 0, length);
            ImageNetwork.CHANNEL.sendTo(new ImageNetwork.Chunk(hash, nextIndex, total,
                    bytes.length, chunk), player);
            nextIndex++;
        }

        private boolean finished() {
            return nextIndex >= total;
        }
    }
}
