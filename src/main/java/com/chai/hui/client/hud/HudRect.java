package com.chai.hui.client.hud;

/**
 * An axis-aligned rectangle in GUI-scaled screen coordinates.
 *
 * @param x      left edge
 * @param y      top edge
 * @param width  width, always positive
 * @param height height, always positive
 */
public record HudRect(int x, int y, int width, int height) {
    /** @return {@code true} if the given point is inside this rectangle, edges included on the top/left side. */
    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < right() && mouseY >= this.y && mouseY < bottom();
    }

    /** @return a copy of this rectangle moved by the given amount. */
    public HudRect offset(int dx, int dy) {
        return new HudRect(this.x + dx, this.y + dy, this.width, this.height);
    }

    public int right() {
        return this.x + this.width;
    }

    public int bottom() {
        return this.y + this.height;
    }

    public int centerX() {
        return this.x + this.width / 2;
    }
}
