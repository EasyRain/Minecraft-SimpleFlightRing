package com.flightring;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
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
                    SoundEvents.ITEM_BREAK, properties));

    /** Indestructible Core: smithing ingredient that makes a ring never lose durability. */
    public static final DeferredItem<Item> INDESTRUCTIBLE_CORE =
            ITEMS.registerItem("indestructible_core", Item::new, new Item.Properties());

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

    /** All rings: the six tiered rings, the two special rings, then the AllTheModium chain when present. */
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
