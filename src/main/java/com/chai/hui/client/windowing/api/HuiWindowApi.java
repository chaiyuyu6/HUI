package com.chai.hui.client.windowing.api;

import com.chai.hui.client.screen.HuiScreens;
import com.chai.hui.client.windowing.HuiDesktopScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/**
 * Public entry point for mods that want to add HUI windows.
 *
 * <p>P4 scope: Java registration of window types plus a small declarative widget library ({@link com.chai.hui.client
 * .windowing.ui.Ui}); the content factory is called on the client when the window is created/restored. JEI integration
 * is not part of this step (documented as future work).
 */
public final class HuiWindowApi {
    private HuiWindowApi() {}

    /** Starts a builder for a new window type. Call {@link WindowTypeBuilder#register()} from client init. */
    public static WindowTypeBuilder register(Identifier id) {
        return new WindowTypeBuilder(id);
    }

    /** Opens the desktop with a fresh window of the given type (only when no other screen is open). */
    public static void open(Minecraft minecraft, Identifier typeId) {
        if (minecraft.gui.screen() == null) {
            HuiScreens.open(minecraft, HuiDesktopScreen.openWithType(typeId));
        }
    }

    /**
     * Fullscreen fallback: opens a screen as a normal full screen on top of the game. Use it for custom GUIs that cannot
     * be embedded inside a window (they lose the windowing features but keep working).
     */
    public static void openFullscreen(Minecraft minecraft, Screen screen) {
        HuiScreens.open(minecraft, screen);
    }
}
