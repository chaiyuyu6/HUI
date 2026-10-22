package com.chai.hui.client.ui.framework;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * A node of the HUI UI tree: roughly the equivalent of a DOM element.
 *
 * <p>Nodes carry a {@link UiStyle}, children, event handlers and drawing data. They are laid out and rendered by
 * {@link UiWindowContent}. Custom rendering can be plugged in through {@link #draw}.
 */
public class UiNode {
    public final UiStyle style = new UiStyle();
    public final List<UiNode> children = new ArrayList<>();
    public final List<UiAnimation> animations = new ArrayList<>();

    public @Nullable String id;
    public @Nullable UiNode parent;

    /** Layout result (absolute GUI coordinates). */
    public int x;
    public int y;
    public int width;
    public int height;
    public int scrollY;
    public int contentHeight;

    public @Nullable Consumer<UiNode> onClick;
    public @Nullable Consumer<UiNode> onRightClick;
    public @Nullable Consumer<UiNode> onHoverEnter;
    public @Nullable Consumer<UiNode> onHoverLeave;
    public @Nullable Consumer<UiNode> onScroll;
    public @Nullable Consumer<KeyEvent> onKey;
    public @Nullable Consumer<CharacterEvent> onChar;
    /** Called every frame with the node, used by widgets for their own drawing. */
    public @Nullable BiConsumer<UiNode, GuiGraphicsExtractor> draw;
    /** Extra per-frame tick, e.g. for blinking carets. */
    public @Nullable Consumer<UiNode> onTick;

    public @Nullable Supplier<@Nullable String> text;
    public @Nullable Identifier image;
    public int imageU;
    public int imageV;
    public int imageWidth;
    public int imageHeight;
    /** Intrinsic size measurement for auto sized nodes: [width, height]. */
    public @Nullable Function<Font, int[]> measure;

    public boolean hovered;
    public boolean focused;
    public boolean pressed;

    public UiNode add(UiNode... nodes) {
        for (UiNode child : nodes) {
            child.parent = this;
            this.children.add(child);
        }
        return this;
    }

    public UiNode text(String value) {
        this.text = () -> value;
        return this;
    }

    public UiNode text(Supplier<@Nullable String> value) {
        this.text = value;
        return this;
    }

    public UiNode onClick(Consumer<UiNode> handler) {
        this.onClick = handler;
        return this;
    }

    public UiNode onRightClick(Consumer<UiNode> handler) {
        this.onRightClick = handler;
        return this;
    }

    public UiNode onHover(Consumer<UiNode> enter, Consumer<UiNode> leave) {
        this.onHoverEnter = enter;
        this.onHoverLeave = leave;
        return this;
    }

    public UiNode onScroll(Consumer<UiNode> handler) {
        this.onScroll = handler;
        return this;
    }

    public UiNode draw(BiConsumer<UiNode, GuiGraphicsExtractor> renderer) {
        this.draw = renderer;
        return this;
    }

    public UiNode tooltip(Supplier<String> tooltipText) {
        this.tooltipSupplier = tooltipText;
        return this;
    }

    public @Nullable Supplier<String> tooltipSupplier;

    public UiNode animate(UiAnimation animation) {
        this.animations.add(animation);
        return this;
    }

    public void tick(float deltaSeconds) {
        for (UiAnimation animation : this.animations) {
            animation.tick(deltaSeconds);
        }
        if (this.onTick != null) {
            this.onTick.accept(this);
        }
        for (UiNode child : this.children) {
            child.tick(deltaSeconds);
        }
    }

    public void measureWith(Function<Font, int[]> measurement) {
        this.measure = measurement;
    }

    public boolean visible() {
        return this.style.visible;
    }

    public @Nullable MouseButtonEvent lastClick;
}
