package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A flight ring item. Grants traditional creative flight while it has durability
 * remaining (see {@link FlightHandler}); durability only drops while actually flying.
 * Rings are enchantable with Unbreaking, Mending and Efficiency (the rings are added
 * to the vanilla {@code enchantable/durability} and {@code enchantable/mining} item
 * tags); Efficiency levels speed up sprint-flight by 10% each.
 * <p>
 * The two special rings (Stable / Powered) are built with an explicit durability,
 * INTRINSIC (built-in) enchantments and are DESTROYED once fully consumed, unlike the
 * tiered rings which merely become inert.
 * <p>
 * The intrinsic enchantments' base levels live on the item itself, so every ring -
 * crafted, taken from the creative tab or spawned with {@code /give} - has them. They
 * are never written to the vanilla enchantments component, so nothing can strip them
 * (grindstone, ...). See {@code EnchantmentHelperMixin} for the effective level.
 * <p>
 * Tooltip layout: name, remaining time, destroy warning, built-in enchantments, then
 * the normal (component) enchantments. The long enchantment EFFECT hints are kept out
 * of the tooltip proper and added behind Shift by {@link RingTooltipHandler}.
 */
public class FlightRingItem extends Item {

    /** Tier of the six crafting rings; {@code null} for the special rings. */
    private final RingTier tier;
    private final int enchantmentValue;
    /** Special rings are destroyed when drained; tiered rings just become inert. */
    private final boolean breaksWhenDepleted;
    /** Built-in (intrinsic) enchantments and their base levels; empty for the tiered rings. */
    private final Map<ResourceKey<Enchantment>, Integer> intrinsicBase;
    /** Sound played when a special ring is destroyed; {@code null} for the tiered rings. */
    private final SoundEvent breakSound;
    /**
     * Attribute bonuses granted while the ring is worn in the Curios slot;
     * {@code null} for every ring except the linked (AllTheModium chain) ones.
     */
    private final RingBonuses bonuses;

    public FlightRingItem(RingTier tier, Properties properties) {
        super(properties.durability(tier.getMaxDurability()));
        this.tier = tier;
        this.enchantmentValue = tier.getEnchantmentValue();
        this.breaksWhenDepleted = false;
        this.intrinsicBase = Map.of();
        this.breakSound = null;
        this.bonuses = null;
    }

    /**
     * Special ring: explicit durability (1 point = 1 second of flight), built-in
     * (intrinsic) enchantments and the sound played when it is destroyed.
     */
    public FlightRingItem(int maxDurability, int enchantmentValue,
                          Map<ResourceKey<Enchantment>, Integer> intrinsicBase,
                          SoundEvent breakSound, Properties properties) {
        super(properties.durability(maxDurability));
        this.tier = null;
        this.enchantmentValue = enchantmentValue;
        this.breaksWhenDepleted = true;
        this.intrinsicBase = Map.copyOf(intrinsicBase);
        this.breakSound = breakSound;
        this.bonuses = null;
    }

    public RingTier getTier() {
        return tier;
    }

    /**
     * Linked ring (the AllTheModium chain): explicit durability and enchantability, no
     * built-in enchantments. Those rings are indestructible, which is provided by the
     * INDESTRUCTIBLE component set as their default component (see {@code ModItems}).
     */
    public FlightRingItem(int maxDurability, int enchantmentValue, Properties properties) {
        this(maxDurability, enchantmentValue, null, properties);
    }

    /**
     * Linked ring with attribute bonuses: same as above, plus the armour / toughness /
     * attack damage / reach the ring grants while worn in the Curios slot.
     */
    public FlightRingItem(int maxDurability, int enchantmentValue, RingBonuses bonuses, Properties properties) {
        super(properties.durability(maxDurability));
        this.tier = null;
        this.enchantmentValue = enchantmentValue;
        this.breaksWhenDepleted = false;
        this.intrinsicBase = Map.of();
        this.breakSound = null;
        this.bonuses = bonuses;
    }

    /**
     * Attribute bonuses granted while the ring is worn in the Curios slot, or
     * {@code null} when the ring grants none. Curios reads this through
     * {@code CuriosCompat}; a ring in the inventory grants nothing.
     */
    public RingBonuses getBonuses() {
        return bonuses;
    }

    /** True for the special rings, which are destroyed instead of turning inert. */
    public boolean breaksWhenDepleted() {
        return breaksWhenDepleted;
    }

    /** Sound played when the ring is destroyed; {@code null} for the tiered rings. */
    public SoundEvent getBreakSound() {
        return breakSound;
    }

