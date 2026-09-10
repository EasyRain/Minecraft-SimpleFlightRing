package com.flightring;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
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
 *   <li>Damage is paid with energy 1:1. If the pool cannot cover the whole hit, it is
 *       emptied and only the remainder is applied to the player.</li>
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
     * Any hit taken interrupts the refill, even a hit that was fully absorbed, and an
     * uninterrupted {@value RingEnergy#IDLE_SECONDS} seconds are required before the
     * pool starts refilling again.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getAmount() <= 0.0F || ignoresEnergy(event.getSource())) {
            return;
        }
        if (wornRing(player, RingAbility.MAGIC_LINING).isEmpty()) {
            return;
        }
        LAST_DAMAGE.put(player.getUUID(), player.level().getGameTime());
    }

    /**
     * The actual absorption. This event fires after armour and potion reduction, so the
     * energy is spent on the damage the player would really take.
     */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        float damage = event.getNewDamage();
        if (damage <= 0.0F || ignoresEnergy(event.getSource())) {
            return;
        }
        ItemStack ring = wornRing(player, RingAbility.MAGIC_LINING);
        if (ring.isEmpty()) {
            return;
        }
        float energy = RingEnergy.get(ring);
        if (energy <= 0.0F) {
            return; // empty pool: nothing to absorb, the hit lands in full
        }
        float absorbed = Math.min(energy, damage);
        RingEnergy.set(ring, energy - absorbed);
        event.setNewDamage(damage - absorbed);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.level().getGameTime();
        ItemStack ring = wornRing(player, RingAbility.MAGIC_LINING);

        // Push the pool to the client for the HUD (twice a second while a ring is worn,
        // only every other second otherwise, just often enough to hide the bar).
        boolean syncNow = ring.isEmpty() ? now % (SYNC_INTERVAL * 4) == 0 : now % SYNC_INTERVAL == 0;
        if (syncNow) {
            float max = ring.isEmpty() ? 0.0F : RingEnergy.max(ring);
            float energy = ring.isEmpty() ? -1.0F : RingEnergy.get(ring);
            PacketDistributor.sendToPlayer(player, new RingEnergyPayload(energy, max));
        }

        if (ring.isEmpty()) {
            LAST_DAMAGE.remove(player.getUUID());
            return;
        }
        float max = RingEnergy.max(ring);
        float energy = RingEnergy.get(ring);
        if (energy >= max) {
            return; // full: nothing to do until the next hit
        }
        // No damage for IDLE_TICKS, then one refill step per second.
        long lastDamage = LAST_DAMAGE.computeIfAbsent(player.getUUID(), uuid -> now - RingEnergy.IDLE_TICKS);
        long idle = now - lastDamage;
        if (idle < RingEnergy.IDLE_TICKS || (idle - RingEnergy.IDLE_TICKS) % RingEnergy.REFILL_STEP_TICKS != 0) {
            return;
        }
        RingEnergy.set(ring, energy + RingEnergy.refillStep(max));
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
