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

    /** All rings in upgrade order (wood -> stone -> iron -> gold -> diamond -> netherite). */
    public static final List<DeferredItem<FlightRingItem>> ALL = List.of(
            WOOD_FLIGHT_RING,
            STONE_FLIGHT_RING,
            IRON_FLIGHT_RING,
            GOLD_FLIGHT_RING,
            DIAMOND_FLIGHT_RING,
            NETHERITE_FLIGHT_RING
    );

    private ModItems() {
    }
}
