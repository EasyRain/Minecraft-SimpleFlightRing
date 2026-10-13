package com.flightring;

import java.util.function.Supplier;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only half of the config screen registration.
 * <p>
 * This exists because the reference must not live in {@link FlightRingMod}: a lambda such as
 * {@code (modContainer, screen) -> new ConfigurationScreen(modContainer, screen)} gets a synthetic method whose
 * <b>descriptor</b> mentions {@code net.minecraft.client.gui.screens.Screen}, and NeoForge's RuntimeDistCleaner
 * cannot strip a client type out of a method descriptor - it aborts with "Attempted to load class
 * net/minecraft/client/gui/screens/Screen for invalid dist DEDICATED_SERVER" and mod loading fails
 * (2026-10-13). Keeping the call in this class means {@link FlightRingMod} only ever refers to our own type
 * ({@code ConfigScreens::register}, descriptor {@code (ModContainer)void}), which is harmless, and this class is
 * simply never loaded on a dedicated server.
 * <p>
 * Only call this from inside a {@code Dist.CLIENT} check.
 */
public final class ConfigScreens {

    private ConfigScreens() {
    }

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (Supplier<IConfigScreenFactory>) () -> ConfigurationScreen::new);
    }
}
