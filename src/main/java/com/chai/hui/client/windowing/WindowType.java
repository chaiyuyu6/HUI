package com.chai.hui.client.windowing;

import java.util.function.Supplier;

import net.minecraft.resources.Identifier;

/**
 * A registered window type: its id (persisted with the window), the default title translation key, and the factory that
 * creates the content when the window is restored.
 *
 * <p>P1 keeps this minimal; the full design in {@code docs/windowing-design.md} adds declared sizes and the public
 * registration API.
 */
public record WindowType(Identifier id, String titleKey, Supplier<WindowContent> factory) {
    public WindowContent createContent() {
        return this.factory.get();
    }
}
