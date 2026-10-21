package com.chai.hui.client.windowing.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

/** A vertical or horizontal container widget with padding and gap. */
public final class PanelWidget implements Widget {
    public enum Direction {
        COLUMN,
        ROW
    }

    private final Direction direction;
    private final List<Widget> children = new ArrayList<>();
    private int padding = 4;
    private int gap = 2;

    public PanelWidget(Direction direction) {
        this.direction = direction;
    }

    public PanelWidget padding(int padding) {
        this.padding = padding;
        return this;
    }

    public PanelWidget gap(int gap) {
        this.gap = gap;
        return this;
    }

    public PanelWidget child(Widget widget) {
        this.children.add(widget);
        return this;
    }

    public Direction direction() {
        return this.direction;
    }

    public List<Widget> children() {
        return this.children;
    }

    @Override
    public int width(Font font) {
        int inner = 0;
        if (this.direction == Direction.COLUMN) {
            for (Widget child : this.children) {
                inner = Math.max(inner, child.width(font));
            }
        } else {
            for (int index = 0; index < this.children.size(); index++) {
                inner += this.children.get(index).width(font) + (index > 0 ? this.gap : 0);
            }
        }
        return inner + this.padding * 2;
    }

    @Override
    public int height(Font font) {
        int inner = 0;
        if (this.direction == Direction.COLUMN) {
            for (int index = 0; index < this.children.size(); index++) {
                inner += this.children.get(index).height(font) + (index > 0 ? this.gap : 0);
            }
        } else {
            for (Widget child : this.children) {
                inner = Math.max(inner, child.height(font));
            }
        }
        return inner + this.padding * 2;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int cursor = 0;
        for (Widget child : this.children) {
            if (this.direction == Direction.COLUMN) {
                child.render(graphics, font, x + this.padding, y + this.padding + cursor, mouseX, mouseY);
                cursor += child.height(font) + this.gap;
            } else {
                child.render(graphics, font, x + this.padding + cursor, y + this.padding, mouseX, mouseY);
                cursor += child.width(font) + this.gap;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int x, int y, int button) {
        Font font = Minecraft.getInstance().font;
        int cursor = 0;
        for (Widget child : this.children) {
            int childX = this.direction == Direction.COLUMN ? x + this.padding : x + this.padding + cursor;
            int childY = this.direction == Direction.COLUMN ? y + this.padding + cursor : y + this.padding;
            if (child.mouseClicked(mouseX, mouseY, childX, childY, button)) {
                return true;
            }
            cursor += (this.direction == Direction.COLUMN ? child.height(font) : child.width(font)) + this.gap;
        }
        return false;
    }

    /** @return the deepest widget at the position, used for text input focus handling. */
    public @Nullable Widget widgetAt(double mouseX, double mouseY, int x, int y) {
        Font font = Minecraft.getInstance().font;
        int cursor = 0;
        for (Widget child : this.children) {
            int childX = this.direction == Direction.COLUMN ? x + this.padding : x + this.padding + cursor;
            int childY = this.direction == Direction.COLUMN ? y + this.padding + cursor : y + this.padding;
            int childWidth = child.width(font);
            int childHeight = child.height(font);
            if (mouseX >= childX && mouseX < childX + childWidth && mouseY >= childY && mouseY < childY + childHeight) {
                if (child instanceof PanelWidget panel) {
                    return panel.widgetAt(mouseX, mouseY, childX, childY);
                }
                return child;
            }
            cursor += (this.direction == Direction.COLUMN ? childHeight : childWidth) + this.gap;
        }
        return null;
    }
}
