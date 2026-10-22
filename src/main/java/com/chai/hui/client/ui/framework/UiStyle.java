package com.chai.hui.client.ui.framework;

/**
 * Web-like style properties for a {@link UiNode}: sizes, flex layout, colours, borders, text and transitions.
 * Unset sizes are {@code -1} (auto).
 */
public final class UiStyle {
    public enum Direction {
        ROW,
        COLUMN
    }

    public enum Justify {
        START,
        CENTER,
        END,
        SPACE_BETWEEN
    }

    public enum Align {
        START,
        CENTER,
        END,
        STRETCH
    }

    public enum Easing {
        LINEAR,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT
    }

    public int width = -1;
    public int height = -1;
    public double widthPercent = -1;
    public double heightPercent = -1;
    public int minWidth = 0;
    public int minHeight = 0;
    public int paddingX = 0;
    public int paddingY = 0;
    public int gap = 0;
    public Direction direction = Direction.COLUMN;
    public Justify justify = Justify.START;
    public Align align = Align.START;
    boolean directionSet;
    boolean justifySet;
    boolean alignSet;
    public int background = 0;
    public int borderColor = 0;
    public int borderWidth = 0;
    public int color = 0xFFE6EDF3;
    public float textScale = 1.0F;
    public boolean textCentered = false;
    public boolean overflowClip = false;
    public int transitionMs = 0;
    public Easing easing = Easing.LINEAR;
    public boolean visible = true;
    public boolean focusable = false;

    public UiStyle width(int value) {
        this.width = value;
        return this;
    }

    public UiStyle height(int value) {
        this.height = value;
        return this;
    }

    public UiStyle size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public UiStyle percentWidth(double percent) {
        this.widthPercent = percent;
        return this;
    }

    public UiStyle percentHeight(double percent) {
        this.heightPercent = percent;
        return this;
    }

    public UiStyle padding(int x, int y) {
        this.paddingX = x;
        this.paddingY = y;
        return this;
    }

    public UiStyle padding(int all) {
        return padding(all, all);
    }

    public UiStyle gap(int value) {
        this.gap = value;
        return this;
    }

    public UiStyle row() {
        this.direction = Direction.ROW;
        this.directionSet = true;
        return this;
    }

    public UiStyle column() {
        this.direction = Direction.COLUMN;
        this.directionSet = true;
        return this;
    }

    public UiStyle justify(Justify value) {
        this.justify = value;
        this.justifySet = true;
        return this;
    }

    public UiStyle align(Align value) {
        this.align = value;
        this.alignSet = true;
        return this;
    }

    public UiStyle background(int argb) {
        this.background = argb;
        return this;
    }

    public UiStyle border(int argb, int width) {
        this.borderColor = argb;
        this.borderWidth = width;
        return this;
    }

    public UiStyle color(int argb) {
        this.color = argb;
        return this;
    }

    public UiStyle textScale(float scale) {
        this.textScale = scale;
        return this;
    }

    public UiStyle textCentered() {
        this.textCentered = true;
        return this;
    }

    public UiStyle overflowClip() {
        this.overflowClip = true;
        return this;
    }

    public UiStyle transition(int ms) {
        this.transitionMs = ms;
        return this;
    }

    public UiStyle easing(Easing value) {
        this.easing = value;
        return this;
    }
}
