package com.flightring;

import java.util.List;
import java.util.Map;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Optional Cloth Config API integration: provides an in-game configuration
 * screen (mod list -> Config) for the HUD settings. When Cloth Config is not
 * installed the same options can still be edited in config/simpleflightring-client.toml.
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
                .setTitle(Component.translatable("cloth.simpleflightring.title"));
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("cloth.simpleflightring.category.hud"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        category.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("cloth.simpleflightring.show_timer"),
                        FlightRingConfig.SHOW_FLIGHT_TIMER.get())
                .setDefaultValue(true)
                .setSaveConsumer(value -> FlightRingConfig.SHOW_FLIGHT_TIMER.set(value))
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("cloth.simpleflightring.hide_in_chat"),
                        FlightRingConfig.HIDE_WHILE_CHAT_OPEN.get())
                .setDefaultValue(true)
                .setSaveConsumer(value -> FlightRingConfig.HIDE_WHILE_CHAT_OPEN.set(value))
                .build());

        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("cloth.simpleflightring.hud_x"),
                        FlightRingConfig.HUD_X.get(), 0, 4096)
                .setDefaultValue(4)
                .setSaveConsumer(value -> FlightRingConfig.HUD_X.set(value))
                .build());

        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("cloth.simpleflightring.hud_y"),
                        FlightRingConfig.HUD_Y.get(), 0, 4096)
                .setDefaultValue(4)
                .setSaveConsumer(value -> FlightRingConfig.HUD_Y.set(value))
                .build());

        builder.setSavingRunnable(() -> FlightRingConfig.SPEC.save());
        return builder.build();
    }

    /**
     * The one screen the mod registers. NeoForge uses a single {@code IConfigScreenFactory} per mod (registering
     * two made only the newer one reachable - the HUD screen disappeared, 2026-10-13), so this hub offers both:
     * the client HUD options and the broken dimension's COMMON options.
     */
    public static Screen createHubScreen(Screen parent) {
        return new Screen(Component.literal("简单飞行戒指 Simple Flight Ring")) {
            @Override
            protected void init() {
                int x = this.width / 2 - 120;
                int y = this.height / 2 - 40;
                addRenderableWidget(Button.builder(Component.literal("HUD 飞行计时器 / Flight timer"),
                                button -> this.minecraft.setScreen(createConfigScreen(this)))
                        .bounds(x, y, 240, 20).build());
                addRenderableWidget(Button.builder(Component.literal("破碎维度 生成设置 / Broken dimension"),
                                button -> this.minecraft.setScreen(createBrokenScreen(this)))
                        .bounds(x, y + 24, 240, 20).build());
                addRenderableWidget(Button.builder(CommonComponents.GUI_DONE,
                                button -> this.minecraft.setScreen(parent))
                        .bounds(x, y + 56, 240, 20).build());
            }
        };
    }

    /**
     * In-game screen for the broken dimension's COMMON options (islands / caves / structures / biome lists).
     * <p>
     * The entries come from {@link BrokenConfig#screenOptions()}, a descriptor list that lives in the COMMON
     * class and carries a label plus a getter and setter per option - Cloth needs one explicit entry each, and
     * {@code ModConfigSpec#getValues()} (NightConfig's {@code UnmodifiableConfig}) cannot be enumerated into
     * writable handles. Adding an option there makes it show up here without touching this class.
     * <p>
     * On a dedicated server the values shown are the <b>client's</b> copy of the file; the server's own file is
     * the one that counts.
     */
    public static Screen createBrokenScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("简单飞行戒指 · 破碎维度 / Broken Dimension"));
        for (Map.Entry<String, List<BrokenConfig.Option>> group : BrokenConfig.screenOptions().entrySet()) {
            ConfigCategory category = builder.getOrCreateCategory(Component.literal(group.getKey()));
            ConfigEntryBuilder entries = builder.entryBuilder();
            for (BrokenConfig.Option option : group.getValue()) {
                Object current = option.get().get();
                if (option.kind() == Boolean.class) {
                    category.addEntry(entries.startBooleanToggle(Component.literal(option.label()), (Boolean) current)
                            .setDefaultValue((Boolean) option.defaultValue())
                            .setSaveConsumer(value -> option.set().accept(value))
                            .build());
                } else if (option.kind() == Integer.class) {
                    category.addEntry(entries.startIntField(Component.literal(option.label()), (Integer) current)
                            .setDefaultValue((Integer) option.defaultValue())
                            .setSaveConsumer(value -> option.set().accept(value))
                            .build());
                } else if (option.kind() == Double.class) {
                    category.addEntry(entries.startDoubleField(Component.literal(option.label()), (Double) current)
                            .setDefaultValue((Double) option.defaultValue())
                            .setSaveConsumer(value -> option.set().accept(value))
                            .build());
                } else if (current instanceof List<?> list) {
                    List<String> text = list.stream().map(String::valueOf).toList();
                    category.addEntry(entries.startStrList(Component.literal(option.label()), text)
                            .setDefaultValue(text)
                            .setSaveConsumer(value -> option.set().accept(value))
                            .build());
                }
            }
        }
        builder.setSavingRunnable(BrokenConfig.SPEC::save);
        return builder.build();
    }
}
