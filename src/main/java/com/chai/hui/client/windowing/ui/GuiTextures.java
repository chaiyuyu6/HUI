package com.chai.hui.client.windowing.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * HUI GUI textures. Everything is optional: when the texture files are missing HUI falls back to flat colours, so the
 * UI keeps working while the art is still being drawn.
 *
 * <p>Sprites live under {@code assets/hui/textures/gui/sprites/<path>.png} and are referenced as {@code hui:<path>}.
 * The window frame and the button use a {@code .mcmeta} nine-slice descriptor so a single small PNG scales to any
 * window size. See {@code docs/gui-texture-spec.md} for the drawing contract.
 */
public final class GuiTextures {
    /** Nine slice window frame, border 4px (see docs/gui-texture-spec.md). */
    public static final Identifier WINDOW_FRAME = id("window/frame");
    /** Slot background, drawn at 18x18. */
    public static final Identifier SLOT = id("slot");
    /** Hovered slot highlight, drawn at 24x24 with a 3px overflow around the slot. */
    public static final Identifier SLOT_HOVER = id("slot_hover");
    /** Button, nine slice border 3px. */
    public static final Identifier BUTTON = id("button");

    private static Boolean available;

    private GuiTextures() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("hui", path);
    }

    /** @return true when the HUI texture set is installed; results are cached. */
    public static boolean available() {
        if (available == null) {
            available = resourceExists("textures/gui/sprites/window/frame.png")
                    && resourceExists("textures/gui/sprites/slot.png");
        }
        return available;
    }

    /** Forgets the cached availability (resource reload / config reload). */
    public static void invalidate() {
        available = null;
    }

    private static boolean resourceExists(String path) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        return minecraft.getResourceManager()
                .getResource(Identifier.fromNamespaceAndPath("hui", path))
                .isPresent();
    }

    public static void drawWindowFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (available()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WINDOW_FRAME, x, y, width, height);
        } else {
            graphics.fill(x, y, x + width, y + height, 0xE610161C);
            graphics.outline(x, y, width, height, 0xFF3FC7FF);
        }
    }

    public static void drawSlot(GuiGraphicsExtractor graphics, int x, int y) {
        if (available()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 18, 18);
        } else {
            graphics.fill(x, y, x + 18, y + 18, 0xFF0B0F14);
            graphics.outline(x, y, 18, 18, 0xFF2F4353);
        }
    }

    public static void drawSlotHover(GuiGraphicsExtractor graphics, int x, int y) {
        if (available()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HOVER, x - 3, y - 3, 24, 24);
        } else {
            graphics.outline(x, y, 18, 18, 0xFF7FD9FF);
        }
    }

    public static void drawButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (available()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BUTTON, x, y, width, height);
        } else {
            graphics.fill(x, y, x + width, y + height, 0xFF16202A);
            graphics.outline(x, y, width, height, 0xFF3FC7FF);
        }
    }
}
