package com.flightring;

import net.minecraft.server.level.ServerPlayer;
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
 * KINETIC DEFLECTION (Vibranium ring): ranged attacks never reach the wearer - the
 * projectile is bounced off a barrier around them, in a random direction, as if the
 * player were enclosed in a sphere.
 * <ul>
 *   <li>Only ranged attacks are deflected; melee damage is left to the Magic Lining.</li>
 *   <li>While more than half of the energy pool is left, deflecting costs nothing and
 *       works without limit. When melee hits have pushed the pool to half or below the
 *       deflection switches off, the Magic Lining absorbs ranged hits as well, and once
 *       the pool is empty the wearer takes damage again.</li>
 * </ul>
 * The barrier is checked at the START of every server tick ({@link ServerTickEvent.Pre}),
 * i.e. before any projectile moves, so a shot is turned away while it is still outside the
 * wearer and never appears to pass through them. 26.1.2 applies a projectile's movement
 * before {@link ProjectileImpactEvent} fires, which is why deflecting only in that event
 * looked like the shot went through the player and was then pushed away; it stays as a
 * fallback for projectiles that get inside the barrier in a single step.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class KineticDeflectionHandler {

    /** Deflection only works while more than this fraction of the ring's pool is left. */
    public static final float MIN_ENERGY_FRACTION = 0.5F;

    /** Radius of the barrier sphere around the wearer: shots are turned away out here. */
    private static final double BARRIER_RADIUS = 1.2D;

    /** Slowest speed a deflected projectile keeps, so the bounce stays visible. */
    private static final double MIN_SPEED = 0.7D;

    /** How far the bounce is scattered around the mirror direction. */
    private static final double SCATTER = 0.35D;

    private KineticDeflectionHandler() {
    }

    /**
     * The barrier itself: checked at the start of every server tick, before any projectile
     * moves, so incoming shots are turned away while they are still outside the wearer.
     * This is what makes the deflection look like a sphere around the player in 26.1.2 too.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!canDeflect(player)) {
                continue;
            }
            Vec3 center = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
            AABB area = new AABB(center, center).inflate(BARRIER_RADIUS);
            for (Projectile projectile : player.level().getEntitiesOfClass(Projectile.class, area)) {
                Vec3 offset = projectile.position().subtract(center);
                if (offset.lengthSqr() > BARRIER_RADIUS * BARRIER_RADIUS) {
                    continue;
                }
                // Only turn away projectiles that are still coming in; one that was just
                // bounced off is already leaving and must not be scattered again.
                if (projectile.getDeltaMovement().dot(offset) >= 0.0D) {
                    continue;
                }
                deflect(player, projectile);
            }
        }
    }

    /**
     * Fallback for a projectile that got inside the barrier in a single step (very fast or
     * modded ones): it is bounced here instead, and the hit itself is still cancelled.
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        if (!(hit.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!canDeflect(player)) {
            return;
        }
        deflect(player, event.getProjectile());
        event.setCanceled(true);
    }

    /**
     * Safety net for projectile damage that did not go through {@link ProjectileImpactEvent}
     * (some modded projectiles): the wearer still takes nothing.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getAmount() <= 0.0F) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof Projectile projectile)) {
            return;
        }
        if (!canDeflect(player)) {
            return;
        }
        deflect(player, projectile);
        event.setCanceled(true);
    }

    /** True while the worn ring has the ability and more than half of its pool is left. */
    private static boolean canDeflect(ServerPlayer player) {
        ItemStack ring = RingAbilities.wornRing(player, RingAbility.KINETIC_DEFLECTION);
        if (ring.isEmpty()) {
            return false;
        }
        float max = RingEnergy.max(ring);
        return max > 0.0F && RingEnergy.get(ring) > max * MIN_ENERGY_FRACTION;
    }

    /**
     * Bounces the projectile off the barrier: it keeps its speed and leaves in a random
     * direction that is biased away from the wearer, and is pushed out of the wearer's
     * hitbox so it cannot clip back in.
     */
    private static void deflect(ServerPlayer player, Projectile projectile) {
        Vec3 offset = projectile.position().subtract(player.position());
        Vec3 outward = offset.lengthSqr() < 1.0E-6D
                ? player.getLookAngle().reverse()
                : offset.normalize();
        Vec3 motion = projectile.getDeltaMovement();
        double speed = Math.max(MIN_SPEED, motion.length());
        Vec3 heading = motion.lengthSqr() < 1.0E-6D ? outward.reverse() : motion.normalize();

        // Mirror the flight direction off the sphere's surface...
        Vec3 bounced = heading.subtract(outward.scale(2.0D * heading.dot(outward)));
        // ...then scatter it randomly and bias it outward, so it never comes back through.
        RandomSource random = player.getRandom();
        Vec3 scatter = new Vec3(
                random.triangle(0.0D, SCATTER),
                random.triangle(0.0D, SCATTER),
                random.triangle(0.0D, SCATTER));
        Vec3 direction = bounced.add(outward.scale(0.6D)).add(scatter).normalize();

        projectile.setDeltaMovement(direction.scale(speed));
        // Let the server push the new motion to the clients (no hasImpulse in 26.1.2).
        projectile.hurtMarked = true;
        projectile.setPos(projectile.position().add(direction.scale(0.35D)));
    }
}
