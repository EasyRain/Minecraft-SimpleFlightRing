package com.flightring;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
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
    }

    private static int unbreakingLevel(ServerPlayer player, ItemStack ring) {
        return ring.getEnchantments().getLevel(player.registryAccess().holderOrThrow(Enchantments.UNBREAKING));
    }

    private static void revokeFlight(ServerPlayer player, PlayerState state) {
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        state.grantedByUs = false;
        state.ticksFlying = 0;
        player.onUpdateAbilities();
    }

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
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            total += usableSeconds(player, stack);
        }
        total += usableSeconds(player, player.getItemBySlot(EquipmentSlot.OFFHAND));
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
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (isIndestructibleRing(stack)) {
                return true;
            }
        }
        if (isIndestructibleRing(player.getItemBySlot(EquipmentSlot.OFFHAND))) {
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

    /**
     * Finds the active ring: first the Curios flight ring slot (if Curios is loaded),
     * then the main inventory and offhand, then sophisticated backpacks carried by
     * the player (if Sophisticated Backpacks is loaded). A fully consumed ring does
     * not count. When the ring comes from a backpack, its location is stored in
     * {@code state.activeBackpackRing} so durability changes can be written back.
     */
    private static ItemStack findRing(ServerPlayer player, PlayerState state) {
        if (CuriosCompat.isLoaded()) {
            ItemStack stack = CuriosCompat.findRingInSlot(player);
            if (isUsableRing(stack)) {
                state.activeBackpackRing = null;
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (isUsableRing(stack)) {
                state.activeBackpackRing = null;
                return stack;
            }
        }
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (isUsableRing(offhand)) {
            state.activeBackpackRing = null;
            return offhand;
        }
        if (BackpackCompat.isLoaded()) {
            state.activeBackpackRing = BackpackCompat.findFirstUsableRing(player).orElse(null);
            if (state.activeBackpackRing != null) {
                return state.activeBackpackRing.stack();
            }
        }
        state.activeBackpackRing = null;
        return ItemStack.EMPTY;
    }

    private static boolean isUsableRing(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem
                && (stack.has(ModDataComponents.INDESTRUCTIBLE.get()) || stack.getDamageValue() < stack.getMaxDamage());
    }

    private static class PlayerState {
        int ticksFlying;
        int ticksSinceSync;
        boolean grantedByUs;
        /** Smooth HUD value (seconds), decreasing 1 per second of actual flight. */
        int displaySeconds;
        /** Ticks accumulated for the smooth display decrement (every 20 ticks = 1 second). */
        int displayTickCounter;
        BackpackCompat.BackpackRing activeBackpackRing;
    }
}
