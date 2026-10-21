package com.chai.hui.client.windowing;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A simple virtual window content: a list of text lines. Used by the demo window and the window properties window in
 * the P1 prototype.
 */
public final class TextWindowContent implements WindowContent {
    private final List<Component> lines;
    private final int width;
    private final int height;

    public TextWindowContent(List<Component> lines, int width, int height) {
        this.lines = List.copyOf(lines);
        this.width = width;
        this.height = height;
    }

    /** The "new demo window" used to exercise multiple windows, z-order and pinning. */
    public static TextWindowContent demo() {
        return new TextWindowContent(List.of(
                Component.translatable("hui.demo.line1"),
                Component.translatable("hui.demo.line2"),
                Component.translatable("hui.demo.line3")), 200, 46);
    }

    /** Content for the ephemeral "window properties" window. */
    public static TextWindowContent properties(List<Component> lines) {
        return new TextWindowContent(lines, 200, lines.size() * 10 + 6);
    }

    @Override
    public int preferredWidth() {
        return this.width;
    }

    @Override
    public int preferredHeight() {
        return this.height;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        for (int index = 0; index < this.lines.size(); index++) {
            graphics.text(Minecraft.getInstance().font, this.lines.get(index), originX + 6, originY + 4 + index * 10,
                    0xFFE6EDF3, false);
        }
    }
}
