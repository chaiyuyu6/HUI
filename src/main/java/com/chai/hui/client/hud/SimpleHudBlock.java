package com.chai.hui.client.hud;

import java.util.List;
import java.util.function.BiFunction;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * A {@link HudBlock} made of one or more HUD layers that are simply drawn translated by the configured offset.
 *
 * <p>Typical for blocks whose vanilla position only depends on the window size (experience bar, hotbar, ...).
 */
public class SimpleHudBlock implements HudBlock {
    private final Identifier id;
    private final Component label;
    private final List<GuiLayer> layers;
    private final BiFunction<Integer, Integer, HudRect> bounds;

    public SimpleHudBlock(Identifier id, String labelKey, List<GuiLayer> layers,
            BiFunction<Integer, Integer, HudRect> bounds) {
        this.id = id;
        this.label = Component.translatable(labelKey);
        this.layers = List.copyOf(layers);
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
    public HudRect bounds(int screenWidth, int screenHeight) {
        return this.bounds.apply(screenWidth, screenHeight);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, int offsetX, int offsetY) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX, offsetY);
        try {
            for (GuiLayer layer : this.layers) {
                layer.render(graphics, deltaTracker);
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
