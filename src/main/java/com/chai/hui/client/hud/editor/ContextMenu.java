package com.chai.hui.client.hud.editor;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * A small, self-drawn popup menu used by the HUD editor.
 *
 * <p>It is deliberately not a vanilla widget: the editor screen owns all mouse handling, so the menu only needs to
 * know its own geometry, how to draw itself and which entry is under the mouse.
 */
public final class ContextMenu {
    private static final int PADDING = 4;
    private static final int ROW_HEIGHT = 14;
    private static final int MIN_WIDTH = 110;
    private static final int BACKGROUND = 0xF014181F;
    private static final int BORDER = 0xFF3FC7FF;
    private static final int HOVER = 0x40FFFFFF;
    private static final int TEXT = 0xFFE6EDF3;
    private static final int TEXT_DISABLED = 0xFF6B7280;

    /** A single clickable menu entry. */
    public record Entry(Component label, Runnable action, boolean enabled) {
        public static Entry of(String translationKey, Runnable action) {
            return new Entry(Component.translatable(translationKey), action, true);
        }
    }

    private final List<Entry> entries;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public ContextMenu(Font font, int x, int y, int screenWidth, int screenHeight, List<Entry> entries) {
        this.entries = List.copyOf(entries);

        int contentWidth = MIN_WIDTH;
        for (Entry entry : entries) {
            contentWidth = Math.max(contentWidth, font.width(entry.label()) + PADDING * 2);
        }
        this.width = Math.min(contentWidth, screenWidth);
        this.height = entries.size() * ROW_HEIGHT + PADDING * 2 - 2;
        this.x = Mth.clamp(x, 0, Math.max(0, screenWidth - this.width));
        this.y = Mth.clamp(y, 0, Math.max(0, screenHeight - this.height));
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BACKGROUND);
        graphics.outline(this.x, this.y, this.width, this.height, BORDER);

        for (int index = 0; index < this.entries.size(); index++) {
            Entry entry = this.entries.get(index);
            int rowY = rowY(index);
            boolean hovered = isInsideRow(mouseX, mouseY, rowY);
            if (hovered && entry.enabled()) {
                graphics.fill(this.x + 1, rowY, this.x + this.width - 1, rowY + ROW_HEIGHT, HOVER);
            }
            int color = !entry.enabled() ? TEXT_DISABLED : hovered ? 0xFFFFFFFF : TEXT;
            graphics.text(font, entry.label(), this.x + PADDING, rowY + (ROW_HEIGHT - font.lineHeight) / 2, color, true);
        }
    }

    /** @return the entry under the given point, or {@code null}. */
    public @Nullable Entry entryAt(double mouseX, double mouseY) {
        if (mouseX < this.x || mouseX >= this.x + this.width || mouseY < this.y || mouseY >= this.y + this.height) {
            return null;
        }
        int index = (int) ((mouseY - this.y - PADDING) / ROW_HEIGHT);
        if (index < 0 || index >= this.entries.size()) {
            return null;
        }
        return this.entries.get(index);
    }

    private int rowY(int index) {
        return this.y + PADDING + index * ROW_HEIGHT;
    }

    private boolean isInsideRow(double mouseX, double mouseY, int rowY) {
        return mouseX >= this.x + 1 && mouseX < this.x + this.width - 1
                && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
    }
}
