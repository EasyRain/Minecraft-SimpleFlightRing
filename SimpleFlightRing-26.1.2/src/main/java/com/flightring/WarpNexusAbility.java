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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
    /** How finely the way forward is sampled, and how far the wearer is lifted onto a step. */
    private static final double STEP = 0.25;
    /** How far short of whatever the aim ray hits the wearer is placed. */
    private static final double BACKOFF = 0.35;
    /** Highest step the wearer is lifted onto; a wall taller than this ends the jump. */
    private static final double LIFT_LIMIT = 1.25;
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
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Long readyAt = READY_AT.get(player.getUUID());
        if (readyAt != null && now < readyAt) {
            long seconds = Math.max(1L, (readyAt - now + 19L) / 20L);
            player.sendOverlayMessage(Component.translatable(
                    "message.simpleflightring.warp_nexus_cooldown", seconds).withStyle(ChatFormatting.GRAY));
            return;
        }

        double distance = BASE_DISTANCE * RingAbilities.abilityDamageMultiplier(ring);
        Vec3 from = player.position();
        Vec3 destination = findDestination(player, distance);
        if (destination == null) {
            player.sendOverlayMessage(Component.translatable(
                    "message.simpleflightring.warp_nexus_blocked").withStyle(ChatFormatting.GRAY));
            return;
        }

        // An enderman leaves and arrives the same way: a burst of portal particles and its own
        // teleport sound at both ends of the jump.
        teleportEffects(level, from);
        // 26.1.2 teleports through Entity#teleportTo(ServerLevel, x, y, z, Set<Relative>, yRot, xRot,
        // resetCamera); the empty set keeps every coordinate absolute, as the 1.21.1 call did.
        player.teleportTo(level, destination.x, destination.y, destination.z, Set.of(),
                player.getYRot(), player.getXRot(), false);
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
    /**
     * Where a blink from here would land, or null when there is nowhere to go.
     * <p>
     * Two things decide it, the way Just Dire Things' wand does it with a single block raycast:
     * <ul>
     *   <li>a <b>block ray</b> along the line of sight, cast from the eyes, says how far the aim
     *       actually reaches. Whatever it hits ends the jump just short of that face, which is what
     *       makes a wall ten blocks ahead a ten block jump - and what makes aiming at the ground
     *       land the wearer on it instead of refusing, because the ray stops at the floor rather
     *       than the walk pushing the whole hitbox into it;</li>
     *   <li>the way there is then walked in small steps, and each step has to be somewhere the
     *       wearer fits. The ground is not an obstruction - a step or a slope lifts the wearer onto
     *       it (up to {@link #LIFT_LIMIT} blocks) - but a wall is, and so is every fluid except
     *       water: lava and other mods' fluids are never lifted out of, they simply end the jump
     *       where they start.</li>
     * </ul>
     * The world border counts as one of those walls. Package private so the dev probe can check the
     * landing spot without a client that would have to move a real player.
     */
    static Vec3 findDestination(ServerPlayer player, double distance) {
        ServerLevel level = player.level();
        Vec3 from = player.position();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        Vec3 end = eye.add(look.scale(distance));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        double reach = hit.getType() == HitResult.Type.MISS
                ? distance
                : Math.min(distance, eye.distanceTo(hit.getLocation()) - BACKOFF);

        Vec3 last = null;
        for (double travelled = STEP; travelled <= reach + 1.0E-6D; travelled += STEP) {
            Vec3 spot = placeable(level, player, from.add(look.scale(travelled)));
            if (spot == null) {
                break;
            }
            last = spot;
        }
        if (last == null || last.distanceTo(from) < MIN_JUMP) {
            return null;
        }
        return level.getWorldBorder().isWithinBounds(last) ? last : null;
    }

    /**
     * Where the wearer ends up on this sample of the way, or null when they cannot be there at all.
     * The fluid check comes first and is never lifted out of, so a pool of lava is a wall; the block
     * check comes second and may be answered by standing on top of what is in the way.
     */
    private static Vec3 placeable(ServerLevel level, ServerPlayer player, Vec3 sample) {
        AABB box = player.getBoundingBox().move(sample.subtract(player.position()));
        if (!onlyWaterOrAir(level, box)) {
            return null;
        }
        if (level.noBlockCollision(player, box)) {
            return sample;
        }
        for (double lift = STEP; lift <= LIFT_LIMIT + 1.0E-6D; lift += STEP) {
            if (level.noBlockCollision(player, box.move(0.0D, lift, 0.0D))) {
                return sample.add(0.0D, lift, 0.0D);
            }
        }
        return null;
    }

    /** True while the box is in nothing but air and water (any other fluid counts as a wall). */
    private static boolean onlyWaterOrAir(ServerLevel level, AABB box) {
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
