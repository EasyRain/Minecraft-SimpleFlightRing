package com.flightring;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * A flight ring item. Grants traditional creative flight while it has durability
 * remaining (see {@link FlightHandler}); durability only drops while actually flying.
 */
public class FlightRingItem extends Item {

    private final RingTier tier;

    public FlightRingItem(RingTier tier, Properties properties) {
        super(properties
                .durability(tier.getMaxDurability())
                .enchantable(tier.getEnchantmentValue()));
        this.tier = tier;
    }

    public RingTier getTier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        int remainingSeconds = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        tooltipComponents.accept(Component.translatable("tooltip.flightring.remaining_time", minutes, seconds));
        tooltipComponents.accept(Component.translatable("tooltip.flightring.hint"));
        tooltipComponents.accept(Component.translatable("tooltip.flightring.unbreaking_hint"));
    }
}
