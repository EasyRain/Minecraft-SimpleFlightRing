package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.function.Consumer;

/**
 * A flight ring item. Grants traditional creative flight while it has durability
 * remaining (see {@link FlightHandler}); durability only drops while actually flying.
 * Rings are enchantable with Unbreaking, Mending and Efficiency (the rings are added
 * to the vanilla {@code enchantable/durability} and {@code enchantable/mining} item
 * tags); Efficiency levels speed up sprint-flight by 10% each.
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
        if (stack.has(ModDataComponents.INDESTRUCTIBLE.get())) {
            tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.remaining_infinite"));
            return;
        }
        int remainingPoints = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        // Take the Unbreaking enchantment into account: each level makes every
        // durability point last one extra second.
        int unbreaking = 0;
        int efficiency = 0;
        int stability = 0;
        int rocketBoost = 0;
        if (context.registries() != null) {
            unbreaking = stack.getEnchantments()
                    .getLevel(context.registries().holderOrThrow(Enchantments.UNBREAKING));
            efficiency = stack.getEnchantments()
                    .getLevel(context.registries().holderOrThrow(Enchantments.EFFICIENCY));
            stability = stack.getEnchantments()
                    .getLevel(context.registries().holderOrThrow(ModEnchantments.FLIGHT_STABILITY));
            rocketBoost = stack.getEnchantments()
                    .getLevel(context.registries().holderOrThrow(ModEnchantments.ROCKET_BOOST));
        }
        int remainingSeconds = remainingPoints * (1 + unbreaking);
        if (remainingSeconds >= 60_000) {
            int hours = remainingSeconds / 3600;
            int minutes = (remainingSeconds % 3600) / 60;
            int seconds = remainingSeconds % 60;
            tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.remaining_time_long", hours, minutes, seconds));
        } else {
            int minutes = remainingSeconds / 60;
            int seconds = remainingSeconds % 60;
            tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.remaining_time", minutes, seconds));
        }
        // Gray hint lines (only for enchantments actually present) keep the countdown prominent.
        if (unbreaking > 0) {
            tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.unbreaking_hint").withStyle(ChatFormatting.GRAY));
        }
        if (efficiency > 0) {
            tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.efficiency_hint").withStyle(ChatFormatting.GRAY));
        }
        if (stability > 0) {
            tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.stability_hint").withStyle(ChatFormatting.GRAY));
        }
        if (rocketBoost > 0) {
            tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.rocket_boost_hint").withStyle(ChatFormatting.GRAY));
        }
    }
}
