package com.flightring;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

/**
 * Optional Cloth Config API integration: provides an in-game configuration
 * screen (mod list -> Config) for the HUD settings. When Cloth Config is not
 * installed the same options can still be edited in config/flightring-client.toml.
 * <p>
 * This class must only be touched when Cloth Config is actually loaded
 * (see {@link #isLoaded()}).
 */
public final class ClothConfigCompat {

    private ClothConfigCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("cloth_config");
    }

    public static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("cloth.flightring.title"));
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("cloth.flightring.category.hud"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        category.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("cloth.flightring.show_timer"),
                        FlightRingConfig.SHOW_FLIGHT_TIMER.get())
                .setDefaultValue(true)
                .setSaveConsumer(value -> FlightRingConfig.SHOW_FLIGHT_TIMER.set(value))
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("cloth.flightring.hide_in_chat"),
                        FlightRingConfig.HIDE_WHILE_CHAT_OPEN.get())
                .setDefaultValue(true)
                .setSaveConsumer(value -> FlightRingConfig.HIDE_WHILE_CHAT_OPEN.set(value))
                .build());

        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("cloth.flightring.hud_x"),
                        FlightRingConfig.HUD_X.get(), 0, 4096)
                .setDefaultValue(4)
                .setSaveConsumer(value -> FlightRingConfig.HUD_X.set(value))
                .build());

        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("cloth.flightring.hud_y"),
                        FlightRingConfig.HUD_Y.get(), 0, 4096)
                .setDefaultValue(4)
                .setSaveConsumer(value -> FlightRingConfig.HUD_Y.set(value))
                .build());

        builder.setSavingRunnable(() -> FlightRingConfig.SPEC.save());
        return builder.build();
    }
}
