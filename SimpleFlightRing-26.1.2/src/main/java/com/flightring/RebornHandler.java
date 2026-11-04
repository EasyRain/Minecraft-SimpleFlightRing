package com.flightring;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 重生 (Reborn): one totem-like save. Modelled on {@link BurstTotemHandler}.
 * <p>
 * Non-indestructible rings pay with durability: each save consumes {@code maxDamage / (rarity + 1)}, i.e. a common
 * roll is used up in one save while a sacred one lasts five. Running out of durability never blocks the last
 * save - the ring is simply left at one point from breaking.
 * <p>
 * Indestructible rings (they have no durability bar at all) instead use a one-charge cooldown stored in the
 * {@code rebirth_charge} component as the number of ticks left. Keeping it on the stack means the client can draw
 * the charge bar (see {@link FlightRingItem}) without needing a time source, and the server just counts it down
 * once per tick.
 * <p>
 * 劫掠 (the raid relic's own save) always wins when a ring could do both, as specified.
 */
public final class RebornHandler {

    /** Cooldown of the indestructible ring's single charge: 60 seconds. */
    public static final int INDESTRUCTIBLE_COOLDOWN_TICKS = 1200;

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
        // A ring whose durability is used up is inert: it must not save anyone (otherwise every "last save" would
        // top it back up and the wearer would be immortal).
        if (!item.isUsable(ring)) {
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
        if (ring.has(ModDataComponents.INDESTRUCTIBLE.get())) {
            Long left = ring.get(ModDataComponents.REBIRTH_CHARGE.get());
            if (left != null && left > 0L) {
                return; // still recharging
            }
            ring.set(ModDataComponents.REBIRTH_CHARGE.get(), (long) INDESTRUCTIBLE_COOLDOWN_TICKS);
        } else {
            int share = Math.max(1, ring.getMaxDamage() / (affix.rarity().ordinal() + 1));
            // Never clamp back below "broken": paying with the last scrap of durability breaks the ring, which is
            // what ends the chain of saves. (Repairing it brings the charge back, which is intended.)
            ring.setDamageValue(Math.min(ring.getMaxDamage(), ring.getDamageValue() + share));
        }
        event.setNewDamage(0.0F);
        revive(player);
    }

    /** Counts the charge down on the worn ring once a second, so the bar stays current without spamming syncs. */
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        Long left = ring.get(ModDataComponents.REBIRTH_CHARGE.get());
        if (left == null || left <= 0L) {
            return;
        }
        long next = left - 20L;
        if (next <= 0L) {
            ring.remove(ModDataComponents.REBIRTH_CHARGE.get());
        } else {
            ring.set(ModDataComponents.REBIRTH_CHARGE.get(), next);
        }
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
