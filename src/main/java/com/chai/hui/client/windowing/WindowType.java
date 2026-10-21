package com.chai.hui.client.windowing;

import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A registered window type: its id (persisted with the window), the default title, and the factory that creates the
 * content when the window is restored. Use {@link com.chai.hui.client.windowing.api.HuiWindowApi} to register new types.
 */
public record WindowType(Identifier id, String titleKey, Supplier<Component> title,
        Supplier<WindowContent> factory) {

    public WindowContent createContent() {
        return this.factory.get();
    }

    /** Convenience constructor for built-in types whose title is a translation key. */
    public static WindowType translatable(Identifier id, String titleKey, Supplier<WindowContent> factory) {
        return new WindowType(id, titleKey, () -> Component.translatable(titleKey), factory);
    }
}
