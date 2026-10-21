package com.chai.hui.client.windowing.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * A small declarative widget used by {@link UiPanelContent}. Widgets know their size and draw themselves relative to a
 * given origin; the panel handles layout and input dispatch.
 */
public interface Widget {
    int width(Font font);

    int height(Font font);

    void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY);

    default boolean mouseClicked(double mouseX, double mouseY, int x, int y, int button) {
        return false;
    }

    default boolean keyPressed(KeyEvent event) {
        return false;
    }

    default boolean charTyped(CharacterEvent event) {
        return false;
    }
}
