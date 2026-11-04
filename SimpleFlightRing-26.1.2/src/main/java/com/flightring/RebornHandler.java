package com.flightring;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 重生 (Reborn): one totem-like save. Modelled on {@link BurstTotemHandler}.
 * <p>
 * Non-indestructible rings pay with durability: each save consumes {@code maxDamage / (rarity + 1)}, i.e. a common
 * roll is used up in one save while a sacred one lasts five. Running out of durability never blocks the last
 * save - the ring is simply left at one point from breaking.
 * <p>
 * Indestructible rings (they have no durability bar at all) instead use a one-charge cooldown, tracked here in
 * server game ticks; the ring's own bar renders that charge (see {@code FlightRingItem}).
 * <p>
 * 劫掠 (the raid relic's own save) always wins when a ring could do both, as specified.
 */
public final class RebornHandler {

    /** Cooldown of the indestructible ring's single charge: 60 seconds. */
    public static final int INDESTRUCTIBLE_COOLDOWN_TICKS = 1200;

    /** Per-player game time at which the next indestructible save becomes available. */
    private static final Map<UUID, Long> RECHARGE_UNTIL = new ConcurrentHashMap<>();

    private RebornHandler() {
    }

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
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        if (!(ring.getItem() instanceof FlightRingItem item)) {
            return;
        }
        RingAffix affix = ring.get(ModDataComponents.RING_AFFIX.get());
        if (affix == null || affix.prefix() != RingAffix.Prefix.REBORN) {
            return;
        }
        // 劫掠 wins whenever the same ring could do both.
        if (item.hasAbility(RingAbility.RAID_PLUNDER)) {
            return;
        }
        long now = player.level().getGameTime();
        if (ring.has(ModDataComponents.INDESTRUCTIBLE.get())) {
            Long until = RECHARGE_UNTIL.get(player.getUUID());
            if (until != null && now < until) {
                return; // still recharging
            }
            RECHARGE_UNTIL.put(player.getUUID(), now + INDESTRUCTIBLE_COOLDOWN_TICKS);
        } else {
            int share = Math.max(1, ring.getMaxDamage() / (affix.rarity().ordinal() + 1));
            // Running out never blocks this save: the ring is simply left just short of breaking.
            ring.setDamageValue(Math.min(ring.getMaxDamage() - 1, ring.getDamageValue() + share));
        }
        event.setNewDamage(0.0F);
        revive(player);
    }

    /** Progress of the indestructible charge, 0..1 (1 = ready). Used by the ring's bar. */
    public static float chargeProgress(ServerPlayer player, long now) {
        Long until = RECHARGE_UNTIL.get(player.getUUID());
        if (until == null) {
            return 1.0F;
        }
        long remaining = until - now;
        if (remaining <= 0L) {
            return 1.0F;
        }
        return 1.0F - (float) remaining / INDESTRUCTIBLE_COOLDOWN_TICKS;
    }

    private static void revive(ServerPlayer player) {
        player.setHealth(1.0F);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