    /**
     * Effective levels of the ring's built-in enchantments: the item's base levels plus
     * any levels raised above them and stored on this stack (Powered ring upgrades).
     */
    public Map<ResourceKey<Enchantment>, Integer> getIntrinsicLevels(ItemStack stack) {
        IntrinsicEnchants stored = stack.get(ModDataComponents.INTRINSIC_ENCHANTMENTS.get());
        if (intrinsicBase.isEmpty()) {
            return stored == null ? Map.of() : stored.levels();
        }
        Map<ResourceKey<Enchantment>, Integer> merged = new LinkedHashMap<>(intrinsicBase);
        if (stored != null) {
            stored.levels().forEach((key, level) -> merged.merge(key, level, Math::max));
        }
        return merged;
    }

    /**
     * Level of one of the ring's INTRINSIC (built-in) enchantments, or 0 if it has none.
     * {@code EnchantmentHelperMixin} feeds this into the vanilla enchantment lookup,
     * using whichever of the intrinsic and the real level is higher.
     */
    public int getIntrinsicEnchantLevel(ItemStack stack, Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey()
                .map(key -> {
                    IntrinsicEnchants stored = stack.get(ModDataComponents.INTRINSIC_ENCHANTMENTS.get());
                    int base = intrinsicBase.getOrDefault(key, 0);
                    return Math.max(base, stored == null ? 0 : stored.level(key));
                })
                .orElse(0);
    }

    /** The ring glints while it carries intrinsic enchantments (they are not in the component). */
    @Override
    public boolean isFoil(ItemStack stack) {
        if (!intrinsicBase.isEmpty()) {
            return true;
        }
        IntrinsicEnchants stored = stack.get(ModDataComponents.INTRINSIC_ENCHANTMENTS.get());
        return (stored != null && !stored.isEmpty()) || super.isFoil(stack);
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        boolean infinite = stack.has(ModDataComponents.INDESTRUCTIBLE.get());

        // 1. Remaining flight time ("Infinite" for a ring forged with the Indestructible Core).
        if (infinite) {
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.remaining_infinite"));
        } else {
            int unbreaking = context.registries() == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(
                    context.registries().holderOrThrow(Enchantments.UNBREAKING), stack);
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

        // 2. The special rings are destroyed once their durability runs out.
        if (breaksWhenDepleted && !infinite) {
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.breaks_when_depleted").withStyle(ChatFormatting.RED));
        }

        // 3. Built-in (intrinsic) enchantments - never part of the vanilla enchantments
        //    component, so they are listed explicitly with a "Built-in" prefix. The
        //    normal enchantments follow, added by the enchantments component itself.
        if (!intrinsicBase.isEmpty() && context.registries() != null) {
            getIntrinsicLevels(stack).forEach((key, level) -> tooltipComponents.add(
                    Component.translatable("tooltip.simpleflightring.intrinsic_enchant",
                            Enchantment.getFullname(context.registries().holderOrThrow(key), level))));
        }
    }

    /**
     * Explanation lines for the enchantments the ring actually has. Kept out of the
     * tooltip proper and added by {@link RingTooltipHandler} only while Shift is held.
     */
    public List<Component> effectHints(ItemStack stack, TooltipContext context) {
        List<Component> hints = new ArrayList<>();
        if (context.registries() == null) {
            return hints;
        }
        int unbreaking = EnchantmentHelper.getItemEnchantmentLevel(context.registries().holderOrThrow(Enchantments.UNBREAKING), stack);
        if (unbreaking > 0) {
            // Each durability point lasts (level + 1) seconds.
            addHint(hints, "unbreaking", unbreaking + 1);
        }
        int efficiency = EnchantmentHelper.getItemEnchantmentLevel(context.registries().holderOrThrow(Enchantments.EFFICIENCY), stack);
        if (efficiency > 0) {
            // +10% sprint-flight speed per level.
            addHint(hints, "efficiency", efficiency * 10);
        }
        if (EnchantmentHelper.getItemEnchantmentLevel(context.registries().holderOrThrow(ModEnchantments.FLIGHT_STABILITY), stack) > 0) {
            addHint(hints, "stability");
        }
        if (EnchantmentHelper.getItemEnchantmentLevel(context.registries().holderOrThrow(ModEnchantments.ROCKET_BOOST), stack) > 0) {
            addHint(hints, "rocket_boost");
        }
        return hints;
    }

    /**
     * Adds one hint block: the enchantment's name on its own line, then what it does.
     * {@code args} fill the {@code %s} placeholders of the description (e.g. the
     * seconds one durability point lasts). Future cross-mod ring effects use the same shape.
     */
    private static void addHint(List<Component> hints, String name, Object... args) {
        hints.add(Component.translatable("tooltip.simpleflightring." + name + "_title").withStyle(ChatFormatting.GRAY));
        hints.add(Component.translatable("tooltip.simpleflightring." + name + "_desc", args).withStyle(ChatFormatting.DARK_GRAY));
    }
}
