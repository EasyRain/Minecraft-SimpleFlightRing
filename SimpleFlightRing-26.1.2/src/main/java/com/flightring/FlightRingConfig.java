package com.flightring;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side configuration (config/simpleflightring-client.toml).
 * <p>
 * The HUD flight timer can be toggled here and its position adjusted; the same
 * options are exposed in-game from the mod list's Config screen (NeoForge's own).
 */
public class FlightRingConfig {

    public static final ModConfigSpec SPEC;

    /** Whether the flight time countdown is drawn on the HUD. */
    public static final ModConfigSpec.BooleanValue SHOW_FLIGHT_TIMER;

    /** Whether the countdown hides while the chat screen is open. */
    public static final ModConfigSpec.BooleanValue HIDE_WHILE_CHAT_OPEN;

    /**
     * Horizontal HUD position: percentage of the screen width, measured from the left edge.
     * <p>
     * A percentage instead of pixels, so the countdown and the energy bar keep their place when the player
     * changes the resolution or the GUI scale (pixel values only looked right at the resolution they were
     * tuned on).
     */
    public static final ModConfigSpec.DoubleValue HUD_X_PERCENT;

    /** Vertical HUD position: percentage of the screen height, measured from the bottom edge. */
    public static final ModConfigSpec.DoubleValue HUD_Y_PERCENT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Flight timer HUD settings / 飞行时间倒计时 HUD 设置")
                .push("hud");
        SHOW_FLIGHT_TIMER = builder
                .comment("Show the remaining flight time countdown on the HUD.",
                        "在游戏界面显示剩余飞行时间倒计时。",
                        "Default: true")
                .define("showFlightTimer", true);
        HIDE_WHILE_CHAT_OPEN = builder
                .comment("Hide the countdown while the chat input is open (default position overlaps the chat box).",
                        "打开聊天框输入文字时隐藏倒计时（默认位置与聊天框重叠，建议保持开启）。",
                        "Default: true")
                .define("hideWhileChatOpen", true);
        HUD_X_PERCENT = builder
                .comment("Horizontal position of the countdown and the energy bar: percentage of the screen width, from the left edge.",
                        "倒计时与能量条的水平位置：屏幕宽度的百分比，从左边缘算起。",
                        "Default: 1.0 (= 1% of the width, about the old 4 pixels)")
                .defineInRange("hudXPercent", 1.0, 0.0, 100.0);
        HUD_Y_PERCENT = builder
                .comment("Vertical position of the countdown and the energy bar: percentage of the screen height, from the bottom edge.",
                        "倒计时与能量条的垂直位置：屏幕高度的百分比，从底部边缘算起。",
                        "Default: 2.0 (= 2% of the height, about the old 4 pixels)")
                .defineInRange("hudYPercent", 2.0, 0.0, 100.0);
        builder.pop();
        SPEC = builder.build();
    }

    private FlightRingConfig() {
    }
}
