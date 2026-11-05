package com.flightring;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The directed reforge stone: rerolls only the half stored in its {@code reforge_mode} component.
 * <p>
 * Its display name follows that component ("词缀定向重铸石" / "稀有度定向重铸石"), so one item covers both
 * kinds and the stone keeps its mode through crafting, copying and storage. A stone without the component
 * behaves like a plain one.
 */
public class DirectedReforgeStoneItem extends Item {

    public DirectedReforgeStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        ReforgeMode mode = stack.get(ModDataComponents.REFORGE_MODE.get());
        if (mode != null && mode != ReforgeMode.BOTH) {
            return Component.translatable("item.simpleflightring.reforge_stone_directed." + mode.key());
        }
        return super.getName(stack);
    }
}
