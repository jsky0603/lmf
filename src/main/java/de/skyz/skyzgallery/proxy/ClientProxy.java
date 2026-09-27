package de.skyz.skyzgallery.proxy;

import de.skyz.skyzgallery.SkyZGallery;
import de.skyz.skyzgallery.client.ClientEvents;
import de.skyz.skyzgallery.client.ClientImageCache;
import de.skyz.skyzgallery.client.RenderWallImage;
import de.skyz.skyzgallery.entity.EntityWallImage;
import de.skyz.skyzgallery.item.ImageItem;
import de.skyz.skyzgallery.network.ImageNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntityWallImage.class, RenderWallImage::new);
        ClientEvents events = new ClientEvents();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(events);
    }

    @SubscribeEvent
    public void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(ImageItem.INSTANCE, 0,
                new ModelResourceLocation(SkyZGallery.MODID + ":image", "inventory"));
    }

    @Override
    public void receiveChunk(final ImageNetwork.Chunk packet) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ClientImageCache.INSTANCE.receive(packet);
            }
        });
    }
}
