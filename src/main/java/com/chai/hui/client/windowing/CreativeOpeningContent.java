package com.chai.hui.client.windowing;

import com.chai.hui.network.OpenCreativePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Placeholder shown while HUI asks the server to open its server side creative inventory menu.
 *
 * <p>Once the menu opens the HUI screen handler replaces this content with the real creative window content.
 */
public final class CreativeOpeningContent implements WindowContent {
    private static boolean requested;
    private int ticks;

    public static void markOpened() {
        requested = true;
    }

    /** Called when the desktop closes so the next open requests the menu again. */
    public static void reset() {
        requested = false;
    }

    @Override
    public int preferredWidth() {
        return 200;
    }

    @Override
    public int preferredHeight() {
        return 44;
    }

    @Override
    public void init(int screenWidth, int screenHeight) {
        if (!requested) {
            requested = true;
            ClientPacketDistributor.sendToServer(new OpenCreativePayload());
        }
    }

    @Override
    public void tick() {
        this.ticks++;
        if (this.ticks > 0 && this.ticks % 40 == 0) {
            // The menu open packet may have been missed (e.g. another menu was closed in the same tick): retry.
            ClientPacketDistributor.sendToServer(new OpenCreativePayload());
        }
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        Font font = Minecraft.getInstance().font;
        graphics.fill(originX, originY, originX + preferredWidth(), originY + preferredHeight(), 0xE610161C);
        graphics.outline(originX, originY, preferredWidth(), preferredHeight(), 0xFF3FC7FF);
        graphics.centeredText(font, Component.translatable("hui.creative.opening"),
                originX + preferredWidth() / 2, originY + 18, 0xFFE6EDF3);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, int originX, int originY, boolean doubleClick) {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return false;
    }
}
