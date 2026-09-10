package com.flightring;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MAGIC LINING (Allthemodium ring): while the ring is worn in the Curios slot, its
 * energy pool absorbs incoming damage.
 * <ul>
 *   <li>Damage is paid with energy 1:1. A hit the pool covers completely is cancelled
 *       outright (no damage, and no red flash / hurt sound / knockback either); if the
 *       pool cannot cover it, the pool is emptied and only the remainder lands.</li>
 *   <li>The pool starts refilling once the wearer has taken no damage for 10 seconds;
 *       a full refill always takes 3 seconds. Taking a hit interrupts and restarts
 *       that delay.</li>
 *   <li>Damage that bypasses invulnerability (void, {@code /kill}) is never absorbed.</li>
 * </ul>
 * The pool is stored on the ring itself (see {@link RingEnergy}); the numbers shown by
 * the HUD are pushed to the client by {@link #onPlayerTick}.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class MagicLiningHandler {

    /** Game time of the last hit each player took: drives the refill delay. */
    private static final Map<UUID, Long> LAST_DAMAGE = new HashMap<>();

    /** The HUD sync runs every 10 ticks (0.5 s), like the flight time sync. */
    private static final int SYNC_INTERVAL = 10;

    private MagicLiningHandler() {
    }

    /** The worn ring, when it has the given ability. */
    private static ItemStack wornRing(ServerPlayer player, RingAbility ability) {
        if (!CuriosCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        if (ring.getItem() instanceof FlightRingItem item && item.hasAbility(ability)) {
            return ring;
        }
        return ItemStack.EMPTY;
    }

    /**
     * The whole lining: the hit is paid for with the ring's energy and, when the pool
     * covers it completely, the hit is CANCELLED outright - no health loss, but also no
     * red flash, hurt sound, knockback or invulnerability frames, exactly as if the blow
     * never landed. Only the remainder of a hit the pool cannot cover keeps its effects.
     * <p>
     * The event fires before armour/enchantment reduction, so the pool pays the incoming
     * damage of the blow itself. Any hit also restarts the refill delay, even a hit that
     * was fully absorbed.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        float damage = event.getAmount();
        if (damage <= 0.0F || ignoresEnergy(event.getSource())) {
            return;
        }
        ItemStack ring = wornRing(player, RingAbility.MAGIC_LINING);
        if (ring.isEmpty()) {
            return;
        }
        LAST_DAMAGE.put(player.getUUID(), player.level().getGameTime());

        float energy = RingEnergy.get(ring);
        if (energy <= 0.0F) {
            return; // empty pool: nothing to absorb, the hit lands in full
        }
        float absorbed = Math.min(energy, damage);
        RingEnergy.set(ring, energy - absorbed);
        float remaining = damage - absorbed;
        if (remaining <= 0.0F) {
            event.setCanceled(true);
        } else {
            event.setAmount(remaining);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.level().getGameTime();
        ItemStack ring = wornRing(player, RingAbility.MAGIC_LINING);
        if (ring.isEmpty()) {
            LAST_DAMAGE.remove(player.getUUID());
            // Push "no ring" now and then, just often enough to hide the bar client-side.
            if (now % (SYNC_INTERVAL * 8) == 0) {
                PacketDistributor.sendToPlayer(player, new RingEnergyPayload(-1.0F, 0.0F));
            }
            return;
        }

        float max = RingEnergy.max(ring);
        float energy = RingEnergy.get(ring);

        // Refill: IDLE_TICKS without damage, then one step every REFILL_STEP_TICKS, so a
        // full pool always takes REFILL_SECONDS (see RingEnergy).
        if (energy < max) {
            long lastDamage = LAST_DAMAGE.computeIfAbsent(player.getUUID(), uuid -> now - RingEnergy.IDLE_TICKS);
            long idle = now - lastDamage;
            if (idle >= RingEnergy.IDLE_TICKS && (idle - RingEnergy.IDLE_TICKS) % RingEnergy.REFILL_STEP_TICKS == 0) {
                energy = Math.min(max, energy + RingEnergy.refillStep(max));
                RingEnergy.set(ring, energy);
            }
        }

        // Push the pool to the client for the HUD: with every refill step while it fills
        // (so the bar animates smoothly), once a second while it is full.
        int interval = energy < max ? RingEnergy.REFILL_STEP_TICKS : SYNC_INTERVAL;
        if (now % interval == 0) {
            PacketDistributor.sendToPlayer(player, new RingEnergyPayload(energy, max));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_DAMAGE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        LAST_DAMAGE.remove(event.getOriginal().getUUID());
    }

    /** Void damage and {@code /kill} stay lethal: the lining cannot pay for them. */
    private static boolean ignoresEnergy(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }
}
