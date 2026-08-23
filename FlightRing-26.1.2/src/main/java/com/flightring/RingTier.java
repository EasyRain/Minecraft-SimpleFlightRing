package com.flightring;

/**
 * The six flight ring tiers, in crafting upgrade order.
 * <p>
 * {@code flightMinutes} is the total flight time of the tier. Since the ring consumes
 * exactly 1 durability per second of flight, the item's max durability is
 * {@code flightMinutes * 60} (Unbreaking enchantment extends each point by
 * {@code level + 1} seconds, see {@link FlightHandler}).
 */
public enum RingTier {

    WOOD("wood", 5, 10),
    STONE("stone", 15, 5),
    IRON("iron", 30, 14),
    GOLD("gold", 60, 22),
    DIAMOND("diamond", 120, 10),
    NETHERITE("netherite", 240, 15);

    private final String name;
    private final int flightMinutes;
    private final int enchantmentValue;

    RingTier(String name, int flightMinutes, int enchantmentValue) {
        this.name = name;
        this.flightMinutes = flightMinutes;
        this.enchantmentValue = enchantmentValue;
    }

    public String getName() {
        return name;
    }

    /** Total flight time in minutes. */
    public int getFlightMinutes() {
        return flightMinutes;
    }

    /** Max durability: 1 durability point per second of flight (60 per minute). */
    public int getMaxDurability() {
        return flightMinutes * 60;
    }

    /** Enchantability used by the vanilla enchanting table. */
    public int getEnchantmentValue() {
        return enchantmentValue;
    }
}
