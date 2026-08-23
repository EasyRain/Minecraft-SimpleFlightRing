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

    private static void tick(ServerPlayer player) {
        PlayerState state = STATES.computeIfAbsent(player.getUUID(), uuid -> new PlayerState());

        // Push the server-authoritative total flight time to the client every 0.5 s.
        // This is required for the HUD countdown when rings are stored inside
        // sophisticated backpacks: their contents are not reliably readable client-side.
        state.ticksSinceSync++;
        if (state.ticksSinceSync >= 10) {
            state.ticksSinceSync = 0;
            PacketDistributor.sendToPlayer(player, new FlightTimePayload(totalFlightSeconds(player)));
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
        return ring.getEnchantmentLevel(player.registryAccess().holderOrThrow(Enchantments.UNBREAKING));
    }

    /**
     * Server-authoritative total remaining flight time (seconds) across every usable
     * ring the player carries: Curios flight ring slot, inventory, offhand and
     * sophisticated backpacks. Pushed to the client for the HUD countdown.
     */
    private static int totalFlightSeconds(ServerPlayer player) {
        int total = 0;
        if (CuriosCompat.isLoaded()) {
            total += usableSeconds(CuriosCompat.findRingInSlot(player));
        }
        for (ItemStack stack : player.getInventory().items) {
            total += usableSeconds(stack);
        }
        total += usableSeconds(player.getInventory().offhand.get(0));
        if (BackpackCompat.isLoaded()) {
            for (BackpackCompat.BackpackRing ring : BackpackCompat.findRingsInBackpacks(player)) {
                total += usableSeconds(ring.stack());
            }
        }
        return total;
    }

    private static int usableSeconds(ItemStack stack) {
        if (stack.getItem() instanceof FlightRingItem && stack.getDamageValue() < stack.getMaxDamage()) {
            return stack.getMaxDamage() - stack.getDamageValue();
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
        for (ItemStack stack : player.getInventory().items) {
            if (isUsableRing(stack)) {
                state.activeBackpackRing = null;
                return stack;
            }
        }
        ItemStack offhand = player.getInventory().offhand.get(0);
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
        return stack.getItem() instanceof FlightRingItem && stack.getDamageValue() < stack.getMaxDamage();
    }

    private static class PlayerState {
        int ticksFlying;
        int ticksSinceSync;
        boolean grantedByUs;
        BackpackCompat.BackpackRing activeBackpackRing;
    }
}
