package com.flightring;

import net.minecraft.Util;

/**
 * Client-side cache of the server-authoritative total flight time.
 * <p>
 * Updated by the network handler every 0.5 seconds while the player is in a world;
 * the HUD trusts the pushed value while it is fresh and falls back to a local
 * calculation otherwise.
 */
public final class ClientFlightTime {

    private static volatile int seconds;
    private static volatile long lastUpdateMillis;

    private ClientFlightTime() {
    }

    public static void update(int totalSeconds) {
        seconds = totalSeconds;
        lastUpdateMillis = Util.getMillis();
    }

    /** True while the pushed value is recent enough to trust (3 seconds). */
    public static boolean isFresh() {
        return Util.getMillis() - lastUpdateMillis < 3000;
    }

    public static int getSeconds() {
        return seconds;
    }
}
