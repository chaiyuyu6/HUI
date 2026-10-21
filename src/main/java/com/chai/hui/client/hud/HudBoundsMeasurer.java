package com.chai.hui.client.hud;

import com.chai.hui.HuiMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.jetbrains.annotations.Nullable;

/**
 * Measures the on-screen area a HUD layer occupies by rendering it into a throw-away GUI render state and reading the
 * bounds of everything it queued.
 *
 * <p>This is what makes auto-detected layers draggable: HUI does not know how a third-party HUD layer draws itself, but
 * the 26.3 render-state model records the bounds of every text, item, blit and custom GUI element it emits, so a dry
 * render is enough to find out where it is.
 */
final class HudBoundsMeasurer {
    private HudBoundsMeasurer() {}

    /** @return the bounds the layer occupies, or {@code null} if it drew nothing (or failed to render). */
    static @Nullable HudRect measure(Minecraft minecraft, GuiLayer layer, DeltaTracker deltaTracker) {
        GuiRenderState renderState = new GuiRenderState();
        renderState.nextStratum();
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(minecraft, renderState, 0, 0);

        // Some vanilla layers (the health/armour stack) mutate these counters while drawing; restore them afterwards.
        Hud hud = minecraft.gui.hud;
        int leftHeight = hud.leftHeight;
        int rightHeight = hud.rightHeight;
        boolean rendered;
        try {
            layer.render(graphics, deltaTracker);
            rendered = true;
        } catch (RuntimeException exception) {
            rendered = false;
            HuiMod.LOGGER.debug("Could not measure HUD layer bounds", exception);
        } finally {
            hud.leftHeight = leftHeight;
            hud.rightHeight = rightHeight;
        }
        if (!rendered) {
            return null;
        }

        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        renderState.forEachElement(element -> include(box, element.bounds()), GuiRenderState.TraverseRange.ALL);
        renderState.forEachText(text -> include(box, text.bounds()));
        renderState.forEachItem(item -> include(box, item.bounds()));
        renderState.forEachPictureInPicture(picture -> include(box, picture.bounds()));
        if (box[0] > box[2] || box[1] > box[3]) {
            return null;
        }
        return new HudRect(box[0], box[1], box[2] - box[0] + 1, box[3] - box[1] + 1);
    }

    private static void include(int[] box, @Nullable ScreenRectangle rectangle) {
        if (rectangle == null || rectangle.width() <= 0 || rectangle.height() <= 0) {
            return;
        }
        box[0] = Math.min(box[0], rectangle.left());
        box[1] = Math.min(box[1], rectangle.top());
        box[2] = Math.max(box[2], rectangle.right());
        box[3] = Math.max(box[3], rectangle.bottom());
    }
}
