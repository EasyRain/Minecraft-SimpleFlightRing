package com.flightring;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side flight logic (vanilla "creative flight" style):
 * <ul>
 *   <li>While a ring with durability remaining is present (Curios flight ring slot first,
 *       then the player inventory), the player is granted {@code mayfly}.</li>
 *   <li>Double-tapping space toggles flight exactly like in creative mode.</li>
 *   <li>Durability is consumed only while actually flying: 1 point per second.
 *       Each Unbreaking level extends one durability point by one extra second
 *       (Unbreaking III = 1 point per 4 seconds).</li>
 *   <li>A fully consumed ring never breaks: it becomes inert and can still be
 *       repaired (Mending/anvil) or used in upgrade recipes.</li>
 * </ul>
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public class FlightHandler {

    private static final Map<UUID, PlayerState> STATES = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        tick(serverPlayer);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // Respawn: start with a clean state (abilities are reset by the game anyway).
        STATES.remove(event.getOriginal().getUUID());
    }

    /**
     * Generic de-duplication against any other mod that also cancels the
     * mid-air mining slow-down (BreakSpeed x5 handlers, other mixins, ...).
     * PlayerMixin already cancels the vanilla /5 divisor while flying with
     * Flight Stability, so the event's original speed is the un-slow-downed
     * ground speed (which already includes attribute/enchantment/potion
     * bonuses). Only obvious stacking is clamped: speeds above 2x the ground
     * speed are treated as another mod's slow-down removal and clamped back;
     * smaller event modifications (normal bonuses up to 2x) are preserved.
     * Runs last so it sees all modifications.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player.getAbilities().flying && !player.onGround() && hasFlightStability(player)) {
            float groundSpeed = event.getOriginalSpeed();
            if (event.getNewSpeed() > groundSpeed * 2.0F) {
                event.setNewSpeed(groundSpeed);
            }
        }
    }

    /**
     * Whether the player carries a flight ring with the Flight Stability
     * enchantment (Curios slot, inventory, offhand or sophisticated backpacks).
     * Used by {@code PlayerMixin} and the {@link #onBreakSpeed} de-duplication.
     */
    public static boolean hasFlightStability(Player player) {
        net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> stability =
                player.registryAccess().holderOrThrow(ModEnchantments.FLIGHT_STABILITY);
        if (CuriosCompat.isLoaded() && stabilityLevel(player, CuriosCompat.findRingInSlot(player), stability) > 0) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stabilityLevel(player, stack, stability) > 0) {
                return true;
            }
        }
        if (stabilityLevel(player, player.getInventory().offhand.get(0), stability) > 0) {
            return true;
        }
        if (BackpackCompat.isLoaded()) {
            for (BackpackCompat.BackpackRing ring : BackpackCompat.findRingsInBackpacks(player)) {
                if (stabilityLevel(player, ring.stack(), stability) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int stabilityLevel(Player player, ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> stability) {
        if (stack.getItem() instanceof FlightRingItem) {
            return stack.getEnchantmentLevel(stability);
        }
        return 0;
    }

    private static void tick(ServerPlayer player) {
        PlayerState state = STATES.computeIfAbsent(player.getUUID(), uuid -> new PlayerState());

        // Push the server-authoritative total flight time to the client every 0.5 s.
        // This is required for the HUD countdown when rings are stored inside
        // sophisticated backpacks: their contents are not reliably readable client-side.
        state.ticksSinceSync++;
        if (state.ticksSinceSync >= 10) {
            state.ticksSinceSync = 0;
            int theoretical = totalFlightSeconds(player);
            boolean flying = player.getAbilities().flying;
            // Keep the pushed value smooth (decreasing 1 second per second of actual
            // flight) instead of jumping in durability-drain steps. Re-sync with the
            // theoretical value when not flying or when the ring set/enchantments
            // changed significantly (e.g. a ring was removed or upgraded mid-flight).
            if (!flying || Math.abs(state.displaySeconds - theoretical) > 30) {
                state.displaySeconds = theoretical;
            }
            if (flying) {
                state.displayTickCounter += 10;
                if (state.displayTickCounter >= 20) {
                    state.displayTickCounter -= 20;
                    if (state.displaySeconds > 0) {
                        state.displaySeconds--;
                    }
                }
            } else {
                state.displayTickCounter = 0;
            }
            PacketDistributor.sendToPlayer(player, new FlightTimePayload(state.displaySeconds));
        }

        // Creative/spectator players already have vanilla flight; the ring is left untouched.
        if (player.isCreative() || player.isSpectator()) {
            STATES.remove(player.getUUID());
            return;
        }

        ItemStack ring = findRing(player, state);

        if (!ring.isEmpty()) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                state.grantedByUs = true;
                player.onUpdateAbilities();
            }

            if (player.getAbilities().flying) {
                state.ticksFlying++;
                // 20 ticks = 1 second; Unbreaking N extends each point to (N + 1) seconds.
                int interval = 20 * (1 + unbreakingLevel(player, ring));
                if (state.ticksFlying >= interval) {
                    state.ticksFlying = 0;
                    if (ring.has(ModDataComponents.INDESTRUCTIBLE.get())) {
                        // Indestructible ring (forged with the Indestructible Core):
                        // never consumes durability.
                        return;
                    }
                    // Deterministic drain: bypass the vanilla probabilistic Unbreaking roll.
                    ring.setDamageValue(ring.getDamageValue() + 1);
                    if (state.activeBackpackRing != null) {
                        // Persist the change into the backpack's item data, otherwise it is
                        // lost whenever the backpack is reloaded (e.g. moved between slots).
                        BackpackCompat.writeBack(state.activeBackpackRing);
                    }
                    if (ring.getDamageValue() >= ring.getMaxDamage()) {
                        // Ring fully consumed: stop flight right away (the ring itself stays).
                        revokeFlight(player, state);
                    }
                }
            } else {
                state.ticksFlying = 0;
            }
        } else {
            if (state.grantedByUs) {
                revokeFlight(player, state);
            }
            state.ticksFlying = 0;
        }

        // Efficiency enchantment: each level adds 10% sprint-flight speed.
        float targetSpeed = 0.05F;
        if (!ring.isEmpty() && player.getAbilities().flying && player.isSprinting()) {
            int efficiency = efficiencyLevel(player, ring);
            if (efficiency > 0) {
                targetSpeed = 0.05F * (1.0F + 0.1F * efficiency);
            }
        }
        if (Math.abs(player.getAbilities().getFlyingSpeed() - targetSpeed) > 1.0E-4F) {
            player.getAbilities().setFlyingSpeed(targetSpeed);
            player.onUpdateAbilities();
        }
    }

    private static int efficiencyLevel(ServerPlayer player, ItemStack ring) {
        return ring.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.EFFICIENCY));
    }

    private static int unbreakingLevel(ServerPlayer player, ItemStack ring) {
        return ring.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.UNBREAKING));
    }

    /**
     * Rocket Boost durability cost (server-authoritative). Each boost adds
     * {@code level * 10} seconds to a per-player accumulator; one durability point is
     * consumed for every {@code 1 + Unbreaking} seconds actually accumulated, with the
     * remainder carried over to the next boost. This keeps the cost at
     * "level * 10 seconds" even with very high Unbreaking and avoids per-boost rounding.
     */
    public static void applyRocketBoost(ServerPlayer player) {
        if (!player.isFallFlying()) {
            return;
        }
        // Creative and spectator players never pay durability (like vanilla flight).
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        PlayerState state = STATES.computeIfAbsent(player.getUUID(), uuid -> new PlayerState());
        ItemStack ring = findRocketBoostRing(player);
        if (ring.isEmpty() || ring.has(ModDataComponents.INDESTRUCTIBLE.get())) {
            return; // no ring, or an infinite ring (boost is free)
        }
        int level = rocketBoostLevel(player, ring);
        if (level <= 0) {
            return;
        }
        int secondsPerPoint = 1 + unbreakingLevel(player, ring);
        state.boostTicks += level * 10 * 20; // level * 10 seconds, in ticks
        int interval = 20 * secondsPerPoint;
        int points = state.boostTicks / interval;
        if (points > 0) {
            state.boostTicks -= points * interval;
            ring.setDamageValue(Math.min(ring.getDamageValue() + points, ring.getMaxDamage()));
        }
    }

    private static int rocketBoostLevel(ServerPlayer player, ItemStack stack) {
        if (stack.getItem() instanceof FlightRingItem) {
            return stack.getEnchantmentLevel(player.registryAccess().holderOrThrow(ModEnchantments.ROCKET_BOOST));
        }
        return 0;
    }

    private static ItemStack findRocketBoostRing(ServerPlayer player) {
        if (CuriosCompat.isLoaded()) {
            ItemStack stack = CuriosCompat.findRingInSlot(player);
            if (rocketBoostLevel(player, stack) > 0) {
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (rocketBoostLevel(player, stack) > 0) {
                return stack;
            }
        }
        ItemStack offhand = player.getInventory().offhand.get(0);
        if (rocketBoostLevel(player, offhand) > 0) {
            return offhand;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Server-authoritative total remaining flight time (seconds) across every usable
     * ring the player carries: Curios flight ring slot, inventory, offhand and
     * sophisticated backpacks. Pushed to the client for the HUD countdown.
     */
    /**
     * Server-authoritative total remaining flight time (seconds) across every usable
     * ring the player carries: Curios flight ring slot, inventory, offhand and
     * sophisticated backpacks. Pushed to the client for the HUD countdown.
     * <p>
     * Each ring contributes {@code remaining durability points * (1 + Unbreaking level)}
     * seconds, i.e. the actual flight time taking the ring's enchantments into account.
     */
    private static int totalFlightSeconds(ServerPlayer player) {
        // An indestructible ring means infinite flight time; the HUD then hides
        // the countdown entirely (signalled with -1).
        if (hasIndestructibleRing(player)) {
            return -1;
        }
        int total = 0;
        if (CuriosCompat.isLoaded()) {
            total += usableSeconds(player, CuriosCompat.findRingInSlot(player));
        }
        for (ItemStack stack : player.getInventory().items) {
            total += usableSeconds(player, stack);
        }
        total += usableSeconds(player, player.getInventory().offhand.get(0));
        if (BackpackCompat.isLoaded()) {
            for (BackpackCompat.BackpackRing ring : BackpackCompat.findRingsInBackpacks(player)) {
                total += usableSeconds(player, ring.stack());
            }
        }
        return total;
    }

    private static boolean hasIndestructibleRing(ServerPlayer player) {
        if (CuriosCompat.isLoaded() && isIndestructibleRing(CuriosCompat.findRingInSlot(player))) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (isIndestructibleRing(stack)) {
                return true;
            }
        }
        if (isIndestructibleRing(player.getInventory().offhand.get(0))) {
            return true;
        }
        if (BackpackCompat.isLoaded()) {
            for (BackpackCompat.BackpackRing ring : BackpackCompat.findRingsInBackpacks(player)) {
                if (isIndestructibleRing(ring.stack())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isIndestructibleRing(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem && stack.has(ModDataComponents.INDESTRUCTIBLE.get());
    }

    private static int usableSeconds(ServerPlayer player, ItemStack stack) {
        if (stack.getItem() instanceof FlightRingItem && stack.getDamageValue() < stack.getMaxDamage()) {
            int remainingPoints = stack.getMaxDamage() - stack.getDamageValue();
            // Unbreaking N makes each durability point last (N + 1) seconds.
            return remainingPoints * (1 + unbreakingLevel(player, stack));
        }
        return 0;
    }

    private static void revokeFlight(ServerPlayer player, PlayerState state) {
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        state.grantedByUs = false;
        state.ticksFlying = 0;
        player.onUpdateAbilities();
    }

    /**
     * Finds the active ring: first the Curios flight ring slot (if Curios is loaded),
     * then the main inventory and offhand, then sophisticated backpacks carried by
     * the player (if Sophisticated Backpacks is loaded). A fully consumed ring does
     * not count. An indestructible ring is preferred over regular rings, so regular
     * rings never lose durability while an infinite one is carried. When the ring
     * comes from a backpack, its location is stored in {@code state.activeBackpackRing}
     * so durability changes can be written back.
     */
    private static ItemStack findRing(ServerPlayer player, PlayerState state) {
        ItemStack infinite = findRingMatching(player, state, true);
        if (!infinite.isEmpty()) {
            return infinite;
        }
        return findRingMatching(player, state, false);
    }

    private static ItemStack findRingMatching(ServerPlayer player, PlayerState state, boolean wantInfinite) {
        if (CuriosCompat.isLoaded()) {
            ItemStack stack = CuriosCompat.findRingInSlot(player);
            if (matchesFilter(stack, wantInfinite)) {
                state.activeBackpackRing = null;
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (matchesFilter(stack, wantInfinite)) {
                state.activeBackpackRing = null;
                return stack;
            }
        }
        ItemStack offhand = player.getInventory().offhand.get(0);
        if (matchesFilter(offhand, wantInfinite)) {
            state.activeBackpackRing = null;
            return offhand;
        }
        if (BackpackCompat.isLoaded()) {
            for (BackpackCompat.BackpackRing ring : BackpackCompat.findRingsInBackpacks(player)) {
                if (matchesFilter(ring.stack(), wantInfinite)) {
                    state.activeBackpackRing = ring;
                    return ring.stack();
                }
            }
        }
        state.activeBackpackRing = null;
        return ItemStack.EMPTY;
    }

    private static boolean matchesFilter(ItemStack stack, boolean wantInfinite) {
        return isUsableRing(stack) && stack.has(ModDataComponents.INDESTRUCTIBLE.get()) == wantInfinite;
    }

    private static boolean isUsableRing(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem
                && (stack.has(ModDataComponents.INDESTRUCTIBLE.get()) || stack.getDamageValue() < stack.getMaxDamage());
    }

    private static class PlayerState {
        int ticksFlying;
        int ticksSinceSync;
        /** Accumulated rocket-boost time (ticks) not yet converted into durability. */
        int boostTicks;
        boolean grantedByUs;
        /** Smooth HUD value (seconds), decreasing 1 per second of actual flight. */
        int displaySeconds;
        /** Ticks accumulated for the smooth display decrement (every 20 ticks = 1 second). */
        int displayTickCounter;
        BackpackCompat.BackpackRing activeBackpackRing;
    }
}
