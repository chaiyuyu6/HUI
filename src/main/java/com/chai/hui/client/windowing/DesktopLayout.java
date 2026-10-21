package com.chai.hui.client.windowing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.resources.Identifier;

/**
 * The persisted desktop state: pages, windows inside them, the active page and the "use HUI inventory instead of the
 * vanilla one" toggle.
 *
 * <p>This is save scoped (see {@link DesktopLayoutStore}); only windows with {@link WindowLayout#pinned} survive a
 * desktop close. Field names are the JSON keys, keep them stable or bump {@link #version}.
 */
public final class DesktopLayout {
    public int version = 1;
    public List<PageLayout> pages = new ArrayList<>();
    public int activePage;
    public boolean huiInventory = true;

    public static DesktopLayout createDefault() {
        DesktopLayout layout = new DesktopLayout();
        layout.pages.add(new PageLayout());
        return layout;
    }

    /** Makes sure there is at least one page and the active index is valid. */
    public void ensurePages() {
        if (this.pages.isEmpty()) {
            this.pages.add(new PageLayout());
        }
        this.activePage = Math.max(0, Math.min(this.pages.size() - 1, this.activePage));
    }

    public PageLayout activePage() {
        ensurePages();
        return this.pages.get(this.activePage);
    }

    public PageLayout addPage() {
        PageLayout page = new PageLayout();
        this.pages.add(page);
        this.activePage = this.pages.size() - 1;
        return page;
    }

    /** Removes pages that contain no windows, keeping at least one page. */
    public void removeEmptyPages() {
        if (this.pages.size() <= 1) {
            return;
        }
        this.pages.removeIf(page -> page.windows.isEmpty());
        if (this.pages.isEmpty()) {
            this.pages.add(new PageLayout());
        }
        this.activePage = Math.max(0, Math.min(this.pages.size() - 1, this.activePage));
    }

    /** One inventory page. An empty {@link #name} means "use the default translatable name". */
    public static final class PageLayout {
        public String name = "";
        public List<WindowLayout> windows = new ArrayList<>();
    }

    /** Persisted state of a single window. */
    public static final class WindowLayout {
        public String id = UUID.randomUUID().toString();
        public String type = "";
        public String titleKey = "";
        /** Literal custom title set by the player (empty = use the window type's default title). */
        public String title = "";
        public int x;
        public int y;
        public int width;
        public int height;
        public boolean minimized;
        public boolean pinned;
        /** Block entity windows: packed BlockPos and dimension id, empty/0 for other window types. */
        public long pos;
        public String dimension = "";

        public static WindowLayout of(Identifier type) {
            WindowLayout layout = new WindowLayout();
            layout.type = type.toString();
            return layout;
        }
    }
}
