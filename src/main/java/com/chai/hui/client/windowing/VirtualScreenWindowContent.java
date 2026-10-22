package com.chai.hui.client.windowing;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Hosts an arbitrary vanilla {@link Screen} inside a HUI window: the screen is initialised with the virtual size of the
 * content area, rendered translated into the window and receives the window local input.
 *
 * <p>Used experimentally for the JEI recipe screen ({@code HuiJeiPlugin}); screens that truly assume they are
 * {@code Minecraft.screen} may not support every interaction.
 */
public final class VirtualScreenWindowContent implements WindowContent {
    private final Screen screen;
    private final int virtualWidth;
    private final int virtualHeight;
    private boolean initialised;

    public VirtualScreenWindowContent(Screen screen, int virtualWidth, int virtualHeight) {
        this.screen = screen;
        this.virtualWidth = virtualWidth;
        this.virtualHeight = virtualHeight;
    }

    @Override
    public int preferredWidth() {
        return this.virtualWidth;
    }

    @Override
    public int preferredHeight() {
        return this.virtualHeight;
    }

    @Override
    public boolean paintsBodyBackground() {
        return false;
    }

    @Override
    public void init(int screenWidth, int screenHeight) {
        if (this.initialised) {
            return;
        }
        this.initialised = true;
        this.screen.init(this.virtualWidth, this.virtualHeight);
        this.screen.added();
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        int localX = mouseX - originX;
        int localY = mouseY - originY;
        graphics.pose().pushMatrix();
        graphics.pose().translate(originX, originY);
        graphics.enableScissor(originX, originY, originX + this.virtualWidth, originY + this.virtualHeight);
        this.screen.extractBackground(graphics, localX, localY, partialTick);
        this.screen.extractRenderState(graphics, localX, localY, partialTick);
        graphics.disableScissor();
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, int originX, int originY, boolean doubleClick) {
        return this.screen.mouseClicked(local(event, originX, originY), doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event, int originX, int originY) {
        return this.screen.mouseReleased(local(event, originX, originY));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, int originX, int originY, double dragX, double dragY) {
        return this.screen.mouseDragged(local(event, originX, originY), dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, int originX,
            int originY) {
        return this.screen.mouseScrolled(mouseX - originX, mouseY - originY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return this.screen.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return this.screen.charTyped(event);
    }

    @Override
    public void tick() {
        this.screen.tick();
    }

    @Override
    public void removed() {
        this.screen.removed();
    }

    private static MouseButtonEvent local(MouseButtonEvent event, int originX, int originY) {
        return new MouseButtonEvent(event.x() - originX, event.y() - originY, event.buttonInfo());
    }
}
