package com.chai.hui.client;

import com.chai.hui.HuiConfig;
import com.chai.hui.HuiMod;
import com.chai.hui.client.hud.HudLayoutManager;
import com.chai.hui.client.hud.HuiHudLayer;
import com.chai.hui.client.hud.editor.HudEditorScreen;
import com.chai.hui.client.screen.HuiConfigScreen;
import com.chai.hui.client.screen.HuiScreens;
import com.chai.hui.client.windowing.DesktopLayoutStore;
import com.chai.hui.client.windowing.HuiDesktopScreen;
import com.chai.hui.client.windowing.HuiRemoteContainers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client entry point of HUI.
 *
 * <p>Loads only on the physical client. It wires up the HUD layers, the HUD layout editor, the key bindings, the
 * in-game key handling and the config screens.
 */
@Mod(value = HuiMod.MOD_ID, dist = Dist.CLIENT)
public final class HuiClient {
    /** Id of the HUI information panel layer. */
    public static final Identifier HUD_LAYER_ID = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "info_hud");

    public HuiClient(ModContainer modContainer, IEventBus modEventBus) {
        // LOWEST so every other mod has registered its HUD layers before HUI enumerates them for auto-detection.
        modEventBus.addListener(EventPriority.LOWEST, this::registerGuiLayers);
        modEventBus.addListener(this::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        // In-place HUD layers (hunger) are translated by pushing/popping the pose around the layer render, so
        // decorations other mods draw after that layer (e.g. AppleSkin) move together with it.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RenderGuiLayerEvent.Pre.class,
                HudLayoutManager.get()::onLayerPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderGuiLayerEvent.Post.class,
                HudLayoutManager.get()::onLayerPost);
        // Safety net in case a layer render was cancelled between push and pop.
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class, HudLayoutManager.get()::onGuiPost);
        // HUI inventory mode: replace the vanilla player inventory screen with the windowed desktop (toggle lives in the
        // desktop's right-click menu and is stored per save).
        NeoForge.EVENT_BUS.addListener(this::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(this::onLoggingOut);

        // Lets the "Config" button in the Mods menu open the HUI settings screen.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new HuiConfigScreen());
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        // Register the HUI panel first: the layout manager auto-detects every layer that exists at this point, so the
        // panel becomes a regular movable block in the editor instead of a special case.
        event.registerAboveAll(HUD_LAYER_ID, new HuiHudLayer());
        // Takes ownership of the vanilla HUD layers that can be re-arranged by the editor.
        HudLayoutManager.get().initialize(event);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(HuiKeyMappings.CATEGORY);
        event.register(HuiKeyMappings.TOGGLE_HUD);
        event.register(HuiKeyMappings.OPEN_SETTINGS);
        event.register(HuiKeyMappings.EDIT_HUD);
        event.register(HuiKeyMappings.OPEN_DESKTOP);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        HuiRemoteContainers.tick();

        while (HuiKeyMappings.TOGGLE_HUD.consumeClick()) {
            if (minecraft.gui.screen() != null || !HuiConfig.SPEC.isLoaded()) {
                continue;
            }
            HuiConfig.ENABLED.set(!HuiConfig.ENABLED.get());
            HuiConfig.SPEC.save();
        }

        while (HuiKeyMappings.OPEN_SETTINGS.consumeClick()) {
            if (minecraft.gui.screen() == null) {
                HuiScreens.open(minecraft, new HuiConfigScreen());
            }
        }

        while (HuiKeyMappings.OPEN_DESKTOP.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null) {
                HuiScreens.open(minecraft, HuiDesktopScreen.openWithoutInventory());
            }
        }
        while (HuiKeyMappings.EDIT_HUD.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null && !HudLayoutManager.get().isEditing()) {
                HuiScreens.open(minecraft, new HudEditorScreen());
            }
        }

        // Safety net: never leave a working layout behind if the editor screen disappeared without being closed.
        if (HudLayoutManager.get().isEditing() && minecraft.gui.screen() == null) {
            HudLayoutManager.get().cancel();
        }
    }

    /**
     * Replaces the vanilla player inventory screen with the windowed desktop when "HUI inventory" is enabled. Only
     * screens opened from the game (no current screen) are replaced, so e.g. the inventory opened from another GUI is
     * left alone.
     */
    private void onScreenOpening(ScreenEvent.Opening event) {
        // A block entity container was requested (from the HUI "open" list or by restoring a pinned window): turn the
        // resulting container screen into a window instead of a full screen.
        if (event.getNewScreen() instanceof AbstractContainerScreen<?> container) {
            net.minecraft.core.BlockPos pos = HuiRemoteContainers.pollOpened();
            if (pos != null) {
                if (event.getCurrentScreen() instanceof HuiDesktopScreen desktop) {
                    desktop.queueContainerWindow(container, pos);
                    event.setNewScreen(desktop);
                } else {
                    event.setNewScreen(HuiDesktopScreen.openWithContainer(container, pos));
                }
                return;
            }
        }

        if (event.getCurrentScreen() != null) {
            return;
        }
        if (!(event.getNewScreen() instanceof InventoryScreen)) {
            return;
        }
        if (!DesktopLayoutStore.isHuiInventoryEnabled()) {
            return;
        }
        event.setNewScreen(HuiDesktopScreen.openWithInventory());
    }

    private void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DesktopLayoutStore.invalidate();
    }
}
