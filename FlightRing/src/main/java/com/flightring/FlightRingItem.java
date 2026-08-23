package com.flightring;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A flight ring item. Grants traditional creative flight while it has durability
 * remaining (see {@link FlightHandler}); durability only drops while actually flying.
 */
public class FlightRingItem extends Item {

    private final RingTier tier;

    public FlightRingItem(RingTier tier, Properties properties) {
        super(properties.durability(tier.getMaxDurability()));
        this.tier = tier;
    }

    public RingTier getTier() {
        return tier;
    }

    @Override
    public int getEnchantmentValue() {
        return tier.getEnchantmentValue();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        int remainingSeconds = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        tooltipComponents.add(Component.translatable("tooltip.flightring.remaining_time", minutes, seconds));
        tooltipComponents.add(Component.translatable("tooltip.flightring.hint"));
        tooltipComponents.add(Component.translatable("tooltip.flightring.unbreaking_hint"));
    }
}
