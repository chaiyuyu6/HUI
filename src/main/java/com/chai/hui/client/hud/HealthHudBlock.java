package com.chai.hui.client.hud;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * The vanilla health (and absorption) hearts.
 *
 * <p>Unlike the other blocks, vanilla draws the hearts relative to {@code Hud#leftHeight}, a value that is reset to
 * {@code 39} at the start of every frame and then increased by the health/armour/food layers to stack them upwards.
 * Because HUI renders the health block itself (possibly last, for the "bring to front" feature), the value of
 * {@code leftHeight} at draw time is usually larger than it was for vanilla, so the translation is compensated to keep
 * the block at its documented default position (bottom centre, {@code y = height - 39}).
 */
public final class HealthHudBlock extends SimpleHudBlock {
    /** {@code leftHeight} value vanilla uses when the health layer is drawn. */
    private static final int VANILLA_LEFT_HEIGHT = 39;
    /** Vanilla draws ten hearts starting at {@code screenWidth / 2 - 91}. */
    private static final int VANILLA_LEFT_MARGIN = 91;
    /** Width of ten vanilla heart sprites. */
    private static final int WIDTH = 81;
    /** Height of a single heart row. */
    private static final int HEIGHT = 9;

    public HealthHudBlock(Identifier id, String labelKey, List<GuiLayer> layers) {
        super(id, labelKey, layers, HealthHudBlock::defaultBounds);
    }

    private static HudRect defaultBounds(int screenWidth, int screenHeight) {
        return new HudRect(screenWidth / 2 - VANILLA_LEFT_MARGIN, screenHeight - VANILLA_LEFT_HEIGHT, WIDTH, HEIGHT);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, int offsetX, int offsetY) {
        int naturalY = graphics.guiHeight() - Minecraft.getInstance().gui.hud.leftHeight;
        int targetY = defaultBounds(graphics.guiWidth(), graphics.guiHeight()).y() + offsetY;
        super.render(graphics, deltaTracker, offsetX, targetY - naturalY);
    }
}
