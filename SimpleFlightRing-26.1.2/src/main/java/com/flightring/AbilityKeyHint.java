package com.flightring;

import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Display name of the "trigger ring ability" key (V by default).
 * <p>
 * An ability's tooltip line has to name whatever key the player actually bound, so the
 * client puts the live key mapping in here when it registers the binding
 * ({@code ModKeyMappings}). This class is deliberately common code with a plain-text
 * fallback: the server never sees the client's key bindings, and asking a client-only
 * class for a name on the server would blow up.
 */
public final class AbilityKeyHint {

    private static Supplier<Component> keyName =
            () -> Component.translatable("key.simpleflightring.ability");

    /** Called from the client when the key mapping is registered. */
    public static void setKeyName(Supplier<Component> supplier) {
        keyName = supplier;
    }

    /** The bound key's display name, e.g. {@code V} or {@code 鼠标中键}. */
    public static Component keyName() {
        return keyName.get();
    }

    private AbilityKeyHint() {
    }
}
