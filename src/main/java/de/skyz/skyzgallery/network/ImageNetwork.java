package de.skyz.skyzgallery.network;

import de.skyz.skyzgallery.SkyZGallery;
import de.skyz.skyzgallery.image.ImageSafety;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class ImageNetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(SkyZGallery.MODID);

    private ImageNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(RequestHandler.class, Request.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ChunkHandler.class, Chunk.class, 1, Side.CLIENT);
    }

    public static final class Request implements IMessage {
        public String hash = "";

        public Request() {
        }

        public Request(String hash) {
            this.hash = hash;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            hash = ByteBufUtils.readUTF8String(buffer);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            ByteBufUtils.writeUTF8String(buffer, hash);
        }
    }

    public static final class Chunk implements IMessage {
        public String hash = "";
        public int index;
        public int total;
        public int byteCount;
        public byte[] bytes = new byte[0];

        public Chunk() {
        }

        public Chunk(String hash, int index, int total, int byteCount, byte[] bytes) {
            this.hash = hash;
            this.index = index;
            this.total = total;
            this.byteCount = byteCount;
            this.bytes = bytes;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            hash = ByteBufUtils.readUTF8String(buffer);
            index = buffer.readInt();
            total = buffer.readInt();
            byteCount = buffer.readInt();
            int length = buffer.readInt();
            if (length <= 0 || length > ImageTransferServer.CHUNK_BYTES || length > buffer.readableBytes()) {
                bytes = new byte[0];
                return;
            }
            bytes = new byte[length];
            buffer.readBytes(bytes);
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            ByteBufUtils.writeUTF8String(buffer, hash);
            buffer.writeInt(index);
            buffer.writeInt(total);
            buffer.writeInt(byteCount);
            buffer.writeInt(bytes.length);
            buffer.writeBytes(bytes);
        }
    }

    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {
        @Override
        public IMessage onMessage(final Request message, MessageContext context) {
            if (!ImageSafety.validHash(message.hash)) {
                return null;
            }
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    ImageTransferServer.INSTANCE.queue(player, message.hash);
                }
            });
            return null;
        }
    }

    public static final class ChunkHandler implements IMessageHandler<Chunk, IMessage> {
        @Override
        public IMessage onMessage(Chunk message, MessageContext context) {
            SkyZGallery.proxy.receiveChunk(message);
            return null;
        }
    }
}
