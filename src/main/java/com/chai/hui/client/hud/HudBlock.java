package com.chai.hui.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A logical HUD block that can be moved, hidden and (if {@link #orderable()}) reordered by the HUD editor.
 *
 * <p>A block may be backed by one or several vanilla (or modded) HUD layers. Implementations are responsible for
 * applying the configured offsets themselves, because some blocks (health) have to compensate for the vanilla
 * layout state they are drawn from.
 */
public interface HudBlock {
    /** Unique id of this block, used as the persistence key. */
    Identifier id();

    /** Display name shown in the editor. */
    Component label();

    /** @return whether the editor should offer "bring to front" for this block. */
    default boolean orderable() {
        return true;
    }

    /**
     * The block's default (un-offset) bounds for the current screen and game state.
     *
     * @param screenWidth  GUI-scaled screen width
     * @param screenHeight GUI-scaled screen height
     */
    HudRect bounds(int screenWidth, int screenHeight);

    /**
     * Renders the block at its default position plus the given offset.
     *
     * @param offsetX configured horizontal offset in GUI pixels
     * @param offsetY configured vertical offset in GUI pixels
     */
    void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, int offsetX, int offsetY);
}
