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

    /** Level cost of one reforge; vanilla charges this and checks it for us. */
    public static final int REFORGE_LEVEL_COST = 10;

    private RingAnvilHandler() {
    }

    /** True for the plain stone and the directed one - anything that can be put on the right anvil slot. */
    public static boolean isReforgeStone(ItemStack stack) {
        return stack.is(ModItems.REFORGE_STONE.get()) || stack.is(ModItems.REFORGE_STONE_DIRECTED.get());
    }

    public static void onUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!(left.getItem() instanceof FlightRingItem) || !isReforgeStone(right)) {
            return;
        }
        ItemStack preview = left.copy();
        preview.remove(ModDataComponents.RING_AFFIX.get());
        // A directed stone (see ReforgeMode) rerolls only one half, so the marker carries the mode and the affix
        // the ring had: the settle step keeps the half this mode is not about.
        ReforgeMode mode = ReforgeMode.of(right);
        preview.set(ModDataComponents.PENDING_REFORGE.get(),
                new PendingReforge(mode, left.get(ModDataComponents.RING_AFFIX.get())));
        event.setOutput(preview);
        event.setCost(REFORGE_LEVEL_COST);
        event.setMaterialCost(1);
    }

    public static void onTaken(AnvilRepairEvent event) {
        if (!(event.getLeft().getItem() instanceof FlightRingItem)) {
            return;
        }
        if (!isReforgeStone(event.getRight())) {
            return;
        }
        // This event fires on BOTH sides (the client's own menu takes the result too), which made the ring roll
        // twice. Only the server may roll.
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer)) {
            return;
        }
        Player player = event.getEntity();
        // One take produces several stacks carrying the pending marker: the event's output, the copy vanilla
        // puts into the inventory (and, when the bag is full, an item entity). Rolling all of them rolled the
        // ring two or three times, so roll exactly one - the one the player actually received - and only strip
        // the marker from the rest.
        // Only roll what actually landed in the inventory (shift-click). A normal click hands the result to the
        // cursor as a *copy*, so rolling the event's output here would roll a throw-away stack and print a
        // message for a ring the player never receives - the cursor tick settles that case instead.
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
            // Nothing in the inventory: the result is on the cursor (or was dropped). Leave every marker alone so
            // the cursor tick / the pickup fallback can settle it exactly once.
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
        if (!(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer)) {
            return;
        }
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
    public static boolean rollIfPending(ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty() || !stack.has(ModDataComponents.PENDING_REFORGE.get())) {
            return false;
        }
        PendingReforge pending = stack.get(ModDataComponents.PENDING_REFORGE.get());
        stack.remove(ModDataComponents.PENDING_REFORGE.get());
        if (!(stack.getItem() instanceof FlightRingItem)) {
            return false;
        }
        // A directed stone keeps the half it is not rerolling; a ring without an affix has nothing to keep, so
        // then the mode simply falls back to rolling both.
        RingAffix kept = pending == null ? null : pending.kept();
        ReforgeMode mode = pending == null ? ReforgeMode.BOTH : pending.mode();
        RingAffix affix;
        if (kept != null && mode == ReforgeMode.PREFIX) {
            affix = RingAffix.roll(player.getRandom(), null, kept.rarity());
        } else if (kept != null && mode == ReforgeMode.RARITY) {
            affix = RingAffix.roll(player.getRandom(), kept.prefix(), null);
        } else {
            affix = RingAffix.roll(player.getRandom(), null, null);
        }
        stack.set(ModDataComponents.RING_AFFIX.get(), affix);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                        "simpleflightring.command.reforge.done", affix.displayName()),
                false);
        return true;
    }
}
