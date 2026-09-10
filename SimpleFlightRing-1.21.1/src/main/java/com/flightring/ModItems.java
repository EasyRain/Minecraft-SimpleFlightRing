package com.flightring;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

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
            ITEMS.registerItem("stable_flight_ring", properties -> new FlightRingItem(2400, 22, properties));

    /**
     * Powered Flight Ring: iron ring + gunpowder + redstone dust, with Rocket Boost I
     * and Efficiency I built in. 3000 durability (50 minutes); destroyed once fully
     * drained. Its built-in enchantments can be raised up to level 3 with the
     * shapeless gunpowder + redstone recipe.
     */
    public static final DeferredItem<FlightRingItem> POWERED_FLIGHT_RING =
            ITEMS.registerItem("powered_flight_ring", properties -> new FlightRingItem(3000, 22, properties));

    /** Indestructible Core: smithing ingredient that makes a ring never lose durability. */
    public static final DeferredItem<Item> INDESTRUCTIBLE_CORE =
            ITEMS.registerItem("indestructible_core", Item::new, new Item.Properties());

    /** All rings: the six tiered rings in upgrade order, then the two special rings. */
    public static final List<DeferredItem<FlightRingItem>> ALL = List.of(
            WOOD_FLIGHT_RING,
            STONE_FLIGHT_RING,
            IRON_FLIGHT_RING,
            GOLD_FLIGHT_RING,
            DIAMOND_FLIGHT_RING,
            NETHERITE_FLIGHT_RING,
            STABLE_FLIGHT_RING,
            POWERED_FLIGHT_RING
    );

    private ModItems() {
    }
}
