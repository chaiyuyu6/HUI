package com.chai.hui.client.windowing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.chai.hui.HuiMod;
import com.chai.hui.client.screen.HuiConfigScreen;
import com.chai.hui.client.screen.HuiScreens;
import com.chai.hui.client.ui.ContextMenu;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The HUI desktop (P1): pages with draggable windows, z-order, minimize/close/pin, a window manager and save-scoped
 * layout persistence.
 *
 * <p>Behaviour follows the decisions in {@code docs/windowing-design.md}: interacting with a window brings it to the
 * front, unpinned windows are dropped when the desktop closes, empty pages are removed, and the "HUI inventory vs vanilla
 * inventory" toggle is stored with the layout.
 */
public final class HuiDesktopScreen extends Screen {
    private static final Identifier PROPERTIES_WINDOW = HuiWindowTypes.PROPERTIES;

    private static final int TAB_HEIGHT = 14;
    private static final int TAB_TOP = 2;
    private static final int TAB_GAP = 2;

    private static final int BACKGROUND = 0x90000000;
    private static final int WINDOW_BODY = 0xD8101418;
    private static final int WINDOW_HEADER = 0xFF16202B;
    private static final int WINDOW_HEADER_FOCUSED = 0xFF1F3346;
    private static final int BORDER = 0xFF3FC7FF;
    private static final int BORDER_FOCUSED = 0xFF7FD9FF;
    private static final int TITLE_COLOR = 0xFFE6EDF3;
    private static final int HINT_COLOR = 0x80FFFFFF;
    private static final int TAB_COLOR = 0x40FFFFFF;
    private static final int TAB_ACTIVE_COLOR = 0xFF3FC7FF;

    private final boolean openInventoryWindow;

    private DesktopLayout layout = DesktopLayout.createDefault();
    private final List<HuiWindow> windows = new ArrayList<>();
    private boolean built;
    private boolean removedOnce;

    private @Nullable HuiWindow focused;
    private @Nullable HuiWindow dragging;
    private @Nullable HuiWindow contentDragging;
    private double dragOffsetX;
    private double dragOffsetY;
    private @Nullable ContextMenu menu;

    private HuiDesktopScreen(boolean openInventoryWindow) {
        super(Component.translatable("hui.desktop.title"));
        this.openInventoryWindow = openInventoryWindow;
    }

    /** Opens the desktop and makes sure the current page has an inventory window (used by the inventory key). */
    public static HuiDesktopScreen openWithInventory() {
        return new HuiDesktopScreen(true);
    }

    /** Opens the desktop restoring the pinned windows only (used by the F8 shortcut). */
    public static HuiDesktopScreen openWithoutInventory() {
        return new HuiDesktopScreen(false);
    }

    // --- Layout loading / page building ----------------------------------------------------

    @Override
    protected void init() {
        if (!this.built) {
            this.layout = DesktopLayoutStore.load();
            this.layout.ensurePages();
            rebuildActivePage();
            this.built = true;
            if (this.openInventoryWindow) {
                ensureInventoryWindow();
            }
        }
        clampAllWindows();
        for (HuiWindow window : this.windows) {
            window.content().init(this.width, this.height);
        }
    }

    private void rebuildActivePage() {
        for (HuiWindow window : this.windows) {
            window.content().removed();
        }
        this.windows.clear();
        this.focused = null;
        this.dragging = null;
        this.contentDragging = null;

        for (DesktopLayout.WindowLayout stored : this.layout.activePage().windows) {
            HuiWindow window = restoreWindow(stored);
            if (window != null) {
                this.windows.add(window);
                if (!window.minimized()) {
                    this.focused = window;
                }
            }
        }
    }

