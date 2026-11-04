package com.flightring;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;

/**
 * Anvil reforge: a ring plus a reforge stone rolls a new affix, and the preview must not reveal it.
 * <p>
 * Deliberately minimal - vanilla does the work, we only touch two things:
 * <ol>
 *   <li>the <b>level cost</b> ({@link #REFORGE_LEVEL_COST}) - vanilla draws the result, shows the cost, checks
 *       the player has the levels and charges them on take;</li>
 *   <li>the <b>preview output</b> - a copy of the ring with the affix stripped and a {@code pending_reforge}
 *       marker, so the anvil shows a plain ring no matter what the roll will be.</li>
 * </ol>
 * The only other hook is the take itself, which rolls the affix into the ring. It writes both the event's
 * output stack and the copy that may already sit in the player's inventory, so it does not matter which of
 * the two vanilla hands over. Nothing is charged here: vanilla already took the levels.
 * <p>
 * Note: the anvil cost must never be 0 - vanilla blanks the result when {@code cost <= 0} and the result slot
 * refuses to be picked up unless {@code cost > 0}.
 */
public final class RingAnvilHandler {

    /**
     * The anvil's own level cost. It is only a <b>gate and a hook</b>: vanilla refuses to draw a result when the
     * cost is 0, and on take it charges {@code cost.get()} levels - we refund that level and charge the real
     * price in XP points (see {@link #REFORGE_XP_COST}). The anvil has always worked in "XP equivalent to
     * levels"; it is the old enchanting table that deducted whole levels.
     */
    public static final int ANVIL_LEVEL_GATE = 1;

    /** XP points one reforge really costs: the total for level 10 (levels 0..9 need 2L+7 each = 160). */
    public static final int REFORGE_XP_COST = 160;

    private RingAnvilHandler() {
    }

    public static void onUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!(left.getItem() instanceof FlightRingItem) || !right.is(ModItems.REFORGE_STONE.get())) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && player.totalExperience < REFORGE_XP_COST) {
            return; // not enough points: no result is offered (our version of vanilla's "not enough levels")
        }
        ItemStack preview = left.copy();
        preview.remove(ModDataComponents.RING_AFFIX.get());
        preview.set(ModDataComponents.PENDING_REFORGE.get(), net.minecraft.util.Unit.INSTANCE);
        event.setOutput(preview);
        event.setCost(ANVIL_LEVEL_GATE);
        event.setMaterialCost(1);
    }

    public static void onTaken(AnvilRepairEvent event) {
        if (!(event.getLeft().getItem() instanceof FlightRingItem)) {
            return;
        }
        if (!event.getRight().is(ModItems.REFORGE_STONE.get())) {
            return;
        }
        Player player = event.getEntity();
        // One take produces several stacks carrying the pending marker: the event's output, the copy vanilla
        // puts into the inventory (and, when the bag is full, an item entity). Rolling all of them rolled the
        // ring two or three times, so roll exactly one - the one the player actually received - and only strip
        // the marker from the rest.
        boolean rolled = false;
        for (ItemStack stack : player.getInventory().items) {
            if (rollIfPending(stack, player)) {
                rolled = true;
                break;
            }
        }
        if (!rolled) {
            for (ItemStack stack : player.getInventory().offhand) {
                if (rollIfPending(stack, player)) {
                    rolled = true;
                    break;
                }
            }
        }
        if (!rolled) {
            rolled = rollIfPending(event.getOutput(), player);
        }
        // Vanilla already charged ANVIL_LEVEL_GATE levels on take; give that back and charge the real price in
        // XP points here - once per reforge, whether the ring went into the bag or was dropped as an entity
        // (so the "bag full" case is paid for as well; the pickup handler only rolls, it never charges).
        player.giveExperienceLevels(ANVIL_LEVEL_GATE);
        player.giveExperiencePoints(-REFORGE_XP_COST);
        if (!rolled) {
            // Nothing to roll yet (the bag was full and the ring is an item entity): keep the marker so the
            // pickup handler can settle it.
            return;
        }
        for (ItemStack stack : player.getInventory().items) {
            clearPending(stack);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            clearPending(stack);
        }
        clearPending(event.getOutput());
    }

    /**
     * Fallback: a pending ring that ended up as an item entity is settled before it is picked up, so the copy
     * that lands in the inventory is already rolled. Nothing is charged - the player who took it paid vanilla's
     * level cost already.
     */
    public static void onPickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre event) {
        ItemStack stack = event.getItemEntity().getItem();
        if (!stack.has(ModDataComponents.PENDING_REFORGE.get())) {
            return;
        }
        rollIfPending(stack, event.getPlayer());
    }

    /** Drops the marker without rolling, for the extra copies a single take produces. */
    private static void clearPending(ItemStack stack) {
        if (stack != null && !stack.isEmpty() && stack.has(ModDataComponents.PENDING_REFORGE.get())) {
            stack.remove(ModDataComponents.PENDING_REFORGE.get());
        }
    }

    /** Rolls the affix into a pending ring; returns whether this stack was one. */
    private static boolean rollIfPending(ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty() || !stack.has(ModDataComponents.PENDING_REFORGE.get())) {
            return false;
        }
        stack.remove(ModDataComponents.PENDING_REFORGE.get());
        if (!(stack.getItem() instanceof FlightRingItem)) {
            return false;
        }
        RingAffix affix = RingAffix.roll(player.getRandom(), null, null);
        stack.set(ModDataComponents.RING_AFFIX.get(), affix);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                        "simpleflightring.command.reforge.done", affix.displayName()),
                false);
        return true;
    }
}
