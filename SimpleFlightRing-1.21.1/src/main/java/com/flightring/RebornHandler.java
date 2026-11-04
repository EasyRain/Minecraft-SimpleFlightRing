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

    /**
     * Entity event id used to ask the client to play the totem flip with the worn ring. Vanilla uses 35 for the
     * real totem (left untouched, so 劫掠 keeps its normal animation); 80 is inside the free mod range.
     */
    public static final byte REBIRTH_ANIMATION_EVENT = (byte) 80;

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
        // Priority (user 2026-11-04): a durability-priced ring lets 劫掠 take the hit while the raid ring still
        // has a totem charge - that costs no durability, so it is the cheaper way out. Once those charges are
        // spent we must still save the wearer, so the condition includes the charge count. An indestructible ring
        // is the other way round: this handler runs at HIGH priority, so its recharge-priced 重生 fires first and
        // the raid never gets to spend a charge.
        boolean indestructible = ring.has(ModDataComponents.INDESTRUCTIBLE.get());
        if (!indestructible && item.hasAbility(RingAbility.RAID_PLUNDER) && RaidCharges.of(ring) > 0) {
            return;
        }
        if (indestructible) {
            // Same number of charges as the durability version has uses: rarity + 1. Each charge takes
            // INDESTRUCTIBLE_COOLDOWN_TICKS to come back, so a sacred ring needs 5 x 60s to be full again.
            int charges = affix.rarity().ordinal() + 1;
            long spent = ring.getOrDefault(ModDataComponents.REBIRTH_CHARGE.get(), 0L);
            if (spent > (long) (charges - 1) * INDESTRUCTIBLE_COOLDOWN_TICKS) {
                return; // no full charge available yet
            }
            ring.set(ModDataComponents.REBIRTH_CHARGE.get(), spent + INDESTRUCTIBLE_COOLDOWN_TICKS);
        } else {
            int share = Math.max(1, ring.getMaxDamage() / (affix.rarity().ordinal() + 1));
            // Never clamp back below "broken": paying with the last scrap of durability breaks the ring, which is
            // what ends the chain of saves. (Repairing it brings the charge back, which is intended.)
            ring.setDamageValue(Math.min(ring.getMaxDamage(), ring.getDamageValue() + share));
        }
        event.setNewDamage(0.0F);
        // Tell the wearer how many saves are left (user 2026-11-04): the affix's remaining count, plus the raid
        // ring's built-in totem charges when it has them.
        int charges = affix.rarity().ordinal() + 1;
        int remaining;
        if (indestructible) {
            long spent = ring.getOrDefault(ModDataComponents.REBIRTH_CHARGE.get(), 0L);
            long owed = (spent + INDESTRUCTIBLE_COOLDOWN_TICKS - 1) / INDESTRUCTIBLE_COOLDOWN_TICKS;
            remaining = charges - (int) owed;
        } else {
            int share = Math.max(1, ring.getMaxDamage() / charges);
            remaining = Math.max(0, (ring.getMaxDamage() - ring.getDamageValue()) / share);
        }
        if (item.hasAbility(RingAbility.RAID_PLUNDER)) {
            remaining += RaidCharges.of(ring);
        }
        // The Unobtainium ring's burst totem is one more save, even though it runs on its own path and only
        // once - the user wants it counted in this number too (2026-11-04).
        if (item.hasAbility(RingAbility.BURST_TOTEM)) {
            remaining += 1;
        }
        // Reuse the raid ring's own message and action bar (user 2026-11-04) instead of adding another line:
        // "rings blocked death for you (charges left: N)".
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "message.simpleflightring.raid_totem_used", Integer.toString(remaining)), true);
        revive(player);
    }

    /** Counts the owed recharge down on the worn ring once a second, so the bar stays current without spamming syncs. */
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        Long spent = ring.get(ModDataComponents.REBIRTH_CHARGE.get());
        if (spent == null || spent <= 0L) {
            return;
        }
        long next = spent - 20L;
        if (next <= 0L) {
            ring.remove(ModDataComponents.REBIRTH_CHARGE.get());
        } else {
            ring.set(ModDataComponents.REBIRTH_CHARGE.get(), next);
        }
    }

    /**
     * Asks the client to play the totem flip animation with the worn ring instead of a totem of undying. Shared by
     * 重生 and by the relic rings' own totem-like saves (e.g. the Unobtainium ring's burst totem), since the user
     * wants every such effect to flip the ring - 劫掠 keeps vanilla's animation because it goes through the vanilla
     * entity event 35 and never calls this.
     */
    public static void playRingAnimation(ServerPlayer player) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundEntityEventPacket(
                player, REBIRTH_ANIMATION_EVENT));
    }

    private static void revive(ServerPlayer player) {
        player.setHealth(1.0F);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        playRingAnimation(player);
    }
}
