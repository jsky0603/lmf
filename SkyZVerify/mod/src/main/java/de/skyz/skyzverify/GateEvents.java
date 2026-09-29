package de.skyz.skyzverify;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Server-side gates; Forge clients do not need this mod. */
public final class GateEvents {
    private final VerifyService service;
    private final Map<UUID, Anchor> anchors = new HashMap<UUID, Anchor>();

    private static final class Anchor {
        final int dimension;
        final double x, y, z;
        final float yaw, pitch;
        Anchor(EntityPlayerMP player) {
            dimension = player.dimension;
            x = player.posX;
            y = player.posY;
            z = player.posZ;
            yaw = player.rotationYaw;
            pitch = player.rotationPitch;
        }
    }

    public GateEvents(VerifyService service) {
        this.service = service;
    }

    private boolean locked(net.minecraft.entity.player.EntityPlayer player) {
        return player instanceof EntityPlayerMP && service.locked((EntityPlayerMP) player);
    }

    @SubscribeEvent
    public void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (service.locked(player)) {
            anchors.put(player.getUniqueID(), new Anchor(player));
            player.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 120, 0, false, false));
        }
        service.joined(player);
    }

    @SubscribeEvent
    public void left(PlayerEvent.PlayerLoggedOutEvent event) {
        anchors.remove(event.player.getUniqueID());
        service.left(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (!service.locked(player)) {
            anchors.remove(player.getUniqueID());
            return;
        }
        Anchor anchor = anchors.get(player.getUniqueID());
        if (anchor == null || anchor.dimension != player.dimension) {
            anchor = new Anchor(player);
            anchors.put(player.getUniqueID(), anchor);
        }
        if (player.getDistanceSq(anchor.x, anchor.y, anchor.z) > 0.0001D) {
            player.connection.setPlayerLocation(anchor.x, anchor.y, anchor.z, anchor.yaw, anchor.pitch);
        }
        player.motionX = 0;
        player.motionY = 0;
        player.motionZ = 0;
        player.fallDistance = 0;
        if (player.ticksExisted % 80 == 0) {
            player.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 120, 0, false, false));
        }
        if (player.ticksExisted % 200 == 0) service.showTitle(player);
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) service.tick();
    }

    @SubscribeEvent
    public void interact(PlayerInteractEvent event) {
        if (locked(event.getEntityPlayer()) && event.isCancelable()) event.setCanceled(true);
    }

    @SubscribeEvent
    public void breakBlock(BlockEvent.BreakEvent event) {
        if (locked(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void placeBlock(BlockEvent.PlaceEvent event) {
        if (locked(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void attack(AttackEntityEvent event) {
        if (locked(event.getEntityPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void hurt(LivingHurtEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayerMP
                && service.locked((EntityPlayerMP) event.getEntityLiving())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void toss(ItemTossEvent event) {
        if (locked(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void pickup(EntityItemPickupEvent event) {
        if (locked(event.getEntityPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void chat(ServerChatEvent event) {
        if (locked(event.getPlayer())) {
            event.setCanceled(true);
            VerifyService.tell(event.getPlayer(), "Nutze /verify <Discord-Username>.");
        }
    }

    @SubscribeEvent
    public void command(CommandEvent event) {
        if (event.getSender() instanceof EntityPlayerMP
                && service.locked((EntityPlayerMP) event.getSender())
                && !"verify".equalsIgnoreCase(event.getCommand().getName())) {
            event.setCanceled(true);
            VerifyService.tell((EntityPlayerMP) event.getSender(), "Nur /verify ist vor der Freischaltung möglich.");
        }
    }
}
