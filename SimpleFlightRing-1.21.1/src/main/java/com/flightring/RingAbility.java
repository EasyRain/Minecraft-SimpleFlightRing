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

    /** Allthemodium: spends the ring's energy pool to absorb incoming damage. */
    MAGIC_LINING("magic_lining", 0xFFFFC24A, 3),

    /** Vibranium: bounces ranged attacks away while more than half of the pool is left. */
    KINETIC_DEFLECTION("kinetic_deflection", 0xFF4BE3A8, 3),

    /** Unobtainium: survives a fatal hit with a block-safe explosion around the wearer. */
    BURST_TOTEM("burst_totem", 0xFFC06BF5, 3);

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
     * RGB colour of the ring that owns this ability, taken from that ring's metal palette
     * (Allthemodium {@code 0xFFFF8B04}→{@code 0xFFFFFFBA}, Vibranium {@code 0x1BB38A}→
     * {@code 0x73FFB9}, Unobtainium {@code 0xA82CE3}→{@code 0xEA84F5}), kept in the
     * readable middle so the title stands out on the dark tooltip background.
     */
    public int color() {
        return color;
    }
}
