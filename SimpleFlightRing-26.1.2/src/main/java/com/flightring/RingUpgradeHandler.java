package com.flightring;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Keeps a ring's affix when the ring is upgraded into another ring.
 * <p>
 * The plan's rule: the result of "eat a ring, get a ring" is the new ring with the <b>same</b> affix component
 * copied over - no number merging, because the numbers are derived from {@link RingAffix}'s tables anyway.
 * That is also why only the component is copied: rebalancing the tables then applies to rings players already
 * upgraded.
 * <p>
 * Hooked on {@code ItemCraftedEvent} (the crafting grid), which covers every crafting-table upgrade - the tier
 * chain as well as the Stable/Powered rings. The one smithing-table upgrade (netherite) is not covered here
 * because NeoForge has no smithing event; TODO(framework): handle it with a small {@code SmithingMenu} hook or
 * by giving the netherite recipe a dedicated treatment.
 */
public final class RingUpgradeHandler {

    private RingUpgradeHandler() {
    }

    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack crafted = event.getCrafting();
        if (!(crafted.getItem() instanceof FlightRingItem)) {
            return;
        }
        if (crafted.has(ModDataComponents.RING_AFFIX.get())) {
            return; // the result already carries one (e.g. a loot-table craft); nothing to copy
        }
        Container grid = event.getInventory();
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack input = grid.getItem(slot);
            if (!(input.getItem() instanceof FlightRingItem)) {
                continue;
            }
            RingAffix affix = input.get(ModDataComponents.RING_AFFIX.get());
            if (affix != null) {
                crafted.set(ModDataComponents.RING_AFFIX.get(), affix);
                return;
            }
        }
    }
}
