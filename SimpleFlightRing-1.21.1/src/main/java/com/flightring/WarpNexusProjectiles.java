package com.flightring;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * WARP NEXUS, projectile half: an incoming shot never touches the wearer - like an enderman dodging
 * an arrow, except that here it is the arrow that is sent away. The projectile is picked up and
 * dropped at a random spot a dozen or so blocks off, in a random direction, leaving the same portal
 * burst and teleport sound behind at both ends.
 * <p>
 * The barrier is checked at the START of every server tick ({@link ServerTickEvent.Pre}), before
 * any projectile moves, so a shot is warped away while it is still outside the wearer and never
 * appears to pass through them; {@link ProjectileImpactEvent} and
 * {@link LivingIncomingDamageEvent} stay as fallbacks for shots that cross the barrier in a single
 * step or that never fire an impact at all (some modded ones), so the wearer takes nothing either
 * way.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class WarpNexusProjectiles {

    /** Radius of the sphere around the wearer in which shots are caught. */
    private static final double BARRIER_RADIUS = 1.5D;

    /** How far away a caught projectile is dropped, at least and at most. */
    private static final double MIN_DISTANCE = 8.0D;
    private static final double MAX_DISTANCE = 16.0D;

    /**
     * Slowest approach that still counts as an attack. A shot that has already stopped - one stuck
     * in the ground next to the wearer, or one that hit a wall and is settling - is not coming for
     * anyone, and walking past it must not send it flying.
     */
    private static final double MIN_APPROACH_SPEED = 0.1D;

    private WarpNexusProjectiles() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!wearsRing(player)) {
                continue;
            }
            Vec3 center = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
            AABB area = new AABB(center, center).inflate(BARRIER_RADIUS);
            for (Projectile projectile : player.level().getEntitiesOfClass(Projectile.class, area)) {
                Vec3 offset = projectile.position().subtract(center);
                if (offset.lengthSqr() > BARRIER_RADIUS * BARRIER_RADIUS || offset.lengthSqr() < 1.0E-6D) {
                    continue;
                }
                // Only catch shots that are still coming in, and only ones that are really moving:
                // an arrow that is already stuck in the ground at the wearer's feet (or crawling
                // down a wall) has an approach speed of about zero and is left where it is.
                Vec3 motion = projectile.getDeltaMovement();
                double approach = -motion.dot(offset.normalize());
                if (approach < MIN_APPROACH_SPEED) {
                    continue;
                }
                warp(player, projectile);
            }
        }
    }

    /** Fallback for a shot that crossed the barrier in one step (very fast or modded ones). */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        if (!(hit.getEntity() instanceof ServerPlayer player) || !wearsRing(player)) {
            return;
        }
        warp(player, event.getProjectile());
        event.setCanceled(true);
    }

    /** Safety net for projectile damage that never went through an impact event. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getAmount() <= 0.0F || !wearsRing(player)) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof Projectile projectile)) {
            return;
        }
        warp(player, projectile);
        event.setCanceled(true);
    }

    private static boolean wearsRing(ServerPlayer player) {
        return !RingAbilities.wornRing(player, RingAbility.WARP_NEXUS).isEmpty();
    }

    /**
     * Sends the projectile away: a random spot in a random direction, with a fresh random heading
     * so it cannot simply fly back, and the enderman's own arrival and departure effects at both
     * ends of the jump.
     */
    private static void warp(ServerPlayer player, Projectile projectile) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        Vec3 from = projectile.position();

        Vec3 direction = new Vec3(
                random.nextDouble() - 0.5D,
                random.nextDouble() - 0.5D,
                random.nextDouble() - 0.5D);
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = player.getLookAngle().reverse();
        }
        direction = direction.normalize();
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        Vec3 to = from.add(direction.scale(distance));

        effect(level, from);
        projectile.teleportTo(to.x, to.y, to.z);
        projectile.setDeltaMovement(direction.scale(0.1D + random.nextDouble() * 0.4D));
        // Let the server push the new position and motion to the clients.
        projectile.hurtMarked = true;
        effect(level, to);
    }

    private static void effect(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 24, 0.2, 0.2, 0.2, 0.3);
        level.playSound(null, BlockPos.containing(at), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.6F, 1.4F);
    }
}
