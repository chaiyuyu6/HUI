package com.chai.hui.client.ui.framework;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

/**
 * Factory helpers for the HUI UI framework: the small widget vocabulary that window contents and other mods build on.
 */
public final class Ui {
    public static final int ACCENT = 0xFF3FC7FF;
    public static final int TEXT = 0xFFE6EDF3;
    public static final int MUTED = 0xFF8CA3B4;
    public static final int PANEL = 0xE610161C;
    public static final int SURFACE = 0xFF16202A;
    public static final int BORDER = 0xFF2F4353;
    public static final int DANGER = 0xFFFF6B6B;

    private Ui() {
    }

    public static UiNode node(UiStyle style, UiNode... children) {
        UiNode node = new UiNode();
        node.style.width = style.width;
        node.style.height = style.height;
        node.style.widthPercent = style.widthPercent;
        node.style.heightPercent = style.heightPercent;
        node.style.minWidth = style.minWidth;
        node.style.minHeight = style.minHeight;
        node.style.paddingX = style.paddingX;
        node.style.paddingY = style.paddingY;
        node.style.gap = style.gap;
        node.style.direction = style.direction;
        node.style.justify = style.justify;
        node.style.align = style.align;
        node.style.background = style.background;
        node.style.borderColor = style.borderColor;
        node.style.borderWidth = style.borderWidth;
        node.style.color = style.color;
        node.style.textScale = style.textScale;
        node.style.textCentered = style.textCentered;
        node.style.overflowClip = style.overflowClip;
        node.style.transitionMs = style.transitionMs;
        node.style.easing = style.easing;
        node.style.focusable = style.focusable;
        return node.add(children);
    }

    public static UiNode panel(UiStyle style, UiNode... children) {
        UiStyle base = new UiStyle().background(PANEL).border(BORDER, 1).padding(6).gap(6);
        copy(base, style);
        return node(base, children);
    }

    public static UiNode column(UiStyle style, UiNode... children) {
        UiStyle base = new UiStyle().column().gap(4);
        copy(base, style);
        return node(base, children);
    }

    public static UiNode row(UiStyle style, UiNode... children) {
        UiStyle base = new UiStyle().row().gap(4);
        copy(base, style);
        return node(base, children);
    }

    public static UiNode label(String text) {
        UiNode node = new UiNode();
        node.text = () -> text;
        node.style.color = TEXT;
        return node;
    }

    public static UiNode label(Supplier<String> text) {
        UiNode node = new UiNode();
        node.text = text;
        node.style.color = TEXT;
        return node;
    }

    public static UiNode mutedLabel(Supplier<String> text) {
        UiNode node = label(text);
        node.style.color = MUTED;
        return node;
    }

    public static UiNode button(String label, Consumer<UiNode> onClick) {
        UiNode node = new UiNode();
        node.text = () -> label;
        node.style.background = SURFACE;
        node.style.border(BORDER, 1);
        node.style.color = TEXT;
        node.style.padding(6, 4);
        node.style.align(UiStyle.Align.CENTER);
        node.style.textCentered = true;
        node.onClick = onClick;
        node.onHoverEnter = current -> {
            current.style.background = 0xFF1E2C38;
            current.style.borderColor = ACCENT;
        };
        node.onHoverLeave = current -> {
            current.style.background = SURFACE;
            current.style.borderColor = BORDER;
        };
        node.measureWith(font -> new int[] {font.width(label) + 12, font.lineHeight + 8});
        return node;
    }

    public static UiNode image(Identifier sprite, int width, int height) {
        UiNode node = new UiNode();
        node.image = sprite;
        node.imageWidth = width;
        node.imageHeight = height;
        return node;
    }

    public static UiNode spacer(int width, int height) {
        UiNode node = new UiNode();
        node.style.width = width;
        node.style.height = height;
        return node;
    }

    /** A scroll container: children larger than the box scroll with the mouse wheel. */
    public static UiNode scroll(UiStyle style, UiNode... children) {
        UiStyle base = new UiStyle().column().gap(4).overflowClip();
        copy(base, style);
        return node(base, children);
    }

    /**
     * A simple text input (web {@code <input>} equivalent): click to focus, type to edit, backspace/enter to submit.
     */
    public static UiNode textInput(String placeholder, Supplier<String> value, Consumer<String> onChange) {
        TextInput input = new TextInput(placeholder, value, onChange);
        return input;
    }

