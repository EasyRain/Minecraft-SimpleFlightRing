package com.flightring;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Copies the "other attributes" of a ring onto the upgraded ring:
 * enchantments, custom name and lore. Durability is intentionally NOT copied
 * (upgraded rings start at full durability).
 */
public final class RingUpgradeHelper {

    private static final Set<DataComponentType<?>> COPIED_COMPONENTS = Set.of(
            DataComponents.ENCHANTMENTS,
            DataComponents.CUSTOM_NAME,
            DataComponents.LORE,
            ModDataComponents.INDESTRUCTIBLE.get()
    );

    private RingUpgradeHelper() {
    }

    public static void copyComponents(ItemStack from, ItemStack to) {
        for (DataComponentType<?> type : COPIED_COMPONENTS) {
            if (from.has(type)) {
                copyComponent(type, from, to);
            }
        }
    }

    private static <T> void copyComponent(DataComponentType<T> type, ItemStack from, ItemStack to) {
        T value = from.get(type);
        to.set(type, value);
    }
}