    private @Nullable HuiWindow restoreWindow(DesktopLayout.WindowLayout stored) {
        Identifier typeId;
        try {
            typeId = Identifier.parse(stored.type);
        } catch (RuntimeException exception) {
            HuiMod.LOGGER.warn("Skipping window with invalid type id '{}'", stored.type);
            return null;
        }
        WindowType type = HuiWindowTypes.get(typeId);
        if (type == null) {
            HuiMod.LOGGER.warn("Skipping window of unknown type {}", typeId);
            return null;
        }
        Component title = stored.titleKey.isEmpty()
                ? Component.translatable(type.titleKey())
                : Component.translatable(stored.titleKey);
        HuiWindow window = new HuiWindow(stored.id, typeId, title, type.createContent());
        window.setBounds(stored.x, stored.y, window.width(), window.height());
        window.setMinimized(stored.minimized);
        window.setPinned(stored.pinned);
        return window;
    }

    private void captureActivePage() {
        DesktopLayout.PageLayout page = this.layout.activePage();
        page.windows.clear();
        for (HuiWindow window : this.windows) {
            DesktopLayout.WindowLayout stored = new DesktopLayout.WindowLayout();
            stored.id = window.uid();
            stored.type = window.typeId().toString();
            stored.titleKey = "";
            stored.x = window.x();
            stored.y = window.y();
            stored.width = window.width();
            stored.height = window.height();
            stored.minimized = window.minimized();
            stored.pinned = window.pinned();
            page.windows.add(stored);
        }
    }

    // --- Pages -----------------------------------------------------------------------------

    private Component pageLabel(int index) {
        String custom = this.layout.pages.get(index).name;
        return custom.isEmpty() ? Component.translatable("hui.page.default_name", index + 1) : Component.literal(custom);
    }

    private int tabWidth(int index) {
        return this.font.width(pageLabel(index)) + 10;
    }

    private int tabX(int index) {
        int x = TAB_GAP;
        for (int i = 0; i < index; i++) {
            x += tabWidth(i) + TAB_GAP;
        }
        return x;
    }

    private int plusTabX() {
        return tabX(this.layout.pages.size());
    }

    private boolean inTabBar(double mouseY) {
        return mouseY >= TAB_TOP && mouseY < TAB_TOP + TAB_HEIGHT;
    }

    private void switchPage(int index) {
        if (index < 0 || index >= this.layout.pages.size() || index == this.layout.activePage) {
            return;
        }
        captureActivePage();
        this.layout.activePage = index;
        rebuildActivePage();
        for (HuiWindow window : this.windows) {
            window.content().init(this.width, this.height);
        }
        clampAllWindows();
    }

    private void addPage() {
        captureActivePage();
        this.layout.addPage();
        rebuildActivePage();
    }

    private void removeActivePageIfEmpty() {
        if (this.layout.pages.size() <= 1 || !this.windows.isEmpty()) {
            return;
        }
        for (HuiWindow window : this.windows) {
            window.content().removed();
        }
        this.windows.clear();
        this.layout.pages.remove(this.layout.activePage);
        this.layout.activePage = Math.max(0, this.layout.activePage - 1);
        this.layout.ensurePages();
        rebuildActivePage();
    }

    // --- Window operations -----------------------------------------------------------------

    private void ensureInventoryWindow() {
        for (HuiWindow window : new ArrayList<>(this.windows)) {
            if (window.typeId().equals(HuiWindowTypes.INVENTORY)) {
                closeWindow(window);
            }
        }
        addWindow(HuiWindowTypes.INVENTORY);
    }

    private @Nullable HuiWindow addWindow(Identifier typeId) {
        WindowType type = HuiWindowTypes.get(typeId);
        if (type == null) {
            return null;
        }
        HuiWindow window = new HuiWindow(UUID.randomUUID().toString(), typeId,
                Component.translatable(type.titleKey()), type.createContent());
        int cascade = this.windows.size() % 6;
        window.setPosition(Math.max(0, (this.width - window.width()) / 2 - 60 + cascade * 18),
                Math.max(TAB_HEIGHT + 8, (this.height - window.height()) / 2 - 40 + cascade * 18));
        clamp(window);
        window.content().init(this.width, this.height);
        this.windows.add(window);
        bringToFront(window);
        return window;
    }

