package com.flightring;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The desert ring's danger sense, client side: every {@link #RESCAN_INTERVAL_TICKS} ticks it
 * collects the living creatures within {@link #SENSE_RADIUS} blocks of the local player and hands
 * them to {@link DesertSense}, which {@code EntityGlowMixin} turns into outlines.
 * <p>
 * Everything happens on the wearer's own client, so <b>only they</b> see the outlines - no
 * glowing effect is ever applied to the creatures and no packet is involved. The creatures are
 * all within the server's entity tracking range for this player, so the client already knows
 * them. Weapons, dropped items and other non living entities are skipped (see
 * {@link DesertSense#isSenseable}).
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class DesertSenseHandler {

    /** "within 32 blocks" of the wearer. */
    private static final double SENSE_RADIUS = 32.0;
    /** Rescanning five times a second is plenty for creatures that walk at most a few blocks. */
    private static final int RESCAN_INTERVAL_TICKS = 10;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !wearsDesertRing(player)) {
            DesertSense.clear();
            return;
        }
        if (level.getGameTime() % RESCAN_INTERVAL_TICKS != 0) {
            return;
        }

        AABB box = player.getBoundingBox().inflate(SENSE_RADIUS);
        List<Entity> nearby = level.getEntities(player, box, DesertSense::isSenseable);
        Map<Integer, Integer> sensed = new HashMap<>(nearby.size());
        for (Entity entity : nearby) {
            sensed.put(entity.getId(), DesertSense.colorFor(entity));
        }
        DesertSense.replace(sensed);
    }

    /** True while the local player wears a usable desert ring. */
    private static boolean wearsDesertRing(LocalPlayer player) {
        if (!CuriosCompat.isLoaded()) {
            return false;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        return ring.getItem() instanceof FlightRingItem item
                && item.hasAbility(RingAbility.DESERT_GUIDE)
                && item.isUsable(ring);
    }

    private DesertSenseHandler() {
    }
}
