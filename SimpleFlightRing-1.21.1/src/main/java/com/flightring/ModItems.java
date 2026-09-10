package com.flightring;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

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
     * AllTheModium is an optional dependency: the recipes only load when it is present.
     * The durability is a placeholder - the INDESTRUCTIBLE component makes it infinite.
     */
    public static final DeferredItem<FlightRingItem> ALLTHEMODIUM_FLIGHT_RING =
            ITEMS.registerItem("allthemodium_flight_ring", properties -> new FlightRingItem(100, 22,
                    properties.component(ModDataComponents.INDESTRUCTIBLE.get(), Unit.INSTANCE)));
    public static final DeferredItem<FlightRingItem> VIBRANIUM_FLIGHT_RING =
            ITEMS.registerItem("vibranium_flight_ring", properties -> new FlightRingItem(100, 22,
                    properties.component(ModDataComponents.INDESTRUCTIBLE.get(), Unit.INSTANCE)));
    public static final DeferredItem<FlightRingItem> UNOBTAINIUM_FLIGHT_RING =
            ITEMS.registerItem("unobtainium_flight_ring", properties -> new FlightRingItem(100, 22,
                    properties.component(ModDataComponents.INDESTRUCTIBLE.get(), Unit.INSTANCE)));

    /** All rings: the six tiered rings, the two special rings, then the AllTheModium chain. */
    public static final List<DeferredItem<FlightRingItem>> ALL = List.of(
            WOOD_FLIGHT_RING,
            STONE_FLIGHT_RING,
            IRON_FLIGHT_RING,
            GOLD_FLIGHT_RING,
            DIAMOND_FLIGHT_RING,
            NETHERITE_FLIGHT_RING,
            STABLE_FLIGHT_RING,
            POWERED_FLIGHT_RING,
            ALLTHEMODIUM_FLIGHT_RING,
            VIBRANIUM_FLIGHT_RING,
            UNOBTAINIUM_FLIGHT_RING
    );

    private ModItems() {
    }
}
