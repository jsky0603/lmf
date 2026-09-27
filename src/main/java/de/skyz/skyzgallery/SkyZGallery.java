package de.skyz.skyzgallery;

import de.skyz.skyzgallery.command.CreateImageCommand;
import de.skyz.skyzgallery.command.ImageSizeCommand;
import de.skyz.skyzgallery.image.ImageLibrary;
import de.skyz.skyzgallery.network.ImageNetwork;
import de.skyz.skyzgallery.network.ImageTransferServer;
import de.skyz.skyzgallery.proxy.CommonProxy;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;

@Mod(modid = SkyZGallery.MODID, name = SkyZGallery.NAME, version = SkyZGallery.VERSION,
        acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:forge@[14.23.5.2859,)")
@Mod.EventBusSubscriber(modid = SkyZGallery.MODID)
public final class SkyZGallery {

    public static final String MODID = "skyzgallery";
    public static final String NAME = "SkyZGallery";
    public static final String VERSION = "1.0.0";

    @SidedProxy(clientSide = "de.skyz.skyzgallery.proxy.ClientProxy",
            serverSide = "de.skyz.skyzgallery.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.Instance(MODID)
    public static SkyZGallery instance;
    private static Path gameDirectory;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        gameDirectory = event.getModConfigurationDirectory().getParentFile().toPath();
        ImageNetwork.register();
        MinecraftForge.EVENT_BUS.register(ImageTransferServer.INSTANCE);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "wall_image"),
                de.skyz.skyzgallery.entity.EntityWallImage.class, "wall_image", 0, this, 128, 20, false);
        proxy.preInit();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        try {
            ImageLibrary.initialize(gameDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("SkyZGallery konnte den Bilderordner nicht erstellen.", exception);
        }
        event.registerServerCommand(new CreateImageCommand());
        event.registerServerCommand(new ImageSizeCommand());
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(de.skyz.skyzgallery.item.ImageItem.INSTANCE);
    }
}
