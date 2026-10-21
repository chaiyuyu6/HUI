package com.chai.hui.client.hud;

import java.util.function.BiFunction;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * A block that is rendered <em>in place</em> inside NeoForge's own layer pipeline, with the configured offset applied
 * through {@code RenderGuiLayerEvent.Pre/Post} (see {@code HudLayoutManager#onLayerPre}).
 *
 * <p>This exists for layers that other mods decorate by listening to the layer's render event (the classic example is
 * AppleSkin drawing saturation/hunger information right after the vanilla food layer): a normally wrapped layer would
 * be moved, but the decorations drawn by other mods after it would stay at the vanilla position. Keeping the layer in
 * the pipeline means everything drawn between our matrix push and pop - including third-party decorations - moves
 * together.
 *
 * <p>In-place blocks cannot be reordered; they keep their vanilla drawing order.
 */
final class InPlaceHudBlock implements HudBlock {
    private final Identifier id;
    private final Component label;
    private final GuiLayer layer;
    private final BiFunction<Integer, Integer, HudRect> bounds;

    InPlaceHudBlock(Identifier id, String labelKey, GuiLayer layer, BiFunction<Integer, Integer, HudRect> bounds) {
        this.id = id;
        this.label = Component.translatable(labelKey);
        this.layer = layer;
        this.bounds = bounds;
    }

    @Override
    public Identifier id() {
        return this.id;
    }

    @Override
    public Component label() {
        return this.label;
    }

    @Override
    public boolean orderable() {
        return false;
    }

    @Override
    public HudRect bounds(int screenWidth, int screenHeight) {
        return this.bounds.apply(screenWidth, screenHeight);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, int offsetX, int offsetY) {
        // Only used as the editor preview; in game the layer is drawn by NeoForge's pipeline.
        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX, offsetY);
        try {
            this.layer.render(graphics, deltaTracker);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
