package com.chai.hui.client.screen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Small helper for opening HUI screens on top of the current screen.
 *
 * <p>HUI uses NeoForge's screen layer stack ({@code Gui#pushScreenLayer}) so that a screen can be opened from another
 * HUI screen and return to it. Unlike {@code Gui#setScreen}, {@code pushScreenLayer} does not release the mouse grab,
 * which would leave the camera following the mouse while the screen is open, so the missing bits are applied here.
 */
public final class HuiScreens {
    private HuiScreens() {}

    /** Pushes the given screen and restores the normal in-screen mouse behaviour. */
    public static void open(Minecraft minecraft, Screen screen) {
        minecraft.gui.pushScreenLayer(screen);
        minecraft.mouseHandler.releaseMouse();
        minecraft.mouseHandler.resyncMousePosition();
        KeyMapping.releaseAll();
    }
}
