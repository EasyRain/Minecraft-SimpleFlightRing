package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.function.Consumer;

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
    /** Special rings are destroyed when drained; tiered rings just become inert. */
    private final boolean breaksWhenDepleted;

    public FlightRingItem(RingTier tier, Properties properties) {
        super(properties
                .durability(tier.getMaxDurability())
                .enchantable(tier.getEnchantmentValue()));
        this.tier = tier;
        this.breaksWhenDepleted = false;
    }

    /**
     * Special ring: explicit durability (1 point = 1 second of flight) and
     * enchantability. Destroyed once its durability runs out.
     */
    public FlightRingItem(int maxDurability, int enchantmentValue, Properties properties) {
        super(properties
                .durability(maxDurability)
                .enchantable(enchantmentValue));
        this.tier = null;
        this.breaksWhenDepleted = true;
    }

    public RingTier getTier() {
        return tier;
    }

    /** True for the special rings, which are destroyed instead of turning inert. */
    public boolean breaksWhenDepleted() {
        return breaksWhenDepleted;
    }

    /**
     * Level of one of the ring's INTRINSIC (built-in) enchantments, or 0 if it has none.
     * Intrinsic enchantments live outside the vanilla enchantments component, so they
     * cannot be removed; {@code EnchantmentHelperMixin} feeds them into the vanilla
     * lookup, using whichever of the intrinsic and the real level is higher.
     */
    public int getIntrinsicEnchantLevel(ItemInstance instance, Holder<Enchantment> enchantment) {
        ItemEnchantments intrinsic = instance.get(ModDataComponents.INTRINSIC_ENCHANTMENTS.get());
        return intrinsic == null ? 0 : intrinsic.getLevel(enchantment);
    }

    /** The ring glints when it carries intrinsic enchantments (they are not in the component). */
    @Override
    public boolean isFoil(ItemStack stack) {
        ItemEnchantments intrinsic = stack.get(ModDataComponents.INTRINSIC_ENCHANTMENTS.get());
        return (intrinsic != null && !intrinsic.isEmpty()) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        // The built-in (intrinsic) enchantments are not part of the vanilla enchantments
        // component, so list them explicitly, exactly like normal enchantment lines.
        ItemEnchantments intrinsic = stack.get(ModDataComponents.INTRINSIC_ENCHANTMENTS.get());
        if (intrinsic != null && !intrinsic.isEmpty()) {
            intrinsic.addToTooltip(context, tooltipComponents, tooltipFlag, stack);
        }

        // Effective levels: the higher of the intrinsic and the real enchantment level.
        // They are read first so their hint lines also show on the indestructible ring.
        int unbreaking = 0;
        int efficiency = 0;
        int stability = 0;
        int rocketBoost = 0;
        if (context.registries() != null) {
            unbreaking = EnchantmentHelper.getItemEnchantmentLevel(
                    context.registries().holderOrThrow(Enchantments.UNBREAKING), stack);
            efficiency = EnchantmentHelper.getItemEnchantmentLevel(
                    context.registries().holderOrThrow(Enchantments.EFFICIENCY), stack);
            stability = EnchantmentHelper.getItemEnchantmentLevel(
                    context.registries().holderOrThrow(ModEnchantments.FLIGHT_STABILITY), stack);
            rocketBoost = EnchantmentHelper.getItemEnchantmentLevel(
                    context.registries().holderOrThrow(ModEnchantments.ROCKET_BOOST), stack);
        }

        if (stack.has(ModDataComponents.INDESTRUCTIBLE.get())) {
            tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.remaining_infinite"));
        } else {
            int remainingPoints = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
            // Unbreaking: each level makes every durability point last one extra second.
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
        }

        if (breaksWhenDepleted && !stack.has(ModDataComponents.INDESTRUCTIBLE.get())) {
            tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.breaks_when_depleted").withStyle(ChatFormatting.RED));
        }

        // Gray hint lines (only for enchantments actually present).
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
