package com.flightring;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tells a desert ring wearer which creatures are hunting them, so {@link DesertSense} can outline
 * those red.
 * <p>
 * Vanilla already answers "is this creature hostile?" by its class ({@link
 * net.minecraft.world.entity.monster.Enemy}), which the client can see on its own. What it cannot
 * see is a <b>neutral</b> creature that turned on the wearer: a mob's attack target and its
 * persistent anger (both of which {@link NeutralMob} keeps) live on the server only. So every
 * {@link #INTERVAL_TICKS} ticks the server collects, for each wearer, the creatures within
 * {@link #TRACK_RADIUS} blocks that either target the wearer or are angry at them, and sends the
 * list to that wearer alone - and only when it actually changed.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class DesertHostileSync {

    /**
     * A little wider than the danger sense itself (32 blocks), so a creature that walks over the
     * border is already coloured red by the time it is outlined.
     */
    private static final double TRACK_RADIUS = 40.0;

    /** The client rescans every 10 ticks; there is no point in telling it any faster. */
    private static final int INTERVAL_TICKS = 10;

    /** What each wearer was last told, so unchanged lists are not sent again. */
    private static final Map<UUID, Set<Integer>> SENT = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Set<Integer> hostiles = DesertRingAbility.isDesertWalker(player) ? grudges(player) : Set.of();
            if (hostiles.equals(SENT.get(player.getUUID()))) {
                continue;
            }
            SENT.put(player.getUUID(), hostiles);
            PacketDistributor.sendToPlayer(player, new DesertHostilesPayload(List.copyOf(hostiles)));
        }
    }

    /** The entity ids of the creatures near this wearer that want them gone. */
    static Set<Integer> grudges(ServerPlayer player) {
        ServerLevel level = player.level();
        AABB box = player.getBoundingBox().inflate(TRACK_RADIUS);
        Set<Integer> hostiles = new HashSet<>();
        for (Entity entity : level.getEntities(player, box, candidate -> candidate instanceof Mob)) {
            if (isHostileTo((Mob) entity, player, level)) {
                hostiles.add(entity.getId());
            }
        }
        return hostiles;
    }

    /**
     * True while this creature is hostile to this very player: either it is actively hunting them
     * (which also covers the mobs vanilla keeps no anger for, like iron golems and polar bears),
     * or it remembers them as an enemy (the neutral mobs' persistent anger, e.g. a zombified
     * piglin or a wolf). Both are per player, so a wolf that hates someone else stays white.
     */
    private static boolean isHostileTo(Mob mob, ServerPlayer player, ServerLevel level) {
        if (mob.getTarget() == player) {
            return true;
        }
        return mob instanceof NeutralMob neutral && neutral.isAngryAt(player, level);
    }

    /** Fresh dimension, fresh entity ids: force a new list instead of trusting the old one. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    private DesertHostileSync() {
    }
}
