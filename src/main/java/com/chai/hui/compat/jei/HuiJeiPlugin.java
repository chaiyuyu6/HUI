package com.chai.hui.compat.jei;

import java.lang.reflect.Field;
import java.util.Optional;

import com.chai.hui.HuiMod;
import com.chai.hui.client.windowing.HuiDesktopScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Optional JEI integration.
 *
 * <ul>
 *   <li>Tells JEI that the HUI desktop is a normal full screen GUI, so JEI draws its ingredient list on it (the HUI
 *       windows themselves are drawn afterwards, so the JEI layers stay at the bottom).</li>
 *   <li>Recipe transfer: JEI resolves the transfer target from the parent container screen. When the recipes GUI is
 *       opened from the HUI desktop, the parent is temporarily replaced with a vanilla container screen created around
 *       the HUI window's real menu, so JEI's normal transfer handlers run against the correct block entity menu. When
 *       the recipes GUI closes, HUI returns to the desktop instead of that throwaway screen.</li>
 * </ul>
 */
@JeiPlugin
public final class HuiJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "jei_plugin");

    private @Nullable HuiDesktopScreen transferDesktop;
    private @Nullable AbstractContainerScreen<?> transferProxy;

    public HuiJeiPlugin() {
        HuiMod.LOGGER.info("[HUI] JEI plugin constructed");
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(HuiDesktopScreen.class, DesktopGuiProperties::new);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        NeoForge.EVENT_BUS.addListener(this::onScreenOpening);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void onScreenOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (next instanceof mezz.jei.gui.recipes.RecipesGui recipesGui) {
            HuiMod.LOGGER.info("[HUI] JEI recipes gui opening, current={}", event.getCurrentScreen());
            if (event.getCurrentScreen() instanceof HuiDesktopScreen desktop) {
                AbstractContainerMenu menu = desktop.activeServerMenu();
                HuiMod.LOGGER.info("[HUI] JEI recipes gui: active HUI menu={}", menu);
                AbstractContainerScreen<?> proxy = menu == null ? null : createProxyScreen(menu);
                HuiMod.LOGGER.info("[HUI] JEI recipes gui: proxy screen={}", proxy);
                if (proxy != null) {
                    desktop.preserveAcrossExternalScreen();
                    setParentScreen(recipesGui, proxy);
                    this.transferDesktop = desktop;
                    this.transferProxy = proxy;
                    HuiMod.LOGGER.info("[HUI] JEI recipe transfer target set to the HUI machine menu {}", menu.containerId);
                }
            }
            return;
        }
        if (this.transferProxy != null && this.transferDesktop != null && next != null
                && event.getCurrentScreen() instanceof mezz.jei.gui.recipes.RecipesGui
                && next.getClass() == this.transferProxy.getClass()) {
            // JEI closed: return to the HUI desktop instead of showing the throwaway vanilla screen.
            event.setNewScreen(this.transferDesktop);
            this.transferProxy = null;
            this.transferDesktop = null;
        }
    }

    private static @Nullable AbstractContainerScreen<?> createProxyScreen(AbstractContainerMenu menu) {
        try {
            MenuType<?> type = menu.getType();
            if (type == null) {
                return null;
            }
            Optional<?> factory = MenuScreens.getScreenFactory(type);
            if (factory.isEmpty() || Minecraft.getInstance().player == null) {
                return null;
            }
            MenuScreens.ScreenConstructor constructor = (MenuScreens.ScreenConstructor) factory.get();
            return (AbstractContainerScreen<?>) constructor.create(menu,
                    Minecraft.getInstance().player.getInventory(), Component.empty());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static void setParentScreen(mezz.jei.gui.recipes.RecipesGui recipesGui,
            AbstractContainerScreen<?> proxy) {
        try {
            Field field = mezz.jei.gui.recipes.RecipesGui.class.getDeclaredField("parentScreen");
            field.setAccessible(true);
            field.set(recipesGui, proxy);
        } catch (ReflectiveOperationException exception) {
            HuiMod.LOGGER.warn("[HUI] could not set the JEI parent screen for recipe transfer", exception);
        }
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
            return Math.max(80, this.screen.width - 170);
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
