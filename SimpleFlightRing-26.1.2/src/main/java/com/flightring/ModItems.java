package com.flightring;

import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FlightRingMod.MODID);

    public static final DeferredItem<FlightRingItem> WOOD_FLIGHT_RING =
            ITEMS.registerItem("wood_flight_ring", properties -> new FlightRingItem(RingTier.WOOD, properties));
    public static final DeferredItem<FlightRingItem> STONE_FLIGHT_RING =
            ITEMS.registerItem("stone_flight_ring", properties -> new FlightRingItem(RingTier.STONE, properties));
    public static final DeferredItem<FlightRingItem> IRON_FLIGHT_RING =
            ITEMS.registerItem("iron_flight_ring", properties -> new FlightRingItem(RingTier.IRON, properties));
    public static final DeferredItem<FlightRingItem> GOLD_FLIGHT_RING =
            ITEMS.registerItem("gold_flight_ring", properties -> new FlightRingItem(RingTier.GOLD, properties));
    public static final DeferredItem<FlightRingItem> DIAMOND_FLIGHT_RING =
            ITEMS.registerItem("diamond_flight_ring", properties -> new FlightRingItem(RingTier.DIAMOND, properties));
    public static final DeferredItem<FlightRingItem> NETHERITE_FLIGHT_RING =
            ITEMS.registerItem("netherite_flight_ring", properties -> new FlightRingItem(RingTier.NETHERITE, properties));

    /** Indestructible Core: smithing ingredient that makes a ring never lose durability. */
    public static final DeferredItem<Item> INDESTRUCTIBLE_CORE =
            ITEMS.registerItem("indestructible_core", Item::new, properties -> properties);

    /**
     * Stable Flight Ring: iron ring + 8 amethyst shards, with Flight Stability I built
     * in. 2400 durability (40 minutes of flight); destroyed once fully drained.
     */
    public static final DeferredItem<FlightRingItem> STABLE_FLIGHT_RING =
            ITEMS.registerItem("stable_flight_ring", properties -> new FlightRingItem(2400, 22,
                    Map.of(ModEnchantments.FLIGHT_STABILITY, 1), SoundEvents.GLASS_BREAK, properties));

    /**
     * Powered Flight Ring: iron ring + gunpowder + redstone dust, with Rocket Boost I
     * and Efficiency I built in. 3000 durability (50 minutes); destroyed once fully
     * drained. Its built-in enchantments can be raised up to level 3 with the
     * shapeless gunpowder + redstone recipe.
     */
    public static final DeferredItem<FlightRingItem> POWERED_FLIGHT_RING =
            ITEMS.registerItem("powered_flight_ring", properties -> new FlightRingItem(3000, 22,
                    Map.of(ModEnchantments.ROCKET_BOOST, 1, Enchantments.EFFICIENCY, 1),
                    SoundEvents.ITEM_BREAK.value(), properties));

    /**
     * AllTheModium integration chain. Like that mod's own gear these three rings are
     * indestructible: the INDESTRUCTIBLE component is their DEFAULT component, so every
     * instance - smithed, taken from the creative tab or spawned with {@code /give} -
     * has infinite flight time and never breaks. They are upgraded in the smithing table
     * with the matching AllTheModium upgrade template (plain vanilla smithing recipes, see
     * the data files); the netherite ring must be forged with the Indestructible Core first.
     * <p>
     * AllTheModium is an optional dependency: the whole chain is only registered when it
     * is present (otherwise these fields stay {@code null} and the rings are simply not in
     * the game), and the recipes/data files carry a {@code neoforge:mod_loaded} condition
     * as well. The durability is a placeholder - the INDESTRUCTIBLE component makes it
     * infinite.
     * <p>
     * The three abilities are inherited up the chain (Vibranium keeps Allthemodium's,
     * Unobtainium keeps both) and the energy pool grows with it: 100 for Allthemodium,
     * 300 from Vibranium on. All three abilities now exist: Magic Lining, Kinetic
     * Deflection and the Burst Totem.
     */
    private static final Set<RingAbility> ALLTHEMODIUM_ABILITIES = EnumSet.of(RingAbility.MAGIC_LINING);
    /** Vibranium inherits every ability of the tiers below it and adds its own. */
    private static final Set<RingAbility> VIBRANIUM_ABILITIES =
            EnumSet.of(RingAbility.MAGIC_LINING, RingAbility.KINETIC_DEFLECTION);
    /** Unobtainium inherits every ability of the tiers below it and adds its own. */
    private static final Set<RingAbility> UNOBTAINIUM_ABILITIES = EnumSet.of(
            RingAbility.MAGIC_LINING, RingAbility.KINETIC_DEFLECTION, RingAbility.BURST_TOTEM);

    /** Energy pool of each tier: it grows with the chain, 100 -> 300 -> 700. */
    private static final float ALLTHEMODIUM_ENERGY = RingEnergy.DEFAULT_MAX;
    private static final float VIBRANIUM_ENERGY = 300.0F;
    private static final float UNOBTAINIUM_ENERGY = 700.0F;

    /** {@code null} while AllTheModium is not installed. */
    public static final DeferredItem<FlightRingItem> ALLTHEMODIUM_FLIGHT_RING = registerLinkedRing(
            "allthemodium_flight_ring", new RingBonuses(5, 3, 0.04, 2), ALLTHEMODIUM_ABILITIES, ALLTHEMODIUM_ENERGY);
    /** {@code null} while AllTheModium is not installed. */
    public static final DeferredItem<FlightRingItem> VIBRANIUM_FLIGHT_RING = registerLinkedRing(
            "vibranium_flight_ring", new RingBonuses(10, 6, 0.08, 4), VIBRANIUM_ABILITIES, VIBRANIUM_ENERGY);
    /** {@code null} while AllTheModium is not installed. */
    public static final DeferredItem<FlightRingItem> UNOBTAINIUM_FLIGHT_RING = registerLinkedRing(
            "unobtainium_flight_ring", new RingBonuses(15, 9, 0.12, 6), UNOBTAINIUM_ABILITIES, UNOBTAINIUM_ENERGY);

    /** Registers one ring of the AllTheModium chain, or returns {@code null} without that mod. */
    private static DeferredItem<FlightRingItem> registerLinkedRing(String name, RingBonuses bonuses,
                                                                   Set<RingAbility> abilities, float energy) {
        if (!AllthemodiumCompat.isLoaded()) {
            return null;
        }
        return ITEMS.registerItem(name, properties -> new FlightRingItem(100, 22, bonuses, abilities, energy,
                properties.component(ModDataComponents.INDESTRUCTIBLE.get(), Unit.INSTANCE)));
    }

    /**
     * The eight relic rings (see {@link RelicRing}). They are never crafted: their broken
     * form is found in structure loot (1% per matching chest) or bought from a master
     * librarian, and is then repaired in the crafting table with the ring's theme material.
     */
    public static final Map<RelicRing, DeferredItem<FlightRingItem>> RELIC_RINGS = registerRelicRings();

    /** The broken counterpart of every relic ring - what the loot tables and the trade give out. */
    public static final Map<RelicRing, DeferredItem<DamagedRingItem>> DAMAGED_RELIC_RINGS = registerDamagedRelicRings();

    private static Map<RelicRing, DeferredItem<FlightRingItem>> registerRelicRings() {
        EnumMap<RelicRing, DeferredItem<FlightRingItem>> rings = new EnumMap<>(RelicRing.class);
        for (RelicRing relic : RelicRing.values()) {
            rings.put(relic, ITEMS.registerItem(relic.ringId(), properties -> new FlightRingItem(
                    relic.durability(), relic.enchantmentValue(), relicIntrinsicEnchantments(relic),
                    relicAbilities(relic), RelicBonuses.of(relic),
                    infernalProof(relic, relicDefaultComponents(relic, properties)))));
        }
        return Collections.unmodifiableMap(rings);
    }

    /**
     * The infernal ring and its broken form are forge work from the Nether, so they are fire proof
     * exactly like netherite: lava does not burn them up, it floats them back to its surface
     * (see {@link InfernalRingQuest}) - and for the broken ring it is the quench that finally
     * turns it into the working ring.
     */
    private static Item.Properties infernalProof(RelicRing relic, Item.Properties properties) {
        return relic == RelicRing.INFERNAL ? properties.fireResistant() : properties;
    }

    /**
     * Default components of a relic ring. Only the emerald ring has one: its strength
     * ({@code hero_level}) defaults to the maximum, so the ring in the creative menu and one
     * spawned with {@code /give} really are level V rings. A ring forged at the crafting table
     * overwrites it with the level of the damaged ring it was made from
     * (see {@link EmeraldForgeRecipe}), so the default only ever shows up on rings that were
     * never charged.
     */
    private static Item.Properties relicDefaultComponents(RelicRing relic, Item.Properties properties) {
        return relic == RelicRing.EMERALD
                ? properties.component(ModDataComponents.HERO_LEVEL.get(), HeroLevel.MAX)
                : properties;
    }

    /**
     * Special abilities of a relic ring: the sculk, miner, emerald, ocean, desert, raid, infernal
     * and ender rings each have one, the rest are plain rings.
     */
    private static Set<RingAbility> relicAbilities(RelicRing relic) {
        return switch (relic) {
            case SCULK -> EnumSet.of(RingAbility.SCULK_SOUL);
            case MINER -> EnumSet.of(RingAbility.MINER_VETERAN);
            case EMERALD -> EnumSet.of(RingAbility.EMERALD_HERO);
            case OCEAN -> EnumSet.of(RingAbility.OCEAN_FAVORED);
            case DESERT -> EnumSet.of(RingAbility.DESERT_GUIDE);
            case RAID -> EnumSet.of(RingAbility.RAID_PLUNDER);
            case INFERNAL -> EnumSet.of(RingAbility.FLAME_LORD);
            case ENDER -> EnumSet.of(RingAbility.WARP_NEXUS);
            default -> Set.of();
        };
    }

    /**
     * Built-in (intrinsic) enchantments of a relic ring, both are kept outside the vanilla
     * enchantments component so nothing can strip them: the miner ring is balanced against
     * the iron ring but ships with Unbreaking I (every point of its durability lasts two
     * seconds of flight), the emerald ring is balanced against the gold ring and ships with
     * Efficiency I (10% faster sprint flight), and the ender ring - which the task says comes
     * with it - ships with Rocket Boost I.
     */
    private static Map<ResourceKey<Enchantment>, Integer> relicIntrinsicEnchantments(RelicRing relic) {
        return switch (relic) {
            case MINER -> Map.of(Enchantments.UNBREAKING, 1);
            case EMERALD -> Map.of(Enchantments.EFFICIENCY, 1);
            case ENDER -> Map.of(ModEnchantments.ROCKET_BOOST, 1);
            default -> Map.of();
        };
    }

    private static Map<RelicRing, DeferredItem<DamagedRingItem>> registerDamagedRelicRings() {
        EnumMap<RelicRing, DeferredItem<DamagedRingItem>> rings = new EnumMap<>(RelicRing.class);
        for (RelicRing relic : RelicRing.values()) {
            rings.put(relic, ITEMS.registerItem(relic.damagedId(),
                    properties -> new DamagedRingItem(relic, infernalProof(relic, properties.stacksTo(1)))));
        }
        return Collections.unmodifiableMap(rings);
    }

    /** All rings: the six tiered rings, the two special rings, the relics, then the AllTheModium chain. */
    public static final List<DeferredItem<FlightRingItem>> ALL = collectRings();

    private static List<DeferredItem<FlightRingItem>> collectRings() {
        List<DeferredItem<FlightRingItem>> rings = new ArrayList<>(List.of(
                WOOD_FLIGHT_RING,
                STONE_FLIGHT_RING,
                IRON_FLIGHT_RING,
                GOLD_FLIGHT_RING,
                DIAMOND_FLIGHT_RING,
                NETHERITE_FLIGHT_RING,
                STABLE_FLIGHT_RING,
                POWERED_FLIGHT_RING));
        rings.addAll(RELIC_RINGS.values());
        if (AllthemodiumCompat.isLoaded()) {
            rings.add(ALLTHEMODIUM_FLIGHT_RING);
            rings.add(VIBRANIUM_FLIGHT_RING);
            rings.add(UNOBTAINIUM_FLIGHT_RING);
        }
        return List.copyOf(rings);
    }

    private ModItems() {
    }
}
