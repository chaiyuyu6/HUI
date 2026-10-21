package com.chai.hui;

import java.util.Locale;

/** The screen corner the HUI information panel is anchored to. */
public enum HudCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    /** @return the next corner, cycling back to the first one. */
    public HudCorner next() {
        HudCorner[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** @return the translation key of this corner, e.g. {@code hui.corner.top_left}. */
    public String translationKey() {
        return "hui.corner." + name().toLowerCase(Locale.ROOT);
    }
}
