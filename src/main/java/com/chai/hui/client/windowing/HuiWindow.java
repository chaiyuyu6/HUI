package com.chai.hui.client.windowing;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A draggable window on the HUI desktop.
 *
 * <p>The prototype keeps this deliberately small; the full design adds pages, pinning, minimizing and per-window
 * state persistence ({@code docs/windowing-design.md}).
 */
public final class HuiWindow {
    /** Height of the title bar / draggable area. */
    public static final int TITLE_HEIGHT = 16;

    private final Identifier id;
    private final Component title;
    private final WindowContent content;
    private final int width;
    private final int height;
    private int x;
    private int y;

    public HuiWindow(Identifier id, Component title, WindowContent content) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.width = content.preferredWidth();
        this.height = content.preferredHeight() + TITLE_HEIGHT;
    }

    public Identifier id() {
        return this.id;
    }

    public Component title() {
        return this.title;
    }

    public WindowContent content() {
        return this.content;
    }

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    /** X coordinate where the content starts (same as the window X, the frame is drawn on the outline). */
    public int contentX() {
        return this.x;
    }

    /** Y coordinate where the content starts, below the title bar. */
    public int contentY() {
        return this.y + TITLE_HEIGHT;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y
                && mouseY < this.y + this.height;
    }

    public boolean isInTitleBar(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y
                && mouseY < this.y + TITLE_HEIGHT;
    }
}
