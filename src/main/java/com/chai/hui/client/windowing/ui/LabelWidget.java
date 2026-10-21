package com.chai.hui.client.windowing.ui;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A single line of text. */
public final class LabelWidget implements Widget {
    private final Supplier<Component> text;
    private final int color;

    public LabelWidget(Supplier<Component> text, int color) {
        this.text = text;
        this.color = color;
    }

    @Override
    public int width(Font font) {
        return font.width(this.text.get());
    }

    @Override
    public int height(Font font) {
        return 9;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        graphics.text(font, this.text.get(), x, y, this.color, false);
    }
}
