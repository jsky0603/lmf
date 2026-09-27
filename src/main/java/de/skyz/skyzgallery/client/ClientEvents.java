package de.skyz.skyzgallery.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/** Visual-only: armor inventory, equipment, stats and damage remain unchanged. */
public final class ClientEvents {

    private final Map<RenderPlayer, List<Object>> hidden = new IdentityHashMap<RenderPlayer, List<Object>>();
    private boolean warned;

    @SubscribeEvent
    public void onPlayerRender(RenderPlayerEvent.Pre event) {
        try {
            List<?> layers = ObfuscationReflectionHelper.getPrivateValue(
                    RenderLivingBase.class, event.getRenderer(), "field_177097_h");
            if (layers == null) {
                return;
            }
            // The inventory's 3D preview keeps its armor. The world model does not.
            if (Minecraft.getMinecraft().currentScreen instanceof GuiInventory
                    || Minecraft.getMinecraft().currentScreen instanceof GuiContainerCreative) {
                List<Object> previous = hidden.remove(event.getRenderer());
                if (previous != null) {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    List<Object> mutable = (List) layers;
                    mutable.addAll(0, previous);
                }
            } else {
                List<Object> removed = hidden.get(event.getRenderer());
                if (removed == null) {
                    removed = new ArrayList<Object>();
                    hidden.put(event.getRenderer(), removed);
                }
                for (Object layer : new ArrayList<Object>(layers)) {
                    if (layer instanceof LayerArmorBase) {
                        removed.add(layer);
                        layers.remove(layer);
                    }
                }
            }
        } catch (RuntimeException exception) {
            if (!warned) {
                warned = true;
                System.err.println("[SkyZGallery] Spielerruestung konnte nicht ausgeblendet werden: "
                        + exception.getMessage());
            }
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ClientImageCache.INSTANCE.clear();
            }
        });
    }
}
