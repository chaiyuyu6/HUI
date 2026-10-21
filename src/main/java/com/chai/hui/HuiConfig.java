package com.chai.hui;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client configuration of HUI.
 *
 * <p>The values are written to {@code config/hui-client.toml} by NeoForge. They can be edited in game through the
 * HUI settings screen (key {@code K} by default) or from the Mods menu.
 */
public final class HuiConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Shows the HUI information panel on the HUD.",
                    "是否在 HUD 上显示 HUI 信息面板。")
            .define("hud.enabled", true);

    public static final ModConfigSpec.EnumValue<HudCorner> CORNER = BUILDER
            .comment("Screen corner the information panel is anchored to.",
                    "信息面板停靠的屏幕角落。")
            .defineEnum("hud.corner", HudCorner.TOP_LEFT);

    public static final ModConfigSpec.IntValue OFFSET_X = BUILDER
            .comment("Distance from the anchored screen edge on the X axis, in GUI pixels.",
                    "面板距离所停靠边缘的水平像素距离。")
            .defineInRange("hud.offsetX", 4, 0, 512);

    public static final ModConfigSpec.IntValue OFFSET_Y = BUILDER
            .comment("Distance from the anchored screen edge on the Y axis, in GUI pixels.",
                    "面板距离所停靠边缘的垂直像素距离。")
            .defineInRange("hud.offsetY", 4, 0, 512);

    public static final ModConfigSpec.IntValue BACKGROUND_OPACITY = BUILDER
            .comment("Opacity (0-255) of the panel background.",
                    "面板背景不透明度（0-255）。")
            .defineInRange("hud.backgroundOpacity", 168, 0, 255);

    public static final ModConfigSpec.ConfigValue<String> ACCENT_COLOR = BUILDER
            .comment("Accent color used for the panel border and title, as #RRGGBB.",
                    "面板边框与标题的强调色，格式 #RRGGBB。")
            .define("hud.accentColor", "#3FC7FF");

    public static final ModConfigSpec.BooleanValue SHOW_FPS = BUILDER
            .comment("Shows the current frame rate.", "显示帧率。")
            .define("hud.showFps", true);

    public static final ModConfigSpec.BooleanValue SHOW_COORDS = BUILDER
            .comment("Shows the player position.", "显示玩家坐标。")
            .define("hud.showCoords", true);

    public static final ModConfigSpec.BooleanValue SHOW_DIMENSION = BUILDER
            .comment("Shows the current dimension.", "显示当前维度。")
            .define("hud.showDimension", true);

    public static final ModConfigSpec.BooleanValue SHOW_BIOME = BUILDER
            .comment("Shows the biome the player is standing in.", "显示玩家所在生物群系。")
            .define("hud.showBiome", true);

    public static final ModConfigSpec.BooleanValue SHOW_TIME = BUILDER
            .comment("Shows the in-game day counter and clock.", "显示游戏内天数与时间。")
            .define("hud.showTime", true);

    /** The built config spec, registered by {@link HuiMod}. Must stay the last static field of this class. */
    public static final ModConfigSpec SPEC = BUILDER.build();

    private static final int DEFAULT_ACCENT = 0xFF3FC7FF;

    private HuiConfig() {}

    /** @return the accent color as ARGB, falling back to the default color if the config value is invalid. */
    public static int accentColor() {
        if (!SPEC.isLoaded()) {
            return DEFAULT_ACCENT;
        }
        try {
            String raw = ACCENT_COLOR.get().trim();
            if (raw.startsWith("#")) {
                raw = raw.substring(1);
            }
            if (raw.length() == 6) {
                raw = "FF" + raw;
            }
            return (int) Long.parseLong(raw, 16);
        } catch (RuntimeException exception) {
            return DEFAULT_ACCENT;
        }
    }

    /** @return the panel background color as ARGB with the configured opacity. */
    public static int backgroundColor() {
        if (!SPEC.isLoaded()) {
            return 0xA80B0E14;
        }
        return (BACKGROUND_OPACITY.get() & 0xFF) << 24 | 0x0B0E14;
    }
}
