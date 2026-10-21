package com.chai.hui.client.hud;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.chai.hui.HuiMod;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayerManager;

/**
 * Discovers every HUD layer that has been registered by the time HUI receives {@link RegisterGuiLayersEvent}.
 *
 * <p>NeoForge does not expose the layer list publicly, but the event itself carries it (the same list that backs
 * {@code wrapLayer}/{@code registerAbove}). Reading it lets HUI offer all registered layers - including the ones added
 * by other mods - in the HUD editor, instead of only the handful of vanilla layers HUI knows by name.
 *
 * <p>The read is defensive: if the field is missing (NeoForge changed it) or access is denied, discovery simply
 * returns an empty list and HUI falls back to its built-in blocks.
 */
final class HudLayerDiscovery {
    private HudLayerDiscovery() {}

    /** @return the ids of all layers registered so far, in registration order (empty if discovery is unavailable). */
    static List<Identifier> discover(RegisterGuiLayersEvent event) {
        try {
            Field field = RegisterGuiLayersEvent.class.getDeclaredField("layers");
            field.setAccessible(true);
            Object value = field.get(event);
            if (!(value instanceof List<?> layers)) {
                return List.of();
            }

            List<Identifier> ids = new ArrayList<>(layers.size());
            for (Object layer : layers) {
                if (layer instanceof GuiLayerManager.NamedLayer named) {
                    ids.add(named.name());
                }
            }
            return ids;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            HuiMod.LOGGER.warn("Could not enumerate the registered HUD layers; auto-detection is disabled", exception);
            return List.of();
        }
    }
}
