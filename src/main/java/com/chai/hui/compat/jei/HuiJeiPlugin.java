package com.chai.hui.compat.jei;

import java.lang.reflect.Field;
import java.util.Optional;

import com.chai.hui.HuiMod;
import com.chai.hui.client.windowing.HuiDesktopScreen;
import com.chai.hui.client.windowing.WindowContent;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
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

    private static @Nullable IJeiRuntime runtime;

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
        registration.addGuiScreenHandler(HuiDesktopScreen.class, new DesktopScreenHandler());
        registration.addGhostIngredientHandler(HuiDesktopScreen.class, new DesktopGhostHandler());
    }


    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        HuiJeiPlugin.runtime = runtime;
        NeoForge.EVENT_BUS.addListener(this::onScreenOpening);
    }

    /**
     * Opens the HUI native recipe browser for the item at the given desktop coordinates (R key on a HUI slot).
     *
     * @return true when a browser window was opened.
     */
    public static boolean tryHandleHuiKey(HuiDesktopScreen desktop, net.minecraft.client.input.KeyEvent event,
            int mouseX, int mouseY) {
        if (runtime == null) {
            return false;
        }
        net.minecraft.world.item.ItemStack stack = desktop.itemAt(mouseX, mouseY);
        if (stack == null) {
            return false;
        }
        var typed = runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack);
        if (typed.isEmpty()) {
            return false;
        }
        com.mojang.blaze3d.platform.InputConstants.Key key = com.mojang.blaze3d.platform.InputConstants.getKey(event);
        var mappings = runtime.getKeyMappings();
        var bookmarks = runtime.getBookmarkManager();
        if (!mappings.getBookmark().isUnbound() && mappings.getBookmark().isActiveAndMatches(key)) {
            if (bookmarks.contains(typed.get())) {
                bookmarks.remove(typed.get());
            } else {
                bookmarks.add(typed.get());
            }
            return true;
        }
        if (mappings.getShowUses().isActiveAndMatches(key)) {
            desktop.queueCustomWindow(Component.translatable("hui.window.recipe_browser_uses"),
                    new RecipeBrowserContent(runtime, stack, true));
            return true;
        }
        if (mappings.getShowRecipe().isActiveAndMatches(key)) {
            desktop.queueCustomWindow(Component.translatable("hui.window.recipe_browser"),
                    new RecipeBrowserContent(runtime, stack));
            return true;
        }
        return false;
    }

    /** Recreates a persisted recipe browser window from its saved focus data ("R:item_id" / "U:item_id"). */
    public static @Nullable WindowContent createRecipeBrowserContent(String data) {
        if (runtime == null || data == null || data.length() < 3) {
            return null;
        }
        boolean uses = data.startsWith("U:");
        Identifier itemId = Identifier.tryParse(data.substring(2));
        if (itemId == null) {
            return null;
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId)
                .map(item -> (WindowContent) new RecipeBrowserContent(runtime,
                        new net.minecraft.world.item.ItemStack(item), uses))
                .orElse(null);
    }


    @SuppressWarnings({"rawtypes", "unchecked"})
    private void onScreenOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (next instanceof mezz.jei.gui.recipes.RecipesGui recipesGui) {
            if (event.getCurrentScreen() instanceof HuiDesktopScreen desktop && runtime != null) {
                // R on a HUI slot: JEI asks to open its recipes screen, open the HUI native browser instead.
                net.minecraft.world.item.ItemStack hovered = desktop.hoveredItem();
                if (hovered != null) {
                    event.setNewScreen(desktop);
                    desktop.queueCustomWindow(Component.translatable("hui.window.recipe_browser"),
                            new RecipeBrowserContent(runtime, hovered));
                    HuiMod.LOGGER.info("[HUI] R on HUI slot, opened the HUI recipe browser");
                    return;
                }
            }
            HuiMod.LOGGER.info("[HUI] JEI recipes gui opening, current={}", event.getCurrentScreen());
            if (event.getCurrentScreen() instanceof HuiDesktopScreen desktop) {
                AbstractContainerMenu menu = desktop.activeServerMenu();
                HuiMod.LOGGER.info("[HUI] JEI recipes gui: active HUI menu={}", menu);
                AbstractContainerScreen<?> proxy = menu == null ? null : createProxyScreen(menu);
                HuiMod.LOGGER.info("[HUI] JEI recipes gui: proxy screen={}", proxy);
                if (proxy != null) {
                    // Make the machine menu the server side active menu, otherwise the transfer clicks are rejected.
                    desktop.activateActiveContent();
                    desktop.prepareExternalScreen();
                    setParentScreen(recipesGui, proxy);
                    this.transferDesktop = desktop;
                    this.transferProxy = proxy;
                    HuiMod.LOGGER.info("[HUI] JEI recipe transfer target set to the HUI machine menu {}", menu.containerId);
                }
            }
            return;
        }
        if (this.transferProxy != null && this.transferDesktop != null
                && event.getCurrentScreen() instanceof mezz.jei.gui.recipes.RecipesGui) {
            // JEI closed: return to the HUI desktop instead of showing the throwaway vanilla screen.
            HuiMod.LOGGER.info("[HUI] JEI closed, returning to the HUI desktop");
            this.transferDesktop.restoreAfterExternalScreen();
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

    /**
     * JEI screen handler for the desktop: gui properties for the overlay layout plus clickable ingredients for the
     * slots of the HUI windows, so hovering a HUI slot shows the JEI recipe/usage hints (R / U).
     */
    /**
     * JEI ghost drag: the slots of the active HUI window become drop targets. In creative mode the dropped item is
     * inserted through the server validated CreativeInsertPayload; survival drops are ignored (the ghost item is not real).
     */
    private static final class DesktopGhostHandler
            implements mezz.jei.api.gui.handlers.IGhostIngredientHandler<HuiDesktopScreen> {
        @Override
        public <I> java.util.List<mezz.jei.api.gui.handlers.IGhostIngredientHandler.Target<I>> getTargetsTyped(
                HuiDesktopScreen screen, mezz.jei.api.ingredients.ITypedIngredient<I> typedIngredient, boolean doStart) {
            Object ingredient = typedIngredient.getIngredient();
            if (!(ingredient instanceof net.minecraft.world.item.ItemStack)) {
                return java.util.List.of();
            }
            java.util.List<mezz.jei.api.gui.handlers.IGhostIngredientHandler.Target<I>> targets = new java.util.ArrayList<>();
            for (int[] area : screen.activeSlotAreas()) {
                targets.add(new mezz.jei.api.gui.handlers.IGhostIngredientHandler.Target<I>() {
                    @Override
                    public net.minecraft.client.renderer.Rect2i getArea() {
                        return new net.minecraft.client.renderer.Rect2i(area[1], area[2], area[3], area[4]);
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public void accept(I value) {
                        if (value instanceof net.minecraft.world.item.ItemStack stack) {
                            screen.ghostInsert(area[0], stack);
                        }
                    }
                });
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }

        @Override
        public boolean shouldHighlightTargets() {
            return true;
        }
    }

    private static final class DesktopScreenHandler implements mezz.jei.api.gui.handlers.IScreenHandler<HuiDesktopScreen> {
        @Override
        public IGuiProperties apply(HuiDesktopScreen screen) {
            return new DesktopGuiProperties(screen);
        }

        @Override
        public java.util.Optional<? extends mezz.jei.api.runtime.IClickableIngredient<?>> getClickableIngredientUnderMouse(
                mezz.jei.api.gui.builder.IClickableIngredientFactory factory, HuiDesktopScreen screen, double mouseX,
                double mouseY) {
            net.minecraft.world.item.ItemStack stack = screen.itemAt(mouseX, mouseY);
            if (stack == null) {
                return java.util.Optional.empty();
            }
            return factory.createBuilder(stack)
                    .buildWithArea((int) mouseX - 8, (int) mouseY - 8, 16, 16);
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
