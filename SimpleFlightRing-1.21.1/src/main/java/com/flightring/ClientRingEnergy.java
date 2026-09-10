package com.flightring;

import net.minecraft.Util;

/**
 * Client-side cache of the server-authoritative energy pool of the ring the player
 * currently wears in the Curios slot.
 * <p>
 * Pushed by {@link MagicLiningHandler} twice a second; the HUD trusts the value while
 * it is fresh. {@code maxEnergy <= 0} (or {@code energy < 0}) means no ability ring is
 * worn, so no bar is drawn.
 */
public final class ClientRingEnergy {

    private static volatile float energy;
    private static volatile float maxEnergy;
    private static volatile long lastUpdateMillis;

    private ClientRingEnergy() {
    }

    public static void update(float newEnergy, float newMaxEnergy) {
        energy = newEnergy;
        maxEnergy = newMaxEnergy;
        lastUpdateMillis = Util.getMillis();
    }

    /** True while the pushed value is recent enough to trust (3 seconds). */
    public static boolean isFresh() {
        return Util.getMillis() - lastUpdateMillis < 3000;
    }

    public static boolean isActive() {
        return isFresh() && maxEnergy > 0.0F && energy >= 0.0F;
    }

    public static float energy() {
        return energy;
    }

    public static float maxEnergy() {
        return maxEnergy;
    }
}
