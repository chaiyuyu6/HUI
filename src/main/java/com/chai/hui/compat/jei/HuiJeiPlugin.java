package com.chai.hui.compat.jei;

import com.chai.hui.HuiMod;
import com.chai.hui.client.windowing.HuiDesktopScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/**
 * Optional JEI integration.
 *
 * <p>Tells JEI that the HUI desktop is a normal full screen GUI, so JEI draws its ingredient list and recipe overlay on
 * it. HUI draws its windows after other mods' foreground overlays (see {@code HuiDesktopScreen}), so the JEI layers end
 * up at the bottom and every HUI window covers them.
 */
@JeiPlugin
public final class HuiJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(HuiDesktopScreen.class, screen -> new DesktopGuiProperties(screen));
    }

    /** The whole desktop is treated as the JEI gui area. */
    private record DesktopGuiProperties(HuiDesktopScreen screen) implements IGuiProperties {
        @Override
        public Class<? extends Screen> screenClass() {
            return HuiDesktopScreen.class;
        }

        @Override
        public int guiLeft() {
            return 0;
        }

        @Override
        public int guiTop() {
            return 0;
        }

        @Override
        public int guiXSize() {
            return this.screen.width;
        }

        @Override
        public int guiYSize() {
            return this.screen.height;
        }

        @Override
        public int screenWidth() {
            return this.screen.width;
        }

        @Override
        public int screenHeight() {
            return this.screen.height;
        }
    }
}
