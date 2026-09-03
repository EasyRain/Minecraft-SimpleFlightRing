package com.flightring;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Optional Curios API integration.
 * <p>
 * This class must only be touched when Curios is actually loaded (see {@link #isLoaded()}),
 * so that the mod keeps working without the dependency.
 */
public final class CuriosCompat {

    /** Identifier of the extra "flight ring" slot type. */
    public static final String SLOT_ID = "flight_ring";

    private static boolean registered = false;

    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("curios");
    }

    /** Registers the curio behavior (right-click equipping) for every ring. */
    public static void register() {
        if (registered) {
            return;
        }
        for (var ring : ModItems.ALL) {
            CuriosApi.registerCurio(ring.get(), new ICurioItem() {
                @Override
                public void onEquipFromUse(SlotContext slotContext, ItemStack stack) {
                    // Curios 15.x plays the equip sound whenever the slot content is
                    // synchronized (e.g. while the Curios menu is open). The flight ring's
                    // durability changes every second while flying, which would trigger the
                    // sound once per second. Suppress it here.
                }

                @Override
                public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
                    // Only play the equip sound when a different item is actually put into
                    // the slot; pure data changes (durability drain) stay silent.
                    if (!ItemStack.isSameItem(prevStack, stack)) {
                        ICurioItem.super.onEquip(slotContext, prevStack, stack);
                    }
                }

                @Override
                public boolean canSync(SlotContext slotContext, ItemStack stack) {
                    // The flight ring has no on-body render, so the cosmetic
                    // "show/hide" toggle in the Curios GUI is meaningless — hide it.
                    return false;
                }
            });
        }
        registered = true;
    }

    /** Returns the ring equipped in the flight ring slot, or {@link ItemStack#EMPTY}. */
    public static ItemStack findRingInSlot(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findCurio(SLOT_ID, 0))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }
}
