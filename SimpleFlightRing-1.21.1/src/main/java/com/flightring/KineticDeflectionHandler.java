package com.flightring;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

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
 * The deflection happens in {@link ProjectileImpactEvent}, i.e. before the projectile can
 * touch the wearer: no damage, no knockback, no hit effect at all.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class KineticDeflectionHandler {

    /** Deflection only works while more than this fraction of the ring's pool is left. */
    public static final float MIN_ENERGY_FRACTION = 0.5F;

    /** Slowest speed a deflected projectile keeps, so the bounce stays visible. */
    private static final double MIN_SPEED = 0.7D;

    /** How far the bounce is scattered around the mirror direction. */
    private static final double SCATTER = 0.35D;

    private KineticDeflectionHandler() {
    }

    /**
     * The main path: the projectile is stopped the moment it would hit the wearer and is
     * sent off in a random direction instead.
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
