package com.flightring;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The eight "relic" flight rings. They cannot be crafted: the only way to get one is
 * to find its BROKEN form in structure loot (1% chance per matching chest) or - for the
 * emerald ring - to buy it from a master librarian, then repair it in the crafting table
 * with the ring's theme material (see the {@code repair_damaged_*} recipes).
 * <p>
 * Every relic ring is an ordinary ring otherwise: normal durability, consumable, and it
 * turns inert (the tiered rings' behaviour) instead of breaking. They can be enchanted
 * and worn in the Curios slot like any other ring.
 * <p>
 * The enum also keeps the two ids in one place so the item registration, the loot tables,
 * the repair recipes and the language files can never drift apart:
 * <ul>
 *   <li>{@code <id>_flight_ring} - the working ring ({@link #ringId()})</li>
 *   <li>{@code damaged_<id>_flight_ring} - the broken item found in loot ({@link #damagedId()})</li>
 * </ul>
 */
public enum RelicRing {

    /** Ancient City (deep dark). Balanced like the diamond ring: 7200 (120 minutes). */
    SCULK("sculk", 7200, 10, Items.ECHO_SHARD),
    /** Abandoned mineshaft chest minecarts. */
    MINER("miner", 5400, 14, Items.IRON_BLOCK),
    /** Master librarian trade (the only relic that is not found in loot). */
    EMERALD("emerald", 3600, 18, Items.EMERALD_BLOCK),
    /** Shipwreck chests. */
    OCEAN("ocean", 7200, 12, Items.HEART_OF_THE_SEA),
    /** Desert pyramid chests. */
    DESERT("desert", 6000, 16, Items.GOLD_BLOCK),
    /** Pillager outpost and woodland mansion chests. */
    RAID("raid", 8400, 14, Items.OMINOUS_BOTTLE),
    /** Nether fortress chests. */
    INFERNAL("infernal", 12000, 15, Items.BLAZE_ROD),
    /** End city chests. */
    ENDER("ender", 14400, 12, Items.SHULKER_SHELL);

    private final String id;
    private final int durability;
    private final int enchantmentValue;
    private final Item repairMaterial;

    RelicRing(String id, int durability, int enchantmentValue, Item repairMaterial) {
        this.id = id;
        this.durability = durability;
        this.enchantmentValue = enchantmentValue;
        this.repairMaterial = repairMaterial;
    }

    /** Base id, e.g. {@code sculk}. */
    public String id() {
        return id;
    }

    /** Item id of the working ring, e.g. {@code sculk_flight_ring}. */
    public String ringId() {
        return id + "_flight_ring";
    }

    /** Item id of the broken ring found in loot, e.g. {@code damaged_sculk_flight_ring}. */
    public String damagedId() {
        return "damaged_" + id + "_flight_ring";
    }

    /**
     * Durability of the working ring. One point is one second of flight, so this is
     * also the ring's flight time in seconds (120 minutes for the sculk ring, ...).
     */
    public int durability() {
        return durability;
    }

    /** Enchantability of the working ring (the six tiers sit between 5 and 22). */
    public int enchantmentValue() {
        return enchantmentValue;
    }

    /** The single item consumed together with the broken ring to repair it. */
    public Item repairMaterial() {
        return repairMaterial;
    }
}
