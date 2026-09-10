package com.flightring;

import net.neoforged.fml.ModList;

/**
 * Optional AllTheModium integration.
 * <p>
 * Everything that belongs to the linked ring chain - the three rings, their smithing
 * recipes and the Arcane Amplification enchantment - is only registered while that mod is
 * present, so a setup without AllTheModium cannot run into missing-item or missing-tag
 * problems. The behaviour itself never touches AllTheModium classes: only item ids and
 * datapack conditions are used.
 */
public final class AllthemodiumCompat {

    /** Mod id of the integrated mod. */
    public static final String MOD_ID = "allthemodium";

    private AllthemodiumCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
