package com.chai.hui.client.windowing.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A clickable button. */
public final class ButtonWidget implements Widget {
    private static final int HEIGHT = 18;
    private static final int PADDING = 6;

    private final Component label;
    private final Runnable action;

    public ButtonWidget(Component label, Runnable action) {
        this.label = label;
        this.action = action;
    }

    @Override
    public int width(Font font) {
        return font.width(this.label) + PADDING * 2;
    }

    @Override
    public int height(Font font) {
        return HEIGHT;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width(font) && mouseY >= y && mouseY < y + HEIGHT;
        graphics.fill(x, y, x + width(font), y + HEIGHT, hovered ? 0xFF2A3B4A : 0xFF1B2833);
        graphics.outline(x, y, width(font), HEIGHT, hovered ? 0xFF7FD9FF : 0xFF3FC7FF);
        graphics.centeredText(font, this.label, x + width(font) / 2, y + (HEIGHT - 8) / 2, 0xFFE6EDF3);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int x, int y, int button) {
        if (button != 0) {
            return false;
        }
        Font font = Minecraft.getInstance().font;
        if (mouseX >= x && mouseX < x + width(font) && mouseY >= y && mouseY < y + HEIGHT) {
            this.action.run();
            return true;
        }
        return false;
    }
}
