package com.chai.hui.client.windowing;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A runtime window on the HUI desktop: its content plus the per-window state (position, size, title, minimized, pinned).
 *
 * <p>Persistence is handled by the desktop through {@link DesktopLayout.WindowLayout}; this class is never serialized
 * directly.
 */
public final class HuiWindow {
    /** Height of the title bar / draggable area. */
    public static final int TITLE_HEIGHT = 16;

    private final String uid;
    private final Identifier typeId;
    private final WindowContent content;
    private Component title;
    private int x;
    private int y;
    private int width;
    private int height;
    private boolean minimized;
    private boolean pinned;
    private boolean ephemeral;

    public HuiWindow(String uid, Identifier typeId, Component title, WindowContent content) {
        this.uid = uid;
        this.typeId = typeId;
        this.title = title;
        this.content = content;
        this.width = content.preferredWidth();
        this.height = content.preferredHeight() + TITLE_HEIGHT;
    }

    public String uid() {
        return this.uid;
    }

    public Identifier typeId() {
        return this.typeId;
    }

    public Component title() {
        return this.title;
    }

    public void setTitle(Component title) {
        this.title = title;
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

    public int contentX() {
        return this.x;
    }

    public int contentY() {
        return this.y + TITLE_HEIGHT;
    }

    public boolean minimized() {
        return this.minimized;
    }

    public void setMinimized(boolean minimized) {
        this.minimized = minimized;
    }

    public boolean pinned() {
        return this.pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public boolean ephemeral() {
        return this.ephemeral;
    }

    public void setEphemeral(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
    }

    public boolean isInTitleBar(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y
                && mouseY < this.y + TITLE_HEIGHT;
    }
}
