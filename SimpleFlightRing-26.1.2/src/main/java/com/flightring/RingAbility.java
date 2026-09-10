package com.flightring;

/**
 * Special abilities of the linked (AllTheModium chain) rings.
 * <p>
 * Upgrades inherit the previous tier's abilities: Vibranium also has whatever
 * Allthemodium grants, and Unobtainium has all of them (see {@code ModItems}).
 * The abilities themselves only work while the ring is worn in the Curios
 * "flight ring" slot.
 * <p>
 * Each ability keeps the colour of the ring it belongs to, so the tooltip shows the
 * whole inherited chain in its own tints (Allthemodium golden, Vibranium teal,
 * Unobtainium violet) - the same palettes the ring textures are drawn from.
 */
public enum RingAbility {

    // Still to come: KINETIC_DEFLECTION (Vibranium, teal 0xFF4BE3A8) and
    // BURST_TOTEM (Unobtainium, violet 0xFFC06BF5).

    /** Allthemodium: spends the ring's energy pool to absorb incoming damage. */
    MAGIC_LINING("magic_lining", 0xFFFFC24A);

    private final String key;
    private final int color;

    RingAbility(String key, int color) {
        this.key = key;
        this.color = color;
    }

    /** Suffix of this ability's translation keys ({@code tooltip.simpleflightring.<key>_title/_desc}). */
    public String key() {
        return key;
    }

    /**
     * RGB colour of the ring that owns this ability (Allthemodium base
     * {@code 0xFFFF8B04} to highlight {@code 0xFFFFFFBA}, kept in between so the
     * title stays readable on the dark tooltip background).
     */
    public int color() {
        return color;
    }
}