    private void closeWindow(HuiWindow window) {
        window.content().removed();
        this.windows.remove(window);
        if (this.focused == window) {
            this.focused = this.windows.isEmpty() ? null : this.windows.get(this.windows.size() - 1);
        }
        if (this.dragging == window) {
            this.dragging = null;
        }
        if (this.contentDragging == window) {
            this.contentDragging = null;
        }
    }

    private void minimizeWindow(HuiWindow window) {
        window.setMinimized(true);
        if (this.focused == window) {
            this.focused = null;
        }
        if (this.dragging == window) {
            this.dragging = null;
        }
    }

    /** Restores a minimized window and focuses it (used by the window manager). */
    public void focusWindow(HuiWindow window) {
        if (!this.windows.contains(window)) {
            return;
        }
        window.setMinimized(false);
        bringToFront(window);
    }

    public List<HuiWindow> allWindows() {
        return List.copyOf(this.windows);
    }

    private void bringToFront(HuiWindow window) {
        this.windows.remove(window);
        this.windows.add(window);
        this.focused = window;
    }

    private void clampAllWindows() {
        for (HuiWindow window : this.windows) {
            clamp(window);
        }
    }

    private void clamp(HuiWindow window) {
        int x = Math.max(-window.width() + 60, Math.min(this.width - 60, window.x()));
        int y = Math.max(TAB_TOP + TAB_HEIGHT, Math.min(this.height - 40, window.y()));
        window.setPosition(x, y);
    }

