package de.skyz.skyzverify;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;

@Mod(modid = SkyZVerify.MODID, name = "SkyZVerify", version = "1.0.0",
        serverSideOnly = true, acceptableRemoteVersions = "*",
        acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:forge@[14.23.5.2859,)")
public final class SkyZVerify {
    public static final String MODID = "skyzverify";
    private VerifyService service;
    private GateEvents gate;

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        Path configDir = event.getServer().getDataDirectory().toPath().resolve("config");
        try {
            VerifyConfig config = VerifyConfig.load(configDir.resolve("skyzverify.properties"));
            LinkStore store = new LinkStore(configDir.resolve("skyzverify-links.properties"));
            service = new VerifyService(event.getServer(), store, config);
            gate = new GateEvents(service);
            MinecraftForge.EVENT_BUS.register(gate);
        } catch (IOException exception) {
            throw new IllegalStateException("SkyZVerify konnte Konfiguration oder Verknüpfungen nicht lesen", exception);
        }
        event.registerServerCommand(new VerifyCommands.Verify(service));
        event.registerServerCommand(new VerifyCommands.Search(service));
        event.registerServerCommand(new VerifyCommands.Link(service));
        event.registerServerCommand(new VerifyCommands.Unlink(service));
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (gate != null) MinecraftForge.EVENT_BUS.unregister(gate);
        if (service != null) service.stop();
    }
}
