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

    /**
     * Re-entrancy guard for the damage we deal ourselves (荆棘 reflects damage from inside this handler, which
     * can land on another ring wearer and come straight back). Apotheosis guards its own damage handler exactly
     * this way with a {@code ThreadLocal<AtomicBoolean>}.
     */
    private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** How long (in game ticks) the anvil detection window stays open after the last tick with it open. */
    private static final long ANVIL_WINDOW_TICKS = 20L;

    /**
     * Per-player deadline (game time) until which pending rings are still looked for. Set while an anvil screen is
     * open and kept for {@link #ANVIL_WINDOW_TICKS} after it closes - closing also happens when the anvil block
     * breaks, and vanilla then returns the cursor stack to the inventory. Entries are dropped once expired, so the
     * map only ever holds players who touched an anvil in the last second.
     */
    private static final java.util.Map<java.util.UUID, Long> ANVIL_WINDOW = new java.util.concurrent.ConcurrentHashMap<>();

    private RingAffixHandler() {
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (REENTRANT.get()) {
            return;
        }
        REENTRANT.set(Boolean.TRUE);
        try {
            attackerSide(event);
            victimSide(event);
        } finally {
            REENTRANT.set(Boolean.FALSE);
        }
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

    /**
     * Settles a pending ring that appears on the player's <b>cursor</b>.
     * <p>
     * A normal left click in the anvil hands the result to the cursor, and it is not in any inventory slot while
     * the take event runs - so the take-time scan cannot see it and the ring would stay plain. Checking the
     * carried stack once per tick covers that, and also covers dropping it out of the GUI (by then it is already
     * rolled, so the pickup fallback does not roll a second time).
     */
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.level().getGameTime();
        net.minecraft.world.inventory.AbstractContainerMenu menu = player.containerMenu;
        if (menu instanceof net.minecraft.world.inventory.AnvilMenu) {
            // Inside the anvil: settle every tick, so taking the result feels instant. The 1s window below is
            // armed here so that leaving (including the anvil block breaking, which closes the GUI) still gets a
            // short detection window.
            ANVIL_WINDOW.put(player.getUUID(), now + ANVIL_WINDOW_TICKS);
            settleCarried(player);
            return;
        }
        Long until = ANVIL_WINDOW.get(player.getUUID());
        if (until == null || now > until) {
            ANVIL_WINDOW.remove(player.getUUID());
            return; // no anvil activity recently: no scanning at all
        }
        settleCarried(player);
        settleInventory(player);
    }

    private static void settleCarried(ServerPlayer player) {
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && carried.has(ModDataComponents.PENDING_REFORGE.get())) {
            RingAnvilHandler.rollIfPending(carried, player);
        }
    }

    private static void settleInventory(ServerPlayer player) {
        // 26.x: Inventory#items is private; getNonEquipmentItems() is the main inventory.
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (RingAnvilHandler.rollIfPending(stack, player)) {
                return;
            }
        }
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
