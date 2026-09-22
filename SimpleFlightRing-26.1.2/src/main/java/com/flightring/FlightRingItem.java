package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
    /**
     * Attribute bonuses of a relic ring (see {@link RelicRing}), granted while it is worn in
     * the Curios slot; {@code null} for every other ring (the tiered, special and linked ones).
     * Left non-final so the five existing constructors stay untouched.
     */
    private RelicBonuses relicBonuses;
    /**
     * Special abilities of the linked rings; upgrades inherit the lower tiers'
     * abilities, so Unobtainium has everything. Empty for all other rings.
     */
    private final Set<RingAbility> abilities;
    /** Size of the energy pool the abilities spend; 0 for rings without a pool. */
    private final float maxEnergy;

    public FlightRingItem(RingTier tier, Properties properties) {
        super(properties
                .durability(tier.getMaxDurability())
                .enchantable(tier.getEnchantmentValue()));
        this.tier = tier;
        this.breaksWhenDepleted = false;
        this.intrinsicBase = Map.of();
        this.breakSound = null;
        this.bonuses = null;
        this.abilities = EnumSet.noneOf(RingAbility.class);
        this.maxEnergy = 0.0F;
    }

    /**
     * Special ring: explicit durability (1 point = 1 second of flight), built-in
     * (intrinsic) enchantments and the sound played when it is destroyed.
     */
    public FlightRingItem(int maxDurability, int enchantmentValue,
                          Map<ResourceKey<Enchantment>, Integer> intrinsicBase,
                          SoundEvent breakSound, Properties properties) {
        super(properties
                .durability(maxDurability)
                .enchantable(enchantmentValue));
        this.tier = null;
        this.breaksWhenDepleted = true;
        this.intrinsicBase = Map.copyOf(intrinsicBase);
        this.breakSound = breakSound;
        this.bonuses = null;
        this.abilities = EnumSet.noneOf(RingAbility.class);
        this.maxEnergy = 0.0F;
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
        this(maxDurability, enchantmentValue, null, Set.of(), 0.0F, properties);
    }

    /**
     * Linked ring with attribute bonuses: same as above, plus the armour / toughness /
     * attack damage / reach the ring grants while worn in the Curios slot.
     */
    public FlightRingItem(int maxDurability, int enchantmentValue, RingBonuses bonuses, Properties properties) {
        this(maxDurability, enchantmentValue, bonuses, Set.of(), 0.0F, properties);
    }

    /**
     * Linked ring with attribute bonuses and special abilities: the abilities spend the
     * ring's energy pool (see {@link RingEnergy}) and only work while the ring is worn
     * in the Curios "flight ring" slot.
     */
    public FlightRingItem(int maxDurability, int enchantmentValue, RingBonuses bonuses,
                          Set<RingAbility> abilities, float maxEnergy, Properties properties) {
        super(properties
                .durability(maxDurability)
                .enchantable(enchantmentValue));
        this.tier = null;
        this.breaksWhenDepleted = false;
        this.intrinsicBase = Map.of();
        this.breakSound = null;
        this.bonuses = bonuses;
        this.abilities = abilities.isEmpty()
                ? EnumSet.noneOf(RingAbility.class)
                : EnumSet.copyOf(abilities);
        this.maxEnergy = maxEnergy;
    }

    /**
     * Relic ring with built-in (intrinsic) enchantments and special abilities - the miner
     * ring comes with Unbreaking I, for example. Unlike the special rings it is NOT destroyed
     * once drained; it goes inert like every tiered ring.
     */
    public FlightRingItem(int maxDurability, int enchantmentValue,
                          Map<ResourceKey<Enchantment>, Integer> intrinsicBase,
                          Set<RingAbility> abilities, Properties properties) {
        super(properties
                .durability(maxDurability)
                .enchantable(enchantmentValue));
        this.tier = null;
        this.breaksWhenDepleted = false;
        this.intrinsicBase = Map.copyOf(intrinsicBase);
        this.breakSound = null;
        this.bonuses = null;
        this.abilities = abilities.isEmpty()
                ? EnumSet.noneOf(RingAbility.class)
                : EnumSet.copyOf(abilities);
        this.maxEnergy = 0.0F;
    }

    /**
     * Relic ring with its attribute bonuses (see {@link RelicBonuses}): armour, toughness,
     * damage, reach, health, luck and dodge, all granted only while the ring sits in the Curios
     * "flight ring" slot. Every relic ring goes through this constructor.
     */
    public FlightRingItem(int maxDurability, int enchantmentValue,
                          Map<ResourceKey<Enchantment>, Integer> intrinsicBase,
                          Set<RingAbility> abilities, RelicBonuses relicBonuses, Properties properties) {
        this(maxDurability, enchantmentValue, intrinsicBase, abilities, properties);
        this.relicBonuses = relicBonuses;
    }

    /** The ring's relic attribute bonuses, or {@code null} when it has none. */
    public RelicBonuses getRelicBonuses() {
        return relicBonuses;
    }

    /** Whether this ring has the given special ability (see {@link RingAbility}). */
    public boolean hasAbility(RingAbility ability) {
        return abilities.contains(ability);
    }

    /** The ring's special abilities, in enum order; empty for rings without any. */
    public Set<RingAbility> getAbilities() {
        return Collections.unmodifiableSet(abilities);
    }

    /**
     * True while the ring can still be used: a ring forged with the Indestructible Core has
     * infinite durability, every other ring simply goes inert once its durability is used
     * up (see {@link FlightHandler}). Special abilities check this as well, so a drained
     * ring grants neither flight nor its ability.
     */
    public boolean isUsable(ItemStack stack) {
        return stack.has(ModDataComponents.INDESTRUCTIBLE.get())
                || stack.getDamageValue() < stack.getMaxDamage();
    }

    /** Size of the ring's energy pool, or 0 when it has none (see {@link RingEnergy}). */
    public float getMaxEnergy() {
        return maxEnergy;
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
    public Map<ResourceKey<Enchantment>, Integer> getIntrinsicLevels(ItemInstance instance) {
        IntrinsicEnchants stored = instance.get(ModDataComponents.INTRINSIC_ENCHANTMENTS.get());
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
    public int getIntrinsicEnchantLevel(ItemInstance instance, Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey()
                .map(key -> {
                    IntrinsicEnchants stored = instance.get(ModDataComponents.INTRINSIC_ENCHANTMENTS.get());
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
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        boolean infinite = stack.has(ModDataComponents.INDESTRUCTIBLE.get());

        // 1. Remaining flight time ("Infinite" for a ring forged with the Indestructible Core).
        if (infinite) {
            tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.remaining_infinite"));
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
                tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.remaining_time_long", hours, minutes, seconds));
            } else {
                int minutes = remainingSeconds / 60;
                int seconds = remainingSeconds % 60;
                tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.remaining_time", minutes, seconds));
            }
        }

        // 2. Energy pool of the linked rings (spent by their abilities, e.g. Magic Lining).
        //    The maximum already includes the Energy Amplification enchantment.
        if (maxEnergy > 0.0F) {
            tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.energy",
                    Math.round(RingEnergy.get(stack)), Math.round(RingEnergy.max(stack))));
        }

        // 3. The special rings are destroyed once their durability runs out.
        if (breaksWhenDepleted && !infinite) {
            tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.breaks_when_depleted").withStyle(ChatFormatting.RED));
        }

        // 4. Built-in (intrinsic) enchantments - never part of the vanilla enchantments
        //    component, so they are listed explicitly with a "Built-in" prefix. The
        //    normal enchantments follow, added by the enchantments component itself.
        if (!intrinsicBase.isEmpty() && context.registries() != null) {
            getIntrinsicLevels(stack).forEach((key, level) -> tooltipComponents.accept(
                    Component.translatable("tooltip.simpleflightring.intrinsic_enchant",
                            Enchantment.getFullname(context.registries().holderOrThrow(key), level))));
        }
    }

    /**
     * Explanation lines for the special abilities and the enchantments the ring actually
     * has. Kept out of the tooltip proper and added by {@link RingTooltipHandler} only
     * while Shift is held.
     * <p>
     * Order inside that block is fixed: first every special ability (each title tinted
     * with the ring it belongs to, in chain order), then the enchantment effect hints.
     */
    public List<Component> effectHints(ItemStack stack, TooltipContext context) {
        List<Component> hints = new ArrayList<>(abilityHints(stack));
        hints.addAll(enchantmentHints(stack, context));
        return hints;
    }

    /**
     * The special abilities' blocks: title plus its description lines. Each description
     * line is its own component, because the vanilla tooltip does not break lines on
     * {@code \n}. The line named by {@link RingAbility#keyLine()} gets the ability key the
     * player actually bound, the one named by {@link RingAbility#levelLine()} the ring's
     * own strength (the emerald ring's raid level).
     */
    private List<Component> abilityHints(ItemStack stack) {
        List<Component> hints = new ArrayList<>();
        for (RingAbility ability : abilities) {
            hints.add(Component.translatable("tooltip.simpleflightring." + ability.key() + "_title")
                    .withColor(ability.color()));
            for (int line = 1; line <= ability.descriptionLines(); line++) {
                String suffix = line == 1 ? "_desc" : "_desc" + line;
                String key = "tooltip.simpleflightring." + ability.key() + suffix;
                MutableComponent text = line == ability.keyLine()
                        ? Component.translatable(key, AbilityKeyHint.keyName())
                        : line == ability.levelLine()
                        ? Component.translatable(key, HeroLevel.roman(HeroLevel.of(stack)))
                        : line == ability.chargeLine()
                        ? Component.translatable(key, Integer.toString(RaidCharges.of(stack)))
                        : Component.translatable(key);
                hints.add(text.withStyle(ChatFormatting.DARK_GRAY));
            }
            if (relicBonuses != null) {
                // The relic ring's attributes, one summary line (their own lang key per ring).
                hints.add(Component.translatable("tooltip.simpleflightring." + ability.key() + "_bonus")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return hints;
    }

    /** Effect hints of the enchantments the ring actually has (always after the abilities). */
    private List<Component> enchantmentHints(ItemStack stack, TooltipContext context) {
        List<Component> hints = new ArrayList<>();
        int amplification = RingEnergy.enchantLevel(stack, ModEnchantments.ARCANE_AMPLIFICATION);
        if (amplification > 0) {
            // Every level adds the ring's base pool once, so level n means (n + 1) times base.
            addHint(hints, "arcane_amplification", amplification + 1);
        }
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
        int burst = RingEnergy.enchantLevel(stack, ModEnchantments.ENERGY_BURST);
        if (burst > 0) {
            // 能量迸发: +25% special ability damage per level (and a wider blast).
            addHint(hints, "energy_burst", burst * 25);
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
