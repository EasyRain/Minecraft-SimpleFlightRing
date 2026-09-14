package com.flightring;

import net.minecraft.world.item.ItemStack;

/**
 * Hero of the Village strength carried by an emerald ring.
 * <p>
 * The value is the level of the raid the ring was carried through - the same 1..5 an ominous
 * bottle (bad omen) gives - so a ring charged in a level III raid grants Hero of the Village
 * III. It is stored in the {@code hero_level} component on both the awakened damaged ring and
 * the finished ring (see {@link EmeraldRingQuest} and {@link ModDataComponents#HERO_LEVEL}),
 * and a ring that carries no level at all - a ring from an older world or from the creative
 * menu - simply counts as level I.
 */
public final class HeroLevel {

    /** Levels an ominous bottle (and therefore a raid) can have. */
    public static final int MIN = 1;
    public static final int MAX = 5;

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    /** Clamps any level into the range a raid can actually have. */
    public static int clamp(int level) {
        return Math.max(MIN, Math.min(MAX, level));
    }

    /** The level stored on the stack, or {@link #MIN} when it carries none. */
    public static int of(ItemStack stack) {
        Integer level = stack.get(ModDataComponents.HERO_LEVEL.get());
        return level == null ? MIN : clamp(level);
    }

    /** Writes the level (clamped) onto the stack. */
    public static void set(ItemStack stack, int level) {
        stack.set(ModDataComponents.HERO_LEVEL.get(), clamp(level));
    }

    /**
     * Roman numeral for the tooltip, e.g. {@code 3 -> "III"}. Both the damaged ring's
     * "the ring remembers the light of the old days (III)" line and the finished ring's
     * "you carry on the will of the ancient hero (III)" line use it.
     */
    public static String roman(int level) {
        return ROMAN[clamp(level) - 1];
    }

    private HeroLevel() {
    }
}
