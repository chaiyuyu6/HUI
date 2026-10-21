package com.chai.hui.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.chai.hui.HuiConfig;
import com.chai.hui.HuiMod;
import com.chai.hui.HudCorner;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * The configurable HUI information panel.
 *
 * <p>It is registered as a NeoForge {@link GuiLayer} (see {@code HuiClient#registerGuiLayers}) and rendered with the
 * 26.3 render-state extraction API ({@link GuiGraphicsExtractor}) instead of the old immediate-mode {@code GuiGraphics}.
 */
public final class HuiHudLayer implements GuiLayer {
    /** Inner padding of the panel, in GUI pixels. */
    private static final int PADDING = 5;
    /** Height of a single text line, in GUI pixels. */
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_COLOR = 0xFFE6EDF3;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (!HuiConfig.SPEC.isLoaded() || !HuiConfig.ENABLED.get()) {
            return;
        }

        List<Component> lines = collectLines(minecraft, player);
        if (lines.isEmpty()) {
            return;
        }

        Font font = minecraft.font;
        int textWidth = 0;
        for (Component line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }

        int panelWidth = textWidth + PADDING * 2;
        int panelHeight = lines.size() * LINE_HEIGHT + PADDING * 2 - 1;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int offsetX = HuiConfig.OFFSET_X.get();
        int offsetY = HuiConfig.OFFSET_Y.get();

        int x;
        int y;
        HudCorner corner = HuiConfig.CORNER.get();
        switch (corner) {
            case TOP_LEFT -> {
                x = offsetX;
                y = offsetY;
            }
            case TOP_RIGHT -> {
                x = screenWidth - panelWidth - offsetX;
                y = offsetY;
            }
            case BOTTOM_LEFT -> {
                x = offsetX;
                y = screenHeight - panelHeight - offsetY;
            }
            case BOTTOM_RIGHT -> {
                x = screenWidth - panelWidth - offsetX;
                y = screenHeight - panelHeight - offsetY;
            }
            default -> throw new IllegalStateException("Unknown corner " + corner);
        }

        int accent = HuiConfig.accentColor();
        graphics.fill(x, y, x + panelWidth, y + panelHeight, HuiConfig.backgroundColor());
        graphics.outline(x, y, panelWidth, panelHeight, accent);

        int textY = y + PADDING;
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), x + PADDING, textY, i == 0 ? accent : TEXT_COLOR, true);
            textY += LINE_HEIGHT;
        }
    }

    /** Builds the visible lines of the panel according to the current configuration. */
    private static List<Component> collectLines(Minecraft minecraft, LocalPlayer player) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("HUI"));

        if (HuiConfig.SHOW_FPS.get()) {
            lines.add(Component.translatable("hui.hud.fps", minecraft.getFps()));
        }
        if (HuiConfig.SHOW_COORDS.get()) {
            lines.add(Component.translatable("hui.hud.xyz",
                    String.format(Locale.ROOT, "%.1f", player.getX()),
                    String.format(Locale.ROOT, "%.1f", player.getY()),
                    String.format(Locale.ROOT, "%.1f", player.getZ())));
        }
        if (HuiConfig.SHOW_DIMENSION.get() && minecraft.level != null) {
            lines.add(Component.translatable("hui.hud.dimension",
                    minecraft.level.dimension().identifier().getPath()));
        }
        if (HuiConfig.SHOW_BIOME.get() && minecraft.level != null) {
            String biome = minecraft.level.getBiome(player.blockPosition())
                    .unwrapKey()
                    .map(key -> key.identifier().getPath())
                    .orElse("unknown");
            lines.add(Component.translatable("hui.hud.biome", biome));
        }
        if (HuiConfig.SHOW_TIME.get() && minecraft.level != null) {
            long gameTime = minecraft.level.getGameTime();
            long day = gameTime / 24000L + 1L;
            long timeOfDay = ((gameTime % 24000L) + 24000L) % 24000L;
            // Minecraft's day starts at 06:00 and one in-game hour is 1000 ticks.
            int hours = (int) ((timeOfDay / 1000L + 6L) % 24L);
            int minutes = (int) ((timeOfDay % 1000L) * 60L / 1000L);
            lines.add(Component.translatable("hui.hud.time", day, String.format(Locale.ROOT, "%02d:%02d", hours, minutes)));
        }

        if (lines.size() == 1) {
            // Only the header is visible: nothing useful to show.
            HuiMod.LOGGER.debug("HUI panel has no enabled lines");
            return List.of();
        }
        return lines;
    }
}
