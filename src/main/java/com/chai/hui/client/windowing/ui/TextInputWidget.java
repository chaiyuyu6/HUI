package com.chai.hui.client.windowing.ui;

import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/** A single line text input with a blinking-free caret (P4 prototype widget). */
public final class TextInputWidget implements Widget {
    public static final int HEIGHT = 18;

    private final StringBuilder value = new StringBuilder();
    private final int maxLength;
    private final int preferredWidth;
    private final Consumer<String> responder;
    private final Runnable onSubmit;
    private boolean focused;

    public TextInputWidget(String initial, int maxLength, int preferredWidth, Consumer<String> responder,
            Runnable onSubmit) {
        this.value.append(initial);
        this.maxLength = maxLength;
        this.preferredWidth = preferredWidth;
        this.responder = responder;
        this.onSubmit = onSubmit;
    }

    public String value() {
        return this.value.toString();
    }

    public boolean focused() {
        return this.focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public int width(Font font) {
        return this.preferredWidth;
    }

    @Override
    public int height(Font font) {
        return HEIGHT;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        graphics.fill(x, y, x + this.preferredWidth, y + HEIGHT, 0xFF0B0F14);
        graphics.outline(x, y, this.preferredWidth, HEIGHT, this.focused ? 0xFF7FD9FF : 0xFF3A4A58);
        String text = this.value.toString();
        int textWidth = font.width(text);
        int room = this.preferredWidth - 8;
        if (textWidth > room) {
            // Show the tail of the text like vanilla edit boxes do.
            int start = 0;
            while (start < text.length() && font.width(text.substring(start)) > room) {
                start++;
            }
            text = text.substring(start);
        }
        graphics.text(font, text, x + 4, y + (HEIGHT - 8) / 2, 0xFFE6EDF3, false);
        if (this.focused) {
            int caretX = x + 4 + font.width(text) + 1;
            graphics.fill(caretX, y + 4, caretX + 1, y + HEIGHT - 4, 0xFF7FD9FF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int x, int y, int button) {
        return button == 0 && mouseX >= x && mouseX < x + this.preferredWidth && mouseY >= y
                && mouseY < y + HEIGHT;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!this.focused || !event.isAllowedChatCharacter() || this.value.length() >= this.maxLength) {
            return false;
        }
        this.value.appendCodePoint(event.codepoint());
        if (this.responder != null) {
            this.responder.accept(value());
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.focused) {
            return false;
        }
        if (event.key() == 259 && !this.value.isEmpty()) { // backspace
            this.value.deleteCharAt(this.value.length() - 1);
            if (this.responder != null) {
                this.responder.accept(value());
            }
            return true;
        }
        if (event.key() == 257 || event.key() == 335) { // enter / numpad enter
            if (this.onSubmit != null) {
                this.onSubmit.run();
            }
            return true;
        }
        return false;
    }
}
