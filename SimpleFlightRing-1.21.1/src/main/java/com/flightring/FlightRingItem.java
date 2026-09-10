package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

/**
 * A flight ring item. Grants traditional creative flight while it has durability
 * remaining (see {@link FlightHandler}); durability only drops while actually flying.
 * Rings are enchantable with Unbreaking, Mending and Efficiency (the rings are added
 * to the vanilla {@code enchantable/durability} and {@code enchantable/mining} item
 * tags); Efficiency levels speed up sprint-flight by 10% each.
 * <p>
 * The two special rings (Stable / Powered) are built with an explicit durability and
 * are DESTROYED once fully consumed, unlike the tiered rings which merely become inert.
 */
public class FlightRingItem extends Item {

    /** Tier of the six crafting rings; {@code null} for the special rings. */
    private final RingTier tier;
    private final int enchantmentValue;
    /** Special rings are destroyed when drained; tiered rings just become inert. */
    private final boolean breaksWhenDepleted;

    public FlightRingItem(RingTier tier, Properties properties) {
        super(properties.durability(tier.getMaxDurability()));
        this.tier = tier;
        this.enchantmentValue = tier.getEnchantmentValue();
        this.breaksWhenDepleted = false;
    }

    /**
     * Special ring: explicit durability (1 point = 1 second of flight) and
     * enchantability. Destroyed once its durability runs out.
     */
    public FlightRingItem(int maxDurability, int enchantmentValue, Properties properties) {
        super(properties.durability(maxDurability));
        this.tier = null;
        this.enchantmentValue = enchantmentValue;
        this.breaksWhenDepleted = true;
    }

    public RingTier getTier() {
        return tier;
    }

    /** True for the special rings, which are destroyed instead of turning inert. */
    public boolean breaksWhenDepleted() {
        return breaksWhenDepleted;
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        // Enchantment levels are read first so their hint lines also show on the
        // indestructible (infinite) ring - only the countdown is replaced there.
        int unbreaking = 0;
        int efficiency = 0;
        int stability = 0;
        int rocketBoost = 0;
        if (context.registries() != null) {
            unbreaking = stack.getEnchantmentLevel(context.registries().holderOrThrow(Enchantments.UNBREAKING));
            efficiency = stack.getEnchantmentLevel(context.registries().holderOrThrow(Enchantments.EFFICIENCY));
            stability = stack.getEnchantmentLevel(context.registries().holderOrThrow(ModEnchantments.FLIGHT_STABILITY));
            rocketBoost = stack.getEnchantmentLevel(context.registries().holderOrThrow(ModEnchantments.ROCKET_BOOST));
        }

        if (stack.has(ModDataComponents.INDESTRUCTIBLE.get())) {
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.remaining_infinite"));
        } else {
            int remainingPoints = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
            // Unbreaking: each level makes every durability point last one extra second.
            int remainingSeconds = remainingPoints * (1 + unbreaking);
            if (remainingSeconds >= 60_000) {
                int hours = remainingSeconds / 3600;
                int minutes = (remainingSeconds % 3600) / 60;
                int seconds = remainingSeconds % 60;
                tooltipComponents.add(Component.translatable("tooltip.simpleflightring.remaining_time_long", hours, minutes, seconds));
            } else {
                int minutes = remainingSeconds / 60;
                int seconds = remainingSeconds % 60;
                tooltipComponents.add(Component.translatable("tooltip.simpleflightring.remaining_time", minutes, seconds));
            }
        }

        if (breaksWhenDepleted && !stack.has(ModDataComponents.INDESTRUCTIBLE.get())) {
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.breaks_when_depleted").withStyle(ChatFormatting.RED));
        }

        // Gray hint lines (only for enchantments actually present).
        if (unbreaking > 0) {
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.unbreaking_hint").withStyle(ChatFormatting.GRAY));
        }
        if (efficiency > 0) {
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.efficiency_hint").withStyle(ChatFormatting.GRAY));
        }
        if (stability > 0) {
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.stability_hint").withStyle(ChatFormatting.GRAY));
        }
        if (rocketBoost > 0) {
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.rocket_boost_hint").withStyle(ChatFormatting.GRAY));
        }
    }
}
