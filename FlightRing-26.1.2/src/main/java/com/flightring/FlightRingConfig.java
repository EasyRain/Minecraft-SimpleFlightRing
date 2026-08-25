package com.flightring;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side configuration (config/simpleflightring-client.toml).
 * <p>
 * The HUD flight timer can be toggled here and its position adjusted; the same
 * options are exposed in-game through Cloth Config when it is installed.
 */
public class FlightRingConfig {

    public static final ModConfigSpec SPEC;

    /** Whether the flight time countdown is drawn on the HUD. */
    public static final ModConfigSpec.BooleanValue SHOW_FLIGHT_TIMER;

    /** Whether the countdown hides while the chat screen is open. */
    public static final ModConfigSpec.BooleanValue HIDE_WHILE_CHAT_OPEN;

    /** Horizontal HUD position: pixels from the left edge of the screen. */
    public static final ModConfigSpec.IntValue HUD_X;

    /** Vertical HUD position: pixels from the bottom edge of the screen (left-bottom corner by default). */
    public static final ModConfigSpec.IntValue HUD_Y;

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
        HUD_X = builder
                .comment("Horizontal position of the countdown: pixels from the left edge.",
                        "倒计时水平位置：距屏幕左边缘的像素。",
                        "Default: 4")
                .defineInRange("hudX", 4, 0, 4096);
        HUD_Y = builder
                .comment("Vertical position of the countdown: pixels from the bottom edge.",
                        "倒计时垂直位置：距屏幕底部边缘的像素（默认位于左下角）。",
                        "Default: 4")
                .defineInRange("hudY", 4, 0, 4096);
        builder.pop();
        SPEC = builder.build();
    }

    private FlightRingConfig() {
    }
}
