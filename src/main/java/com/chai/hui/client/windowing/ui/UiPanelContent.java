package com.chai.hui.client.windowing.ui;

import com.chai.hui.client.windowing.WindowContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Window content backed by a {@link PanelWidget} tree. Handles text input focus: clicking a text input focuses it, any
 * other click clears the focus, characters and keys are forwarded to the focused input.
 *
 * <p>This is the P4 prototype of the "write windows like web pages" API; third party mods can build content with
 * {@link Ui} and register a window type through {@code HuiWindowApi}.
 */
public final class UiPanelContent implements WindowContent {
    private final PanelWidget root;
    private @Nullable TextInputWidget focused;

    public UiPanelContent(PanelWidget root) {
        this.root = root;
    }

    public PanelWidget root() {
        return this.root;
    }

    private Font font() {
        return Minecraft.getInstance().font;
    }

    @Override
    public int preferredWidth() {
        return this.root.width(font());
    }

    @Override
    public int preferredHeight() {
        return this.root.height(font());
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        this.root.render(graphics, font(), originX, originY, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, int originX, int originY, boolean doubleClick) {
        Widget hit = this.root.widgetAt(event.x(), event.y(), originX, originY);
        if (hit instanceof TextInputWidget input) {
            if (this.focused != null && this.focused != input) {
                this.focused.setFocused(false);
            }
            this.focused = input;
            input.setFocused(true);
        } else if (this.focused != null) {
            this.focused.setFocused(false);
            this.focused = null;
        }
        return this.root.mouseClicked(event.x(), event.y(), originX, originY, event.button());
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return this.focused != null && this.focused.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return this.focused != null && this.focused.keyPressed(event);
    }
}
