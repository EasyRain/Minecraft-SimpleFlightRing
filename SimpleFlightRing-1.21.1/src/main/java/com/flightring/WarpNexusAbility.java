package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The ability key of the ender relic ring, "Warp Nexus": a blink along the line the wearer is
 * looking down, modelled on Just Dire Things' Voidshift/Eclipsegate wand - it jumps the player to a
 * spot in front of them and spares them the fall if that spot is in mid air, which is the one part
 * of the wand's behaviour copied here beyond the jump itself.
 * <p>
 * The ring is a utility item, so 能量迸发 (Energy Burst) does nothing but stretch the jump: fifteen
 * blocks at its base, plus a quarter of that per enchantment level.
 * <p>
 * Where it lands: the far end is checked first, and if the wearer could stand there the jump goes
 * the whole way, through whatever happens to be in between. Only when the far end is inside
 * something does the jump get cut short, and then it stops at the last clear spot on the way - a
 * wall ten blocks ahead simply means a ten block jump, ending just outside that wall. A jump of
 * less than a block is not a jump at all: nothing moves, nothing is spent and the key stays ready,
 * which is what standing right against a wall looks like. Every fluid except water counts as a
 * wall for all of this, so a blink can never end inside lava or another mod's fluid.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class WarpNexusAbility {

    /** Five seconds between blinks. */
    private static final int COOLDOWN_TICKS = 100;
    /** Fifteen blocks at level 0 of Energy Burst; the enchantment multiplies it. */
    private static final double BASE_DISTANCE = 15.0;
    /** How finely the way forward is sampled when the far end turns out to be blocked. */
    private static final double STEP = 0.25;
    /** Shortest jump worth making; anything less leaves the wearer where they stand. */
    private static final double MIN_JUMP = 1.0;
    /** How long a fresh jump keeps its wearer safe from the landing, in ticks. */
    private static final long FALL_GRACE_TICKS = 600L;

    /** The ability key's internal cooldown, per player. */
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    /** Wearers who just blinked: their next landing does not hurt, like the wand's own jump. */
    private static final Map<UUID, Long> NO_FALL_UNTIL = new HashMap<>();

    /**
     * Blinks the wearer forward, if the ability is off cooldown and there is anywhere to go. Called
     * by {@link RingAbilityKeyHandler} when the ability key is pressed.
     */
    static void tryCast(ServerPlayer player, ItemStack ring) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long readyAt = READY_AT.get(player.getUUID());
        if (readyAt != null && now < readyAt) {
            long seconds = Math.max(1L, (readyAt - now + 19L) / 20L);
            player.displayClientMessage(Component.translatable(
                    "message.simpleflightring.warp_nexus_cooldown", seconds).withStyle(ChatFormatting.GRAY), true);
            return;
        }

        double distance = BASE_DISTANCE * RingAbilities.abilityDamageMultiplier(ring);
        Vec3 from = player.position();
        Vec3 destination = findDestination(player, distance);
        if (destination == null) {
            player.displayClientMessage(Component.translatable(
                    "message.simpleflightring.warp_nexus_blocked").withStyle(ChatFormatting.GRAY), true);
            return;
        }

        // An enderman leaves and arrives the same way: a burst of portal particles and its own
        // teleport sound at both ends of the jump.
        teleportEffects(level, from);
        player.teleportTo(level, destination.x, destination.y, destination.z, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        NO_FALL_UNTIL.put(player.getUUID(), now + FALL_GRACE_TICKS);
        teleportEffects(level, destination);

        READY_AT.put(player.getUUID(), now + COOLDOWN_TICKS);
        FlightRingMod.LOGGER.debug("[FlightRing] {} blinked {} of {} blocks forward",
                player.getName().getString(), destination.distanceTo(from), distance);
    }

    /**
     * Where a blink from here would land, or null when there is nowhere to go.
     * <p>
     * The way forward is walked in small steps and the first spot the wearer no longer fits in
     * ends it: a wall ten blocks ahead simply means a ten block jump that stops just outside it,
     * and the world border counts as one of those walls. There is no reaching past an obstruction
     * to something clear further on - Just Dire Things' wand behaves the same way, with a block
     * raycast instead of a walk, which is where this comes from.
     * <p>
     * Package private so the dev probe can check the landing spot without a client that would have
     * to move a real player.
     */
    static Vec3 findDestination(ServerPlayer player, double distance) {
        ServerLevel level = player.serverLevel();
        Vec3 from = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 last = null;
        for (double travelled = STEP; travelled <= distance + 1.0E-6D; travelled += STEP) {
            Vec3 candidate = from.add(look.scale(travelled));
            if (!isClear(level, player, candidate)) {
                break;
            }
            last = candidate;
        }
        if (last == null || last.distanceTo(from) < MIN_JUMP) {
            return null;
        }
        return level.getWorldBorder().isWithinBounds(last) ? last : null;
    }

    /**
     * True when the wearer's own hitbox fits at {@code at} without touching a block, and when the
     * only fluid it would end up in is water. Everything else - lava, and any fluid another mod
     * adds - counts as a wall, exactly like the blocks around it. Other creatures are ignored: a
     * blink is not blocked by whatever happens to be standing at the far end.
     */
    private static boolean isClear(ServerLevel level, ServerPlayer player, Vec3 at) {
        AABB box = player.getBoundingBox().move(at.subtract(player.position()));
        if (!level.noBlockCollision(player, box)) {
            return false;
        }
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX - 1.0E-4, box.maxY - 1.0E-4, box.maxZ - 1.0E-4);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            FluidState fluid = level.getFluidState(pos);
            if (!fluid.isEmpty() && !fluid.is(FluidTags.WATER)) {
                return false;
            }
        }
        return true;
    }

    private static void teleportEffects(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 48, 0.4, 0.9, 0.4, 0.35);
        level.playSound(null, BlockPos.containing(at), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** The landing after a blink never hurts, however far the wearer falls. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Long until = NO_FALL_UNTIL.get(player.getUUID());
        if (until != null && player.level().getGameTime() <= until) {
            event.setCanceled(true);
        }
    }

    /** The grace ends the moment the wearer is back on solid ground. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.onGround()) {
            NO_FALL_UNTIL.remove(player.getUUID());
        }
    }

    private WarpNexusAbility() {
    }
}
