package com.chai.hui.client;

import com.chai.hui.HuiConfig;
import com.chai.hui.HuiMod;
import com.chai.hui.client.hud.HuiHudLayer;
import com.chai.hui.client.screen.HuiConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client entry point of HUI.
 *
 * <p>Loads only on the physical client. It wires up the HUD layer, the key bindings, the in-game key handling and the
 * custom config screen.
 */
@Mod(value = HuiMod.MOD_ID, dist = Dist.CLIENT)
public final class HuiClient {
    /** Id of the HUI HUD layer, also useful for other mods that want to order against it. */
    public static final Identifier HUD_LAYER_ID = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "info_hud");

    public HuiClient(ModContainer modContainer, IEventBus modEventBus) {
        modEventBus.addListener(this::registerGuiLayers);
        modEventBus.addListener(this::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);

        // Lets the "Config" button in the Mods menu open the HUI settings screen.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new HuiConfigScreen());
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(HUD_LAYER_ID, new HuiHudLayer());
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(HuiKeyMappings.CATEGORY);
        event.register(HuiKeyMappings.TOGGLE_HUD);
        event.register(HuiKeyMappings.OPEN_SETTINGS);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        while (HuiKeyMappings.TOGGLE_HUD.consumeClick()) {
            if (minecraft.gui.screen() != null || !HuiConfig.SPEC.isLoaded()) {
                continue;
            }
            HuiConfig.ENABLED.set(!HuiConfig.ENABLED.get());
            HuiConfig.SPEC.save();
        }

        while (HuiKeyMappings.OPEN_SETTINGS.consumeClick()) {
            if (minecraft.gui.screen() == null) {
                minecraft.gui.pushScreenLayer(new HuiConfigScreen());
            }
        }
    }
}