    // --- Rendering -------------------------------------------------------------------------

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, BACKGROUND);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        for (HuiWindow window : this.windows) {
            if (!window.minimized()) {
                renderWindow(graphics, window, mouseX, mouseY, partialTick);
            }
        }
        renderTabBar(graphics);
        graphics.centeredText(this.font, Component.translatable("hui.desktop.hint"), this.width / 2,
                this.height - 12, HINT_COLOR);
        if (this.menu != null) {
            this.menu.render(graphics, this.font, mouseX, mouseY);
        }
    }

    private void renderWindow(GuiGraphicsExtractor graphics, HuiWindow window, int mouseX, int mouseY,
            float partialTick) {
        boolean isFocused = window == this.focused;
        int x = window.x();
        int y = window.y();
        int width = window.width();
        int height = window.height();

        graphics.fill(x, y, x + width, y + height, WINDOW_BODY);
        graphics.fill(x + 1, y + 1, x + width - 1, y + HuiWindow.TITLE_HEIGHT,
                isFocused ? WINDOW_HEADER_FOCUSED : WINDOW_HEADER);
        graphics.outline(x, y, width, height, isFocused ? BORDER_FOCUSED : BORDER);
        Component title = window.pinned()
                ? Component.translatable("hui.window.title_pinned", window.title())
                : window.title();
        graphics.text(this.font, title, x + 5, y + (HuiWindow.TITLE_HEIGHT - 8) / 2, TITLE_COLOR, true);

        window.content().extract(graphics, mouseX, mouseY, partialTick, window.contentX(), window.contentY());
    }

    private void renderTabBar(GuiGraphicsExtractor graphics) {
        for (int index = 0; index < this.layout.pages.size(); index++) {
            int x = tabX(index);
            int width = tabWidth(index);
            boolean active = index == this.layout.activePage;
            graphics.fill(x, TAB_TOP, x + width, TAB_TOP + TAB_HEIGHT, active ? 0xFF1B2A33 : TAB_COLOR);
            if (active) {
                graphics.outline(x, TAB_TOP, width, TAB_HEIGHT, TAB_ACTIVE_COLOR);
            }
            graphics.text(this.font, pageLabel(index), x + 5, TAB_TOP + (TAB_HEIGHT - 8) / 2,
                    active ? TITLE_COLOR : 0xFFAAB8C2, false);
        }
        int plusX = plusTabX();
        graphics.fill(plusX, TAB_TOP, plusX + 14, TAB_TOP + TAB_HEIGHT, TAB_COLOR);
        graphics.centeredText(this.font, Component.literal("+"), plusX + 7, TAB_TOP + (TAB_HEIGHT - 8) / 2,
                TITLE_COLOR);
    }

    // --- Input -----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        boolean rightClick = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;

        if (this.menu != null) {
            ContextMenu.Entry entry = this.menu.entryAt(mouseX, mouseY);
            this.menu = null;
            if (entry != null && entry.enabled()) {
                entry.action().run();
            }
            return true;
        }

        if (inTabBar(mouseY)) {
            for (int index = 0; index < this.layout.pages.size(); index++) {
                int x = tabX(index);
                if (mouseX >= x && mouseX < x + tabWidth(index)) {
                    if (rightClick) {
                        openPageMenu(index, mouseX, mouseY);
                    } else {
                        switchPage(index);
                    }
                    return true;
                }
            }
            int plusX = plusTabX();
            if (!rightClick && mouseX >= plusX && mouseX < plusX + 14) {
                addPage();
                return true;
            }
        }

        for (int index = this.windows.size() - 1; index >= 0; index--) {
            HuiWindow window = this.windows.get(index);
            if (window.minimized() || !window.contains(mouseX, mouseY)) {
                continue;
            }
            bringToFront(window);
            if (rightClick) {
                openWindowMenu(window, mouseX, mouseY);
                return true;
            }
            if (window.isInTitleBar(mouseX, mouseY)) {
                this.dragging = window;
                this.dragOffsetX = mouseX - window.x();
                this.dragOffsetY = mouseY - window.y();
                return true;
            }
            this.contentDragging = window;
            window.content().mouseClicked(event, window.contentX(), window.contentY(), doubleClick);
            return true;
        }

        if (rightClick) {
            openMainMenu(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging != null) {
            int newX = (int) Math.round(event.x() - this.dragOffsetX);
            int newY = (int) Math.round(event.y() - this.dragOffsetY);
            this.dragging.setPosition(newX, newY);
            clamp(this.dragging);
            return true;
        }
        if (this.contentDragging != null) {
            this.contentDragging.content().mouseDragged(event, this.contentDragging.contentX(),
                    this.contentDragging.contentY(), dragX, dragY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging != null) {
            this.dragging = null;
            return true;
        }
        if (this.contentDragging != null) {
            HuiWindow window = this.contentDragging;
            this.contentDragging = null;
            window.content().mouseReleased(event, window.contentX(), window.contentY());
            return true;
        }
        return false;
    }

    // --- Menus -----------------------------------------------------------------------------

    private void openMainMenu(double mouseX, double mouseY) {
        List<ContextMenu.Entry> entries = new ArrayList<>();
        entries.add(new ContextMenu.Entry(Component.translatable("hui.menu.open_container"), () -> {}, false));
        entries.add(ContextMenu.Entry.of("hui.menu.window_manager", this::openWindowManager));
        entries.add(ContextMenu.Entry.of("hui.menu.new_page", this::addPage));
        entries.add(ContextMenu.Entry.of("hui.menu.demo_window", () -> addWindow(HuiWindowTypes.DEMO)));
        entries.add(ContextMenu.Entry.of("hui.menu.open_inventory", this::ensureInventoryWindow));
        entries.add(new ContextMenu.Entry(
                Component.translatable("hui.menu.toggle_inventory", onOff(this.layout.huiInventory)),
                this::toggleHuiInventory, true));
        entries.add(ContextMenu.Entry.of("hui.menu.hui_config", this::openHuiConfig));
        this.menu = new ContextMenu(this.font, (int) mouseX, (int) mouseY, this.width, this.height, entries);
    }

    private void openWindowMenu(HuiWindow window, double mouseX, double mouseY) {
        List<ContextMenu.Entry> entries = new ArrayList<>();
        entries.add(ContextMenu.Entry.of("hui.menu.minimize_window", () -> minimizeWindow(window)));
        entries.add(ContextMenu.Entry.of("hui.menu.close_window", () -> closeWindow(window)));
        entries.add(ContextMenu.Entry.of(
                window.pinned() ? "hui.menu.unpin_window" : "hui.menu.pin_window",
                () -> window.setPinned(!window.pinned())));
        entries.add(ContextMenu.Entry.of("hui.menu.window_properties", () -> openProperties(window)));
        this.menu = new ContextMenu(this.font, (int) mouseX, (int) mouseY, this.width, this.height, entries);
    }

    private void openPageMenu(int index, double mouseX, double mouseY) {
        boolean empty = index == this.layout.activePage
                ? this.windows.isEmpty()
                : this.layout.pages.get(index).windows.isEmpty();
        boolean canRemove = this.layout.pages.size() > 1 && empty;
        List<ContextMenu.Entry> entries = new ArrayList<>();
        entries.add(new ContextMenu.Entry(Component.translatable("hui.menu.delete_page"),
                () -> {
                    if (index == this.layout.activePage) {
                        removeActivePageIfEmpty();
                    } else {
                        this.layout.pages.remove(index);
                        this.layout.activePage = Math.max(0, Math.min(this.layout.pages.size() - 1,
                                this.layout.activePage));
                    }
                }, canRemove));
        this.menu = new ContextMenu(this.font, (int) mouseX, (int) mouseY, this.width, this.height, entries);
    }

    private void openProperties(HuiWindow window) {
        List<Component> lines = List.of(
                Component.translatable("hui.properties.type", window.typeId().toString()),
                Component.translatable("hui.properties.pinned", onOff(window.pinned())),
                Component.translatable("hui.properties.size", window.width(), window.height()),
                Component.translatable("hui.properties.position", window.x(), window.y()));
        HuiWindow properties = new HuiWindow(UUID.randomUUID().toString(), PROPERTIES_WINDOW,
                Component.translatable("hui.window.properties"), TextWindowContent.properties(lines));
        int cascade = this.windows.size() % 6;
        properties.setPosition(Math.max(0, window.x() + 24 + cascade * 6), Math.max(TAB_TOP + TAB_HEIGHT,
                window.y() + 24 + cascade * 6));
        clamp(properties);
        properties.content().init(this.width, this.height);
        this.windows.add(properties);
        bringToFront(properties);
    }

    private void openWindowManager() {
        HuiScreens.open(this.minecraft, new HuiWindowManagerScreen(this));
    }

    private void openHuiConfig() {
        HuiScreens.open(this.minecraft, new HuiConfigScreen());
    }

    private void toggleHuiInventory() {
        this.layout.huiInventory = !this.layout.huiInventory;
        DesktopLayoutStore.save(this.layout);
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "hui.value.on" : "hui.value.off");
    }

    // --- Lifecycle -------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        for (HuiWindow window : this.windows) {
            if (!window.minimized()) {
                window.content().tick();
            }
        }
    }

    @Override
    public void onClose() {
        captureActivePage();
        for (DesktopLayout.PageLayout page : this.layout.pages) {
            page.windows.removeIf(window -> !window.pinned);
        }
        this.layout.removeEmptyPages();
        DesktopLayoutStore.save(this.layout);
        super.onClose();
    }

    @Override
    public void removed() {
        if (this.removedOnce) {
            return;
        }
        this.removedOnce = true;
        for (HuiWindow window : this.windows) {
            window.content().removed();
        }
        this.windows.clear();
        this.focused = null;
        this.dragging = null;
        this.contentDragging = null;
    }
}
