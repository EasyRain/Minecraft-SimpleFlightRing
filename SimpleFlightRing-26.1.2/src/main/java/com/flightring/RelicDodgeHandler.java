package com.flightring;

import java.util.Random;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Dodge chance of the relic rings (ocean 25%, desert 50%, ender 20%), implemented the way
 * ApothicAttributes does it so the two feel identical:
 * <ul>
 *   <li><b>only melee and projectiles</b> are dodged - never environmental damage (fall, lava,
 *       void, poison, hunger);</li>
 *   <li>the roll is <b>deterministic per tick</b> (seed = {@code tickCount + uuid.hashCode()}
 *       run through the 0x9E3779B9 mix), so asking twice in the same tick gives the same
 *       answer and a hit cannot be re-rolled until it lands;</li>
 *   <li>a dodge plays a whoosh and puffs smoke where the blow would have landed.</li>
 * </ul>
 * <p>
 * When ApothicAttributes is installed this class does nothing: its {@code dodge_chance}
 * attribute already carries our values (see {@link RelicBonuses} and {@link CuriosCompat}) and
 * its own handler rolls them - one roll, one sound, no double counting.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class RelicDodgeHandler {

    /** The same hash constant ApothicAttributes seeds its roll with. */
    private static final int SEED_HASH = -1640531527;

    /** One shared generator: only ever used server side, and re-seeded before every roll. */
    private static final Random ROLL = new Random();

    private RelicDodgeHandler() {
    }

    /** Melee: a player's or a mob's swing that is actually in range of the wearer. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (ApothicAttributesCompat.handlesDodge() || event.isCanceled()) {
            return;                                  // their handler owns the roll
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity attacker = event.getSource().getDirectEntity();
        boolean inMeleeRange;
        if (attacker instanceof Player striker) {
            double reach = striker.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            inMeleeRange = striker.distanceToSqr(player) <= reach * reach;
        } else if (attacker instanceof Mob mob) {
            inMeleeRange = mob.isWithinMeleeAttackRange(player);
        } else {
            inMeleeRange = false;                    // explosions, fire, magic, the world itself
        }
        if (inMeleeRange && isDodging(player)) {
            onDodge(player);
            event.setCanceled(true);
        }
    }

    /** Projectiles: the shot is shrugged off at the moment it would have connected. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (ApothicAttributesCompat.handlesDodge() || event.isCanceled()) {
            return;
        }
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        if (!(hit.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (isDodging(player)) {
            onDodge(player);
            event.setCanceled(true);
        }
    }

    /**
     * Rolls this tick for the given player. Public together with {@link #computeDodgeSeed} so a
     * probe can assert the roll is stable within a tick and follows the ring's chance.
     */
    public static boolean isDodging(LivingEntity entity) {
        return roll(entity, dodgeChance(entity));
    }

    /**
     * The roll itself: {@code chance} is a fraction (0.5 = 50%), the result is stable for the
     * whole tick because the generator is re-seeded from the entity every call. A chance of zero
     * short-circuits so a ring without dodge never even seeds the generator.
     */
    public static boolean roll(LivingEntity entity, double chance) {
        if (chance <= 0.0D) {
            return false;
        }
        ROLL.setSeed(computeDodgeSeed(entity));
        return ROLL.nextFloat() <= chance;
    }

    /** The dodge chance of the ring the entity wears, or 0 when it wears none. */
    private static double dodgeChance(LivingEntity entity) {
        if (!(entity instanceof Player player) || !CuriosCompat.isLoaded()) {
            return 0.0D;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        if (!(ring.getItem() instanceof FlightRingItem item) || !item.isUsable(ring)) {
            return 0.0D;
        }
        RelicBonuses bonuses = item.getRelicBonuses();
        return bonuses == null ? 0.0D : bonuses.dodgeChance();
    }

    /** ApothicAttributes' seed: the tick and the entity id mixed with the golden-ratio hash. */
    public static int computeDodgeSeed(LivingEntity entity) {
        int value = entity.tickCount + entity.getUUID().hashCode();
        return value + SEED_HASH + (value << 6) + (value >> 2);
    }

    /** The feedback a dodge leaves behind: a whoosh and a puff of smoke. */
    private static void onDodge(Player player) {
        player.level().playSound(null, player, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL,
                1.0F, 1.2F + player.getRandom().nextFloat() * 0.3F);
        if (player.level() instanceof ServerLevel level) {
            double width = player.getBbWidth();
            double height = player.getBbHeight();
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    player.getX() - width / 4.0D, player.getY(), player.getZ() - width / 4.0D,
                    6, -width / 4.0D, height / 8.0D, -width / 4.0D, 0.0D);
        }
    }
}
