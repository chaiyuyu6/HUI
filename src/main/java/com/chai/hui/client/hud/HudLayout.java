package com.chai.hui.client.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.Identifier;

/**
 * The persisted state of the HUD layout: per block offset, visibility and draw order.
 *
 * <p>Instances are mutable working copies. The layout manager keeps one committed instance (used for rendering in
 * game) and, while the editor is open, one working copy that is discarded when the user cancels.
 */
public final class HudLayout {
    /** Offsets are clamped to this range so a broken config cannot push a HUD block unreachably far away. */
    private static final int OFFSET_LIMIT = 8192;

    private final Map<Identifier, Entry> entries = new LinkedHashMap<>();

    /** @return a deep copy of this layout. */
    public HudLayout copy() {
        HudLayout copy = new HudLayout();
        this.entries.forEach((id, entry) -> copy.entries.put(id, entry.copy()));
        return copy;
    }

    /**
     * Makes sure the given block has an entry.
     *
     * @param defaultOrder draw order used when the block is seen for the first time
     * @return the (possibly newly created) entry
     */
    public Entry ensure(Identifier id, int defaultOrder) {
        return this.entries.computeIfAbsent(id, key -> {
            Entry entry = new Entry();
            entry.order = defaultOrder;
            return entry;
        });
    }

    /** @return the ids of all blocks known to this layout, in insertion order. */
    public Collection<Identifier> ids() {
        return List.copyOf(this.entries.keySet());
    }

    /** @return the entry of the given block, creating a default one if necessary. */
    public Entry entry(Identifier id) {
        return ensure(id, this.entries.size());
    }

    public int offsetX(Identifier id) {
        return entry(id).offsetX;
    }

    public int offsetY(Identifier id) {
        return entry(id).offsetY;
    }

    public void setOffset(Identifier id, int offsetX, int offsetY) {
        Entry entry = entry(id);
        entry.offsetX = clamp(offsetX);
        entry.offsetY = clamp(offsetY);
    }

    public boolean isHidden(Identifier id) {
        return entry(id).hidden;
    }

    public void setHidden(Identifier id, boolean hidden) {
        entry(id).hidden = hidden;
    }

    /** Moves the block to the end of the draw order, i.e. it is rendered on top of the other blocks. */
    public void bringToFront(Identifier id) {
        int highest = Integer.MIN_VALUE;
        for (Entry entry : this.entries.values()) {
            highest = Math.max(highest, entry.order);
        }
        entry(id).order = highest + 1;
    }

    /** Restores the default state (no offset, visible, original order) for every given block. */
    public void resetAll(Collection<Identifier> ids) {
        int order = 0;
        for (Identifier id : ids) {
            Entry entry = entry(id);
            entry.offsetX = 0;
            entry.offsetY = 0;
            entry.hidden = false;
            entry.order = order++;
        }
    }

    /** Restores the default offset and visibility of a single block; the draw order is left untouched. */
    public void reset(Identifier id) {
        Entry entry = entry(id);
        entry.offsetX = 0;
        entry.offsetY = 0;
        entry.hidden = false;
    }
    /** @return the given block ids sorted by their configured draw order (first = bottom). */
    public List<Identifier> ordered(Collection<Identifier> knownIds) {
        List<Identifier> ordered = new ArrayList<>(knownIds);
        ordered.sort(Comparator.comparingInt(id -> entry(id).order));
        return ordered;
    }

    /** @return {@code true} if the block is the last one in the draw order. */
    public boolean isFront(Identifier id) {
        int order = entry(id).order;
        for (Entry entry : this.entries.values()) {
            if (entry.order > order) {
                return false;
            }
        }
        return true;
    }

    private static int clamp(int value) {
        return Math.max(-OFFSET_LIMIT, Math.min(OFFSET_LIMIT, value));
    }

    /** Mutable state of a single block. Field access is package-private for the layout store. */
    public static final class Entry {
        int offsetX;
        int offsetY;
        boolean hidden;
        int order;

        private Entry copy() {
            Entry copy = new Entry();
            copy.offsetX = this.offsetX;
            copy.offsetY = this.offsetY;
            copy.hidden = this.hidden;
            copy.order = this.order;
            return copy;
        }

        public int offsetX() {
            return this.offsetX;
        }

        public int offsetY() {
            return this.offsetY;
        }

        public boolean hidden() {
            return this.hidden;
        }

        public int order() {
            return this.order;
        }
    }
}
