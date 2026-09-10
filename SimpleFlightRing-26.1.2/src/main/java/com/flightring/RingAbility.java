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
    MAGIC_LINING("magic_lining", 0xFFFFC24A, 3);

    private final String key;
    private final int color;
    private final int descriptionLines;

    RingAbility(String key, int color, int descriptionLines) {
        this.key = key;
        this.color = color;
        this.descriptionLines = descriptionLines;
    }

    /** Suffix of this ability's translation keys ({@code tooltip.simpleflightring.<key>_title/_desc}). */
    public String key() {
        return key;
    }

    /**
     * Number of description lines this ability shows: the first uses the {@code _desc}
     * key and the following ones {@code _desc2}, {@code _desc3}, ... Every line is its
     * own component, because a {@code \n} inside a component is NOT turned into a line
     * break by the vanilla tooltip (1.21.1 renders it as a missing glyph box).
     */
    public int descriptionLines() {
        return descriptionLines;
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
