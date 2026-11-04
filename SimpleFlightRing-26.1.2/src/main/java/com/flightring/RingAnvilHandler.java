package com.flightring;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;

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
 * 26.x notes: {@code AnvilRepairEvent} became {@code AnvilCraftEvent.Post} and the level cost is
 * {@code getXpCost/setXpCost(int)} (1.21.1: {@code getCost/setCost(long)}). Also
 * {@code Inventory#items/#offhand} are private here, so the main inventory is {@code getNonEquipmentItems()}
 * and {@code displayClientMessage} is {@code sendSystemMessage}.
 */
public final class RingAnvilHandler {

    /** Level cost of one reforge; vanilla charges this and checks it for us. */
    public static final int REFORGE_LEVEL_COST = 10;

    private RingAnvilHandler() {
    }

    public static void onUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!(left.getItem() instanceof FlightRingItem) || !right.is(ModItems.REFORGE_STONE.get())) {
            return;
        }
        ItemStack preview = left.copy();
        preview.remove(ModDataComponents.RING_AFFIX.get());
        preview.set(ModDataComponents.PENDING_REFORGE.get(), net.minecraft.util.Unit.INSTANCE);
        event.setOutput(preview);
        event.setXpCost(REFORGE_LEVEL_COST);
        event.setMaterialCost(1);
    }

    public static void onTaken(AnvilCraftEvent.Post event) {
        if (!(event.getLeft().getItem() instanceof FlightRingItem)) {
            return;
        }
        if (!event.getRight().is(ModItems.REFORGE_STONE.get())) {
            return;
        }
        Player player = event.getEntity();
        // The event's output is the stack vanilla is handing over; roll it in place.
        rollIfPending(event.getOutput(), player);
        // With a full inventory vanilla drops the result as an item entity instead; roll it there too. The
        // inventory scan also covers the case where the handed-over stack is a copy this event never saw.
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            rollIfPending(stack, player);
        }
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
        player.sendSystemMessage(
                net.minecraft.network.chat.Component.translatable(
                        "simpleflightring.command.reforge.done", affix.displayName()));
        return true;
    }
}
