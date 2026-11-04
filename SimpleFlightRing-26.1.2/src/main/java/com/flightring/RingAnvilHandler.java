package com.flightring;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;

/**
 * Anvil reforge: a ring plus a reforge stone rolls a new affix, and the preview must not reveal it.
 * <p>
 * Two events are used. {@code AnvilUpdateEvent} builds the preview: a copy of the ring with the affix
 * <b>stripped</b> and a {@code pending_reforge} marker, so the anvil shows a plain ring no matter what the
 * roll will be (the anti-preview rule from the plan). {@code AnvilCraftEvent.Post} fires when the player
 * actually takes the result; it finds the marked ring in the player's inventory, rolls the affix there and
 * charges the XP points. Working through the inventory instead of the event's stack keeps this correct whether
 * or not the menu hands over the same instance it showed.
 * <p>
 * 26.x notes: {@code AnvilRepairEvent} no longer exists (it became {@code AnvilCraftEvent} with Pre/Post), and
 * the level cost is called {@code xpCost} ({@code getXpCost/setXpCost}); 1.21.1 uses
 * {@code getCost/setCost(long)}. That is why this class differs between the two versions.
 * <p>
 * Cost: the anvil's own level cost is forced to 0 (otherwise the player would pay twice); the real cost is
 * {@link #REFORGE_XP_COST} <b>XP points</b> - the amount needed for level 10 (levels 0..9 need 2L+7 each),
 * not "10 levels". The reforge is refused up front when the player has fewer points than that.
 */
public final class RingAnvilHandler {

    /** XP points one reforge costs (level 10 in total: sum of 2L+7 for L=0..9). */
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
            return; // not enough points: no result is offered at all
        }
        ItemStack preview = left.copy();
        preview.remove(ModDataComponents.RING_AFFIX.get());
        preview.set(ModDataComponents.PENDING_REFORGE.get(), net.minecraft.util.Unit.INSTANCE);
        event.setOutput(preview);
        event.setXpCost(0);
        event.setMaterialCost(1);
    }

    public static void onTaken(AnvilCraftEvent.Post event) {
        if (!(event.getLeft().getItem() instanceof FlightRingItem)) {
            return;
        }
        if (!event.getRight().is(ModItems.REFORGE_STONE.get())) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean rolled = false;
        // 26.x: Inventory#items / #offhand are private; getNonEquipmentItems() is the main inventory.
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            rolled |= rollIfPending(stack, player);
        }
        // Charge exactly once, on take, whether the ring went into the inventory or was dropped as an item
        // entity (vanilla does the same for its level cost). The pickup path below therefore never charges:
        // a dropped pending ring that somebody else picks up was already paid for by the player who took it.
        player.giveExperiencePoints(-REFORGE_XP_COST);
    }

    /**
     * Fallback for the "inventory was full" case: settle the pending roll <b>before</b> the picked-up stack
     * lands in the inventory, so the copy that is added is already the rolled one.
     */
    public static void onPickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre event) {
        ItemStack stack = event.getItemEntity().getItem();
        if (!stack.has(ModDataComponents.PENDING_REFORGE.get())) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        rollIfPending(stack, player);
    }

    /** Rolls the affix into a pending ring; returns whether this stack was one. */
    private static boolean rollIfPending(ItemStack stack, ServerPlayer player) {
        if (!stack.has(ModDataComponents.PENDING_REFORGE.get())) {
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