    private static void copy(UiStyle base, UiStyle style) {
        if (style.width >= 0) {
            base.width = style.width;
        }
        if (style.height >= 0) {
            base.height = style.height;
        }
        if (style.widthPercent >= 0) {
            base.widthPercent = style.widthPercent;
        }
        if (style.heightPercent >= 0) {
            base.heightPercent = style.heightPercent;
        }
        if (style.minWidth > 0) {
            base.minWidth = style.minWidth;
        }
        if (style.minHeight > 0) {
            base.minHeight = style.minHeight;
        }
        if (style.paddingX > 0 || style.paddingY > 0) {
            base.paddingX = style.paddingX;
            base.paddingY = style.paddingY;
        }
        if (style.gap > 0) {
            base.gap = style.gap;
        }
        if (style.direction != null) {
            base.direction = style.direction;
        }
        if (style.justify != null) {
            base.justify = style.justify;
        }
        if (style.align != null) {
            base.align = style.align;
        }
        if (style.background != 0) {
            base.background = style.background;
        }
        if (style.borderColor != 0) {
            base.borderColor = style.borderColor;
            base.borderWidth = Math.max(1, style.borderWidth);
        }
        if (style.color != 0) {
            base.color = style.color;
        }
        if (style.textScale > 0) {
            base.textScale = style.textScale;
        }
        base.textCentered = style.textCentered;
        base.overflowClip = style.overflowClip;
        if (style.transitionMs > 0) {
            base.transitionMs = style.transitionMs;
        }
    }

    private static final class TextInput extends UiNode {
        private final String placeholder;
        private final Supplier<String> value;
        private final Consumer<String> onChange;
        private long lastCaretToggle;
        private boolean caretVisible = true;

        private TextInput(String placeholder, Supplier<String> value, Consumer<String> onChange) {
            this.placeholder = placeholder;
            this.value = value;
            this.onChange = onChange;
            this.style.width = 120;
            this.style.height = 16;
            this.style.padding(4, 3);
            this.style.background = 0xFF0B0F14;
            this.style.border(BORDER, 1);
            this.style.focusable = true;
            this.onClick = node -> {
                clearFocus(node);
                this.focused = true;
            };
            this.onChar = this::onCharTyped;
            this.onKey = this::onKeyTyped;
            this.onTick = node -> {
                long now = System.nanoTime();
                if (now - this.lastCaretToggle > 500_000_000L) {
                    this.lastCaretToggle = now;
                    this.caretVisible = !this.caretVisible;
                }
            };
            this.draw = (node, graphics) -> this.drawInput(graphics);
        }

        private static void clearFocus(UiNode node) {
            UiNode root = node;
            while (root.parent != null) {
                root = root.parent;
            }
            clearFocusRecursive(root);
        }

        private static void clearFocusRecursive(UiNode node) {
            node.focused = false;
            for (UiNode child : node.children) {
                clearFocusRecursive(child);
            }
        }

        private void onCharTyped(CharacterEvent event) {
            String current = this.value.get();
            String next = current + event.codepoint();
            this.onChange.accept(next);
        }

        private void onKeyTyped(KeyEvent event) {
            String current = this.value.get();
            int key = event.key();
            if (key == 259 && !current.isEmpty()) {
                this.onChange.accept(current.substring(0, current.length() - 1));
                return;
            }
            if (key == 257 || key == 335) {
                this.focused = false;
                return;
            }
            if (key == 256) {
                this.focused = false;
            }
        }

        private void drawInput(GuiGraphicsExtractor graphics) {
            Font font = Minecraft.getInstance().font;
            String text = this.value.get();
            int color = this.style.color;
            if (text.isEmpty()) {
                graphics.text(font, this.placeholder, this.x + this.style.paddingX,
                        this.y + (this.height - font.lineHeight) / 2, MUTED);
                text = "";
            } else {
                graphics.text(font, text, this.x + this.style.paddingX,
                        this.y + (this.height - font.lineHeight) / 2, color);
            }
            if (this.focused && this.caretVisible) {
                int caretX = this.x + this.style.paddingX + font.width(text) + 1;
                int caretY = this.y + (this.height - font.lineHeight) / 2;
                graphics.fill(caretX, caretY, caretX + 1, caretY + font.lineHeight, ACCENT);
            }
        }
    }
}
