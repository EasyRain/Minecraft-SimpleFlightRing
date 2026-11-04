package com.flightring;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The event-driven halves of the affix prefixes, batch 1: 精准 (projectile damage), 凋零 / 荼毒 (on-hit
 * effects) and 荆棘 (reflect).
 * <p>
 * One damage event serves both sides. On the <b>attacker</b> side we look at the attacker's worn ring: 精准
 * scales projectile damage, and 凋零 / 荼毒 apply their effect - but only on a <b>fully charged</b> melee hit,
 * so an auto-clicker cannot spam them (see the plan). On the <b>victim</b> side we look at the victim's ring:
 * 荆棘 reflects a share of the <b>attacker's own attack power</b>, which is the only reading that keeps
 * scaling against modded bosses with huge attack values.
 * <p>
 * 26.x difference: dealing damage is {@code hurtServer(level, source, amount)} instead of
 * {@code hurt(source, amount)}, which is the only thing that separates this file from the 1.21.1 one.
 */
public final class RingAffixHandler {

    /** Effect duration in ticks by rarity: 3s / 5s / 6s / 8s / 10s. */
    private static final int[] EFFECT_TICKS = {60, 100, 120, 160, 200};

    private RingAffixHandler() {
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        attackerSide(event);
        victimSide(event);
    }

    /** 精准 (projectile share) and 凋零 / 荼毒 (full-charge on-hit) - read from the attacker's ring. */
    private static void attackerSide(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        RingAffix affix = affixOf(attacker);
        if (affix == null) {
            return;
        }
        RingAffix.Prefix prefix = affix.prefix();
        double value = prefix.value(affix.rarity());
        if (prefix == RingAffix.Prefix.PRECISE) {
            // Only projectile damage (arrows, tridents, thrown potions, modded projectiles that use the tag).
            if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
                event.setAmount((float) (event.getAmount() * (1.0 + value)));
            }
            return;
        }
        if (prefix != RingAffix.Prefix.WITHERING && prefix != RingAffix.Prefix.VENOMOUS) {
            return;
        }
        // Both effects share one predicate: a fully charged melee hit, so a clicker gets nothing.
        if (!isFullCharge(attacker)) {
            return;
        }
        // MobEffects.* are Holders, not MobEffect instances.
        net.minecraft.core.Holder<MobEffect> effect = prefix == RingAffix.Prefix.WITHERING
                ? MobEffects.WITHER
                : MobEffects.POISON;
        event.getEntity().addEffect(new MobEffectInstance(
                effect, EFFECT_TICKS[affix.rarity().ordinal()], (int) value));
    }

    /** 荆棘: reflects a share of the attacker's attack power back at them. */
    private static void victimSide(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        // Thorns damage itself must not reflect again, or two ring wearers would ping-pong forever.
        if (event.getSource().is(DamageTypes.THORNS)) {
            return;
        }
        RingAffix affix = affixOf(victim);
        if (affix == null || affix.prefix() != RingAffix.Prefix.THORNED) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        double reflect = affix.prefix().value(affix.rarity())
                * attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (reflect <= 0.0) {
            return;
        }
        // 26.x: ServerPlayer#serverLevel() is gone; level() is already the ServerLevel on the server.
        attacker.hurtServer((net.minecraft.server.level.ServerLevel) victim.level(),
                victim.damageSources().thorns(victim), (float) reflect);
    }

    /** The affix of the ring the player has equipped, or {@code null}. */
    private static RingAffix affixOf(ServerPlayer player) {
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        return ring.get(ModDataComponents.RING_AFFIX.get());
    }

    /** Whether the vanilla attack cooldown is full - the "no auto-clicker" rule. */
    private static boolean isFullCharge(ServerPlayer player) {
        return player.getAttackStrengthScale(0.5F) >= 1.0F;
    }
}
