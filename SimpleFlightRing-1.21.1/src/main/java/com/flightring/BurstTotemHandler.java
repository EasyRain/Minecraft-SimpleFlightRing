package com.flightring;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BURST TOTEM (Unobtainium ring): a hit that would be fatal is stopped and answered with a
 * TNT-sized explosion around the wearer which breaks no blocks: nearby creatures are blown
 * away and hurt, while the wearer is saved exactly like a Totem of Undying (health is set
 * to a fixed amount first, then the healing buffs take over).
 * <p>
 * The totem is only charged while the ring's energy pool has been filled up. A rescue
 * discharges it, and the pool then waits {@value #REFILL_DELAY_TICKS} ticks (60 s) before it
 * starts refilling - so the totem can only fire again once the pool is full.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class BurstTotemHandler {

    /** Radius of the explosion, in blocks. */
    private static final double RADIUS = 8.0D;

    /** Flat damage every living entity caught in the blast takes. */
    private static final float DAMAGE = 20.0F;

    /** Push strength, matching TNT: vanilla knocks entities back by {@code 1 - distance/radius}. */
    private static final double KNOCKBACK = 1.0D;

    /** Extra upward push, like an explosion lifting entities off the ground. */
    private static final double KNOCKBACK_LIFT = 0.25D;

    /** After a rescue the pool waits 60 s (instead of the usual 10 s) before refilling. */
    public static final int REFILL_DELAY_TICKS = 60 * 20;

    /** Explosion particles scattered over the blast so it reads at its real size. */
    private static final int PARTICLE_COUNT = 24;

    /** Players whose totem is charged, i.e. whose pool has been full since the last rescue. */
    private static final Map<UUID, Boolean> CHARGED = new HashMap<>();

    private BurstTotemHandler() {
    }

    /**
     * A hit that would kill the wearer is cancelled and replaced by the burst: the pool is
     * left empty (the Magic Lining got what it could), the totem fires and the wearer is
     * revived.
     */
    @SubscribeEvent
    public static void onFatalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        float damage = event.getNewDamage();
        if (damage <= 0.0F) {
            return;
        }
        // Only a hit that would actually kill counts (health plus absorption hearts).
        if (damage < player.getHealth() + player.getAbsorptionAmount()) {
            return;
        }
        // Void damage and /kill stay lethal, exactly like the vanilla totem.
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        ItemStack ring = RingAbilities.wornRing(player, RingAbility.BURST_TOTEM);
        if (ring.isEmpty() || !isCharged(player)) {
            return;
        }

        event.setNewDamage(0.0F);
        CHARGED.put(player.getUUID(), Boolean.FALSE);
        MagicLiningHandler.delayRefill(player, REFILL_DELAY_TICKS);
        explode(player);
        revive(player);
    }

    /** The explosion: TNT sound and particles, flat damage and TNT-like knockback, no block damage. */
    private static void explode(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 center = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0F, 1.0F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        // Level#random is public in 1.21.1 but protected in 26.1.2: use the player's source.
        RandomSource random = player.getRandom();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            level.sendParticles(ParticleTypes.EXPLOSION,
                    center.x + random.nextDouble() * 2.0D * RADIUS - RADIUS,
                    center.y + random.nextDouble() * 2.0D * RADIUS - RADIUS,
                    center.z + random.nextDouble() * 2.0D * RADIUS - RADIUS,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        AABB area = new AABB(center, center).inflate(RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (target == player || !target.isAlive()) {
                continue;
            }
            double distance = Math.sqrt(target.distanceToSqr(center));
            if (distance > RADIUS) {
                continue;
            }
            // Explosion damage falls off with distance, the push follows the same curve.
            double falloff = 1.0D - distance / RADIUS;
            target.hurt(level.damageSources().explosion(player, player), DAMAGE);
            Vec3 offset = target.position().subtract(center);
            Vec3 direction = offset.lengthSqr() < 1.0E-8D
                    ? new Vec3(0.0D, 1.0D, 0.0D)
                    : offset.normalize();
            target.push(direction.x * KNOCKBACK * falloff,
                    direction.y * KNOCKBACK * falloff + KNOCKBACK_LIFT * falloff,
                    direction.z * KNOCKBACK * falloff);
            target.hurtMarked = true;
        }
    }

    /**
     * The Totem of Undying's rescue: a fixed amount of health right away (the healing buffs
     * only start from there), the harmful effects removed, then Regeneration, Absorption
     * and Fire Resistance.
     */
    private static void revive(ServerPlayer player) {
        player.setHealth(1.0F);
        // Vanilla clears the effects a totem cures; NeoForge's EffectCures helper is not
        // available on 26.1.2, so the harmful effects are dropped explicitly.
        for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
            if (!effect.getEffect().value().isBeneficial()) {
                player.removeEffect(effect.getEffect());
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
    }

    /** The totem is charged again as soon as the pool is full, and only then. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack ring = RingAbilities.wornRing(player, RingAbility.BURST_TOTEM);
        if (ring.isEmpty()) {
            return;
        }
        float max = RingEnergy.max(ring);
        if (max > 0.0F && RingEnergy.get(ring) >= max) {
            CHARGED.put(player.getUUID(), Boolean.TRUE);
        }
    }

    /** A ring that was never used for a rescue counts as charged. */
    private static boolean isCharged(ServerPlayer player) {
        return CHARGED.getOrDefault(player.getUUID(), Boolean.TRUE);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        CHARGED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CHARGED.remove(event.getOriginal().getUUID());
    }
}
