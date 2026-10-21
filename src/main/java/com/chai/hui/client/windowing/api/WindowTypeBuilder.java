package com.chai.hui.client.windowing.api;

import java.util.Objects;
import java.util.function.Supplier;

import com.chai.hui.client.windowing.HuiWindowTypes;
import com.chai.hui.client.windowing.WindowContent;
import com.chai.hui.client.windowing.WindowType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Builder for a window type that can be registered by other mods (or HUI itself).
 *
 * <pre>{@code
 * HuiWindowApi.register(Identifier.fromNamespaceAndPath("mymod", "machine"))
 *         .title(() -> Component.translatable("mymod.window.machine"))
 *         .content(() -> new UiPanelContent(Ui.column()
 *                 .gap(4)
 *                 .child(Ui.label(Component.literal("Hello")))
 *                 .child(Ui.button(Component.literal("OK"), () -> {}))))
 *         .register();
 * }</pre>
 */
public final class WindowTypeBuilder {
    private final Identifier id;
    private Supplier<Component> title;
    private Supplier<WindowContent> content;

    WindowTypeBuilder(Identifier id) {
        this.id = id;
        this.title = () -> Component.literal(id.getPath());
    }

    public WindowTypeBuilder title(Supplier<Component> title) {
        this.title = Objects.requireNonNull(title, "title");
        return this;
    }

    public WindowTypeBuilder content(Supplier<WindowContent> content) {
        this.content = Objects.requireNonNull(content, "content");
        return this;
    }

    /** Registers the type and returns its id (usable with {@link HuiWindowApi#open}). */
    public Identifier register() {
        if (this.content == null) {
            throw new IllegalStateException("window type " + this.id + " has no content factory");
        }
        HuiWindowTypes.register(new WindowType(this.id, "", this.title, this.content));
        return this.id;
    }
}
