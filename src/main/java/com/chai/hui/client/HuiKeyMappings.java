package com.chai.hui.client;

import com.chai.hui.HuiMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * All key bindings added by HUI.
 *
 * <p>26.3 switched the input backend to SDL: {@code InputConstants} now exposes scancode constants such as
 * {@link InputConstants#KEY_H} and the type {@link InputConstants.Type#KEYBOARD} (the old {@code KEYSYM}/GLFW
 * constants are gone). Modifiers come from NeoForge's {@link KeyModifier}, which makes the HUD editor default to
 * {@code Alt + H} while still being rebindable in the vanilla controls screen.
 */
public final class HuiKeyMappings {
    /** The "HUI" category shown in the vanilla controls screen. */
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "main"));

    /** Toggles the HUD information panel on/off. Default: {@code H}. */
    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.hui.toggle_hud",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_H,
            CATEGORY);

    /** Opens the HUI settings screen. Default: {@code K}. */
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.hui.open_settings",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_K,
            CATEGORY);

    /** Opens the HUD layout editor. Default: {@code Alt + H}. */
    public static final KeyMapping EDIT_HUD = new KeyMapping(
            "key.hui.edit_hud",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_H,
            CATEGORY);

    private HuiKeyMappings() {}
}
