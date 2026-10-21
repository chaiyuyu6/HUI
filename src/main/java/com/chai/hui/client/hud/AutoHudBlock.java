package com.chai.hui.client.hud;

import com.chai.hui.HuiMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.jetbrains.annotations.Nullable;

/**
 * A HUD block that was discovered automatically, e.g. a vanilla layer HUI does not special-case or a HUD layer added
 * by another mod.
 *
 * <p>Its bounds are measured lazily with {@link HudBoundsMeasurer} the first time they are needed (editor only), so
 * layers that currently draw nothing are simply not shown in the editor. Layers that cover most of the screen (vignette,
 * sleep/demo overlays, ...) are treated as overlays and excluded from the editor as well.
 *
 * <p>Auto-detected blocks support moving and hiding. They cannot be reordered: they are rendered by NeoForge's own layer
 * pipeline (with the offset applied), which keeps their relative drawing order exactly as the owning mod intended.
 */
final class AutoHudBlock implements HudBlock {
    /** Layers covering more than this fraction of the screen are overlays, not editable HUD widgets. */
    private static final double MAX_EDITABLE_AREA_RATIO = 0.5;

    private final Identifier id;
    private final GuiLayer layer;

    private @Nullable HudRect cachedBounds;
    private int measuredWidth = -1;
    private int measuredHeight = -1;
    private boolean editable;

    AutoHudBlock(Identifier id, GuiLayer layer) {
        this.id = id;
        this.layer = layer;
    }

    @Override
    public Identifier id() {
        return this.id;
    }

    @Override
    public Component label() {
        String key = "hui.block." + this.id.getNamespace() + "." + this.id.getPath();
        return Component.translatableWithFallback(key, this.id.toString());
    }

    @Override
    public boolean orderable() {
        return false;
    }

    @Override
    public HudRect bounds(int screenWidth, int screenHeight) {
        ensureMeasured(screenWidth, screenHeight);
        return this.cachedBounds != null ? this.cachedBounds : new HudRect(0, 0, 0, 0);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, int offsetX, int offsetY) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX, offsetY);
        try {
            this.layer.render(graphics, deltaTracker);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /** @return whether this layer can be shown in the editor (it draws something and is not a full screen overlay). */
    boolean isEditable(int screenWidth, int screenHeight) {
        ensureMeasured(screenWidth, screenHeight);
        return this.editable;
    }

    private void ensureMeasured(int screenWidth, int screenHeight) {
        if (this.measuredWidth == screenWidth && this.measuredHeight == screenHeight) {
            return;
        }
        this.measuredWidth = screenWidth;
        this.measuredHeight = screenHeight;

        Minecraft minecraft = Minecraft.getInstance();
        HudRect measured = HudBoundsMeasurer.measure(minecraft, this.layer, minecraft.getDeltaTracker());
        this.cachedBounds = measured == null ? null : clampToScreen(measured, screenWidth, screenHeight);
        this.editable = this.cachedBounds != null
                && (long) this.cachedBounds.width() * this.cachedBounds.height()
                        <= (long) (screenWidth * screenHeight * MAX_EDITABLE_AREA_RATIO);
        if (this.cachedBounds == null) {
            HuiMod.LOGGER.debug("HUD layer {} draws nothing right now, hiding it from the editor", this.id);
        }
    }

    private static HudRect clampToScreen(HudRect rectangle, int screenWidth, int screenHeight) {
        int left = Math.max(0, rectangle.x());
        int top = Math.max(0, rectangle.y());
        int right = Math.min(screenWidth, rectangle.right());
        int bottom = Math.min(screenHeight, rectangle.bottom());
        if (right <= left || bottom <= top) {
            return new HudRect(0, 0, 0, 0);
        }
        return new HudRect(left, top, right - left, bottom - top);
    }
}
