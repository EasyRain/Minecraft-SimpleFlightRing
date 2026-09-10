package com.flightring;

/**
 * Special abilities of the linked (AllTheModium chain) rings.
 * <p>
 * Upgrades inherit the previous tier's abilities: Vibranium also has whatever
 * Allthemodium grants, and Unobtainium has all of them (see {@code ModItems}).
 * The abilities themselves only work while the ring is worn in the Curios
 * "flight ring" slot.
 */
public enum RingAbility {

    /** Allthemodium: spends the ring's energy pool to absorb incoming damage. */
    MAGIC_LINING("magic_lining");

    private final String key;

    RingAbility(String key) {
        this.key = key;
    }

    /** Suffix of this ability's translation keys ({@code tooltip.simpleflightring.<key>_title/_desc}). */
    public String key() {
        return key;
    }
}
