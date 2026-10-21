package com.chai.hui.client.windowing;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * The content of a window. Implementations own their rendering and input handling; the desktop only positions the
 * window and forwards events with the content origin so the content never has to know where the window is.
 *
 * <p>Mouse coordinates are desktop coordinates: content implementations translate them into their own space.
 */
public interface WindowContent {
    /** Preferred content width in GUI pixels. */
    int preferredWidth();

    /** Preferred content height in GUI pixels. */
    int preferredHeight();

    /** Called with the desktop size, e.g. to lay out an embedded screen that expects a full screen size. */
    default void init(int screenWidth, int screenHeight) {}

    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX, int originY);

    default boolean mouseClicked(MouseButtonEvent event, int originX, int originY, boolean doubleClick) {
        return false;
    }

    default boolean mouseDragged(MouseButtonEvent event, int originX, int originY, double dragX, double dragY) {
        return false;
    }

    default boolean mouseReleased(MouseButtonEvent event, int originX, int originY) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, int originX,
            int originY) {
        return false;
    }

    default boolean keyPressed(KeyEvent event) {
        return false;
    }

    default boolean charTyped(CharacterEvent event) {
        return false;
    }

    default void tick() {}

    default void removed() {}
}
