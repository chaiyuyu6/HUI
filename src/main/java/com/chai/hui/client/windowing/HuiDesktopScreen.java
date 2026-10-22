package com.chai.hui.client.windowing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.chai.hui.HuiMod;
import com.chai.hui.client.screen.HuiConfigScreen;
import com.chai.hui.client.screen.HuiScreens;
import com.chai.hui.client.ui.ContextMenu;
import com.chai.hui.client.windowing.ui.PanelWidget;
import com.chai.hui.client.windowing.ui.TextInputWidget;
import com.chai.hui.client.windowing.ui.Ui;
import com.chai.hui.client.windowing.ui.UiPanelContent;
import com.chai.hui.network.CloseMenuPayload;
import com.chai.hui.network.CreativeInsertPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
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

    private final @Nullable Identifier requestedInventoryType;

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
    private @Nullable AbstractContainerScreen<?> pendingContainerScreen;
    private @Nullable net.minecraft.core.BlockPos pendingContainerPos;
    private boolean pendingContainerPersist = true;
    private final java.util.Map<net.minecraft.core.BlockPos, HuiWindow> transientBlockWindows = new java.util.HashMap<>();
    private final java.util.Map<net.minecraft.core.BlockPos, HuiWindow> blockUiWindows = new java.util.HashMap<>();
    private @Nullable net.minecraft.core.BlockPos pendingBlockUiPos;
    private @Nullable Component pendingBlockUiTitle;
    private @Nullable WindowContent pendingBlockUiContent;
    private final java.util.Map<net.minecraft.core.BlockPos, HuiWindow> pendingRestores = new java.util.HashMap<>();
    private @Nullable MenuWindowContent activeContent;
    private @Nullable WindowContent overlayContent;

    private HuiDesktopScreen(@Nullable Identifier requestedInventoryType) {
        super(Component.translatable("hui.desktop.title"));
        this.requestedInventoryType = requestedInventoryType;
    }

    /** Opens the desktop and makes sure the current page has an inventory window matching the game mode. */
    public static HuiDesktopScreen openWithInventory() {
        return new HuiDesktopScreen(HuiWindowTypes.inventoryTypeForCurrentGameMode());
    }

    /** Opens the desktop with the given inventory window type (e.g. the creative inventory). */
    public static HuiDesktopScreen openWithType(Identifier type) {
        return new HuiDesktopScreen(type);
    }

    /** Opens a fresh desktop that hosts the given container screen as a window (used after the previous menu closed). */
    public static HuiDesktopScreen openWithContainer(AbstractContainerScreen<?> screen,
            @Nullable net.minecraft.core.BlockPos pos) {
        HuiDesktopScreen desktop = new HuiDesktopScreen(null);
        desktop.queueContainerWindow(screen, pos);
        return desktop;
    }

    /** Same as {@link #openWithContainer} but the window is session only (not written to the layout file). */
    public static HuiDesktopScreen openWithTransientContainer(AbstractContainerScreen<?> screen,
            @Nullable net.minecraft.core.BlockPos pos) {
        HuiDesktopScreen desktop = new HuiDesktopScreen(null);
        desktop.queueContainerWindow(screen, pos, false);
        return desktop;
    }


    /** Opens the desktop restoring the pinned windows only (used by the F8 shortcut). */
    public static HuiDesktopScreen openWithoutInventory() {
        return new HuiDesktopScreen(null);
    }

    /** Queues a container screen (chest, furnace, ...) to be added as a window on the next init. */
    public void queueContainerWindow(AbstractContainerScreen<?> screen, @Nullable net.minecraft.core.BlockPos pos) {
        queueContainerWindow(screen, pos, true);
    }

    public void queueContainerWindow(AbstractContainerScreen<?> screen, @Nullable net.minecraft.core.BlockPos pos,
            boolean persist) {
        this.pendingContainerScreen = screen;
        this.pendingContainerPos = pos;
        this.pendingContainerPersist = persist;
    }

    /** Opens the desktop hosting a HUI block UI window (registered block entity without a vanilla GUI). */
    public static HuiDesktopScreen openWithBlockUi(net.minecraft.core.BlockPos pos, Component title,
            WindowContent content) {
        HuiDesktopScreen desktop = new HuiDesktopScreen(null);
        desktop.pendingBlockUiPos = pos;
        desktop.pendingBlockUiTitle = title;
        desktop.pendingBlockUiContent = content;
        return desktop;
    }

    /**
     * Adds (or refreshes) the HUI window for a registered block entity. Windows created through the block UI channel are
     * ephemeral: they live as long as the desktop and are not restored from the layout file.
     */
    public void addBlockUiWindow(net.minecraft.core.BlockPos pos, Component title, WindowContent content) {
        HuiWindow existing = this.blockUiWindows.get(pos);
        if (existing != null) {
            existing.setTitle(title);
            existing.setContent(content);
            content.init(this.width, this.height);
            bringToFront(existing);
            return;
        }
        HuiWindow window = new HuiWindow(UUID.randomUUID().toString(),
                Identifier.fromNamespaceAndPath("hui", "block_ui"), title, content);
        window.setEphemeral(true);
        int cascade = this.blockUiWindows.size() % 6;
        window.setPosition(Math.max(0, (this.width - window.width()) / 2 - 60 + cascade * 18),
                Math.max(TAB_HEIGHT + 8, (this.height - window.height()) / 2 - 40 + cascade * 18));
        clamp(window);
        content.init(this.width, this.height);
        this.windows.add(window);
        this.blockUiWindows.put(pos, window);
        pairMachineWithInventory(window);
        bringToFront(window);
    }

    // --- Layout loading / page building ----------------------------------------------------

    @Override
    protected void init() {

        if (!this.built) {
            this.layout = DesktopLayoutStore.load();
            this.layout.ensurePages();
            if (this.requestedInventoryType != null) {
                // Jump to the desktop page that already hosts this inventory kind instead of reopening whichever page
                // was active last time.
                int page = findPageWithInventoryType(this.requestedInventoryType);
                if (page >= 0) {
                    this.layout.activePage = page;
                }
            }
            rebuildActivePage();
            this.built = true;
            if (this.requestedInventoryType != null) {
                ensureInventoryWindow(this.requestedInventoryType);
            }
        }
        if (this.pendingContainerScreen != null) {
            boolean persist = this.pendingContainerPersist;
            this.pendingContainerPersist = true;
            adoptContainerWindow(this.pendingContainerScreen, this.pendingContainerPos, persist);
            this.pendingContainerScreen = null;
            this.pendingContainerPos = null;
        }
        if (this.pendingBlockUiContent != null && this.pendingBlockUiPos != null && this.pendingBlockUiTitle != null) {
            addBlockUiWindow(this.pendingBlockUiPos, this.pendingBlockUiTitle, this.pendingBlockUiContent);
            this.pendingBlockUiPos = null;
            this.pendingBlockUiTitle = null;
            this.pendingBlockUiContent = null;
        }
        ensureMachineCompanions();
        clampAllWindows();
        for (HuiWindow window : this.windows) {
            window.content().init(this.width, this.height);
        }
    }

    /** Makes sure every machine window on the page is paired with the inventory companion. */
    private void ensureMachineCompanions() {
        for (HuiWindow window : new ArrayList<>(this.windows)) {
            boolean machine = window.typeId().equals(HuiWindowTypes.BLOCK_ENTITY)
                    || window.typeId().equals(Identifier.fromNamespaceAndPath("hui", "block_ui"));
            if (machine && findInventoryWindow() == null) {
                pairMachineWithInventory(window);
            }
        }
    }

    /**
     * Wraps an already opened container screen into an ephemeral window and makes sure the page also has an inventory
     * window next to it (the cropped player inventory or the creative inventory), so items can be moved between the
     * container and the player without the container window showing the player inventory itself.
     */
    private void adoptContainerWindow(AbstractContainerScreen<?> screen,
            @Nullable net.minecraft.core.BlockPos sourcePos) {
        adoptContainerWindow(screen, sourcePos, true);
    }

    private void adoptContainerWindow(AbstractContainerScreen<?> screen,
            @Nullable net.minecraft.core.BlockPos sourcePos, boolean persist) {
        // The window content only stores the source position when the window should be restorable across sessions.
        net.minecraft.core.BlockPos contentPos = persist ? sourcePos : null;
        if (sourcePos != null && this.minecraft != null && this.minecraft.level != null) {
            net.minecraft.world.level.block.entity.BlockEntity blockEntity = this.minecraft.level
                    .getBlockEntity(sourcePos);
            if (blockEntity != null && com.chai.hui.client.ui.HuiUiRegistry.isBlockRegistered(blockEntity.getType())) {
                // Registered block entity: HUI owns the UI. The vanilla menu is closed and the block UI channel provides
                // the data for the registered HUI UI.
                ClientPacketDistributor.sendToServer(new CloseMenuPayload(screen.getMenu().containerId));
                if (this.minecraft.player != null) {
                    this.minecraft.player.containerMenu = this.minecraft.player.inventoryMenu;
                }
                ClientPacketDistributor.sendToServer(new com.chai.hui.network.OpenBlockUiPayload(sourcePos));
                return;
            }
        }
        if (sourcePos != null && !this.pendingRestores.containsKey(sourcePos) && focusExistingBlockUi(sourcePos)) {
            // This block entity UI is already open somewhere on the desktop: close the freshly opened menu and just
            // bring the existing window to the front instead of opening a duplicate.
            ClientPacketDistributor.sendToServer(new CloseMenuPayload(screen.getMenu().containerId));
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.containerMenu = this.minecraft.player.inventoryMenu;
            }
            return;
        }

        WindowContent content;
        com.chai.hui.client.ui.HuiUiRegistry.MenuUiFactory menuFactory =
                com.chai.hui.client.ui.HuiUiRegistry.menuUi(screen.getMenu().getType());
        if (menuFactory != null) {
            net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
            content = new com.chai.hui.client.ui.framework.UiWindowContent(
                    menuFactory.build(new com.chai.hui.client.ui.MenuUiContext(menu, screen.getTitle(),
                            () -> com.chai.hui.client.ui.MenuUiContext.activateMenu(menu))));
        } else {
            // HUI style slots plus HUI re-implementations of the vanilla widgets (furnace flame/arrow, brewing
            // bubbles, stone cutter candidates, ...).
            net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
            if (menu instanceof net.minecraft.world.inventory.StonecutterMenu) {
                content = new VanillaMenuUiContent(menu, screen.getTitle(),
                        slot -> !(slot.container instanceof net.minecraft.world.entity.player.Inventory), contentPos,
                        MachineDecorations::stonecutterSlotPosition, 186, 78)
                        .withDecoration(MachineDecorations.forMenu(menu));
            } else if (menu instanceof net.minecraft.world.inventory.CartographyTableMenu) {
                content = new VanillaMenuUiContent(menu, screen.getTitle(),
                        slot -> !(slot.container instanceof net.minecraft.world.entity.player.Inventory), contentPos,
                        MachineDecorations::cartographySlotPosition, 186, 82)
                        .withDecoration(MachineDecorations.forMenu(menu));
            } else if (menu instanceof net.minecraft.world.inventory.EnchantmentMenu) {
                content = new VanillaMenuUiContent(menu, screen.getTitle(),
                        slot -> !(slot.container instanceof net.minecraft.world.entity.player.Inventory), contentPos,
                        MachineDecorations::enchantingSlotPosition, 186, 82)
                        .withDecoration(MachineDecorations.forMenu(menu));
            } else {
                content = new VanillaMenuUiContent(menu, screen.getTitle(),
                        slot -> !(slot.container instanceof net.minecraft.world.entity.player.Inventory), contentPos)
                        .withDecoration(MachineDecorations.forMenu(menu));
            }
        }
        HuiWindow restored = sourcePos == null ? null : this.pendingRestores.remove(sourcePos);
        if (restored != null) {
            restored.setContent(content);
            restored.setTitle(screen.getTitle());
            clamp(restored);
            pairMachineWithInventory(restored);
            // Do not steal focus from a window the player just opened (restored containers arrive a bit later).
            if (this.focused == null) {
                bringToFront(restored);
            }
            return;
        }
        HuiWindow existingInventory = findInventoryWindow();
        if (existingInventory != null && existingInventory.pinned()
                && !existingInventory.typeId().equals(HuiWindowTypes.DEFAULT_INVENTORY)) {
            // Only a pinned inventory of a different kind needs its own page: the machine then gets a new desktop page
            // (and its own default inventory companion) while the pinned window stays where it is.
            addPage();
        }
        HuiWindow window = new HuiWindow(UUID.randomUUID().toString(), HuiWindowTypes.BLOCK_ENTITY, screen.getTitle(),
                content);
        window.setEphemeral(true);

        this.windows.add(window);
        if (!persist && sourcePos != null) {
            trackTransientBlockWindow(sourcePos, window);
        }
        pairMachineWithInventory(window);
        bringToFront(window);
    }

    /**
     * Every machine window opened through HUI is paired with the cropped default inventory window, so items can be moved
     * between the machine and the player without the machine window showing the player inventory itself. In creative
     * mode the creative inventory window takes that place (the default inventory window is survival only).
     */
    /**
     * @return true when a window for this block position is already open (current page or another page); focuses it.
     */
    private boolean focusExistingBlockUi(net.minecraft.core.BlockPos pos) {
        HuiWindow transientWindow = this.transientBlockWindows.get(pos);
        if (transientWindow != null && this.windows.contains(transientWindow)) {
            com.chai.hui.HuiMod.LOGGER.info("[HUI] duplicate block ui at {} focused", pos);
            bringToFront(transientWindow);
            return true;
        }
        for (HuiWindow window : this.windows) {
            if (window.content() instanceof MenuWindowContent content && pos.equals(content.sourcePos())) {
                com.chai.hui.HuiMod.LOGGER.info("[HUI] duplicate block ui at {} focused", pos);
                bringToFront(window);
                return true;
            }
        }
        long wanted = pos.asLong();
        for (int pageIndex = 0; pageIndex < this.layout.pages.size(); pageIndex++) {
            if (pageIndex == this.layout.activePage) {
                continue;
            }
            for (DesktopLayout.WindowLayout stored : this.layout.pages.get(pageIndex).windows) {
                if (stored.pos == wanted) {
                    switchPage(pageIndex);
                    for (HuiWindow window : this.windows) {
                        if (window.content() instanceof MenuWindowContent content && pos.equals(content.sourcePos())) {
                            bringToFront(window);
                            return true;
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void pairMachineWithInventory(HuiWindow machine) {
        // Machine windows always pair with the cropped default inventory window, in survival and creative alike.
        Identifier desiredInventory = HuiWindowTypes.DEFAULT_INVENTORY;
        HuiWindow companion = findInventoryWindow();
        if (companion != null && !companion.typeId().equals(desiredInventory)) {
            closeWindow(companion);
            companion = null;
        }
        boolean createdCompanion = companion == null;
        if (companion == null) {
            companion = addWindow(desiredInventory);
        }
        if (companion == null) {
            machine.setPosition((this.width - machine.width()) / 2, (this.height - machine.height()) / 2);
            clamp(machine);
            return;
        }

        int gap = 8;
        int top = TAB_TOP + TAB_HEIGHT + 4;
        if (createdCompanion) {
            int totalWidth = machine.width() + gap + companion.width();
            int startX = Math.max(4, (this.width - totalWidth) / 2);
            int y = Math.max(top, (this.height - Math.max(machine.height(), companion.height())) / 2);
            companion.setPosition(startX + machine.width() + gap, y);
            machine.setPosition(startX, y);
        } else {
            int x = companion.x() - machine.width() - gap;
            int y = companion.y();
            if (x < 4) {
                x = companion.x() + companion.width() + gap;
                if (x + machine.width() > this.width - 4) {
                    x = Math.max(4, (this.width - machine.width()) / 2);
                    y = Math.max(top, companion.y() - machine.height() - gap);
                }
            }
            machine.setPosition(x, y);
        }
        clamp(companion);
        clamp(machine);
    }

    private void trackTransientBlockWindow(net.minecraft.core.BlockPos pos, HuiWindow window) {
        if (pos != null) {
            this.transientBlockWindows.put(pos, window);
        }
    }

    private void ensureInventoryCompanion() {
        Identifier desired = HuiWindowTypes.DEFAULT_INVENTORY;
        HuiWindow current = findInventoryWindow();
        if (current != null && current.typeId().equals(desired)) {
            return;
        }
        if (current != null) {
            closeWindow(current);
        }
        addWindow(desired);
    }

    /** Removes a restore placeholder whose container could not be opened anymore. */
    public void dropPendingRestore(net.minecraft.core.BlockPos pos) {
        HuiWindow placeholder = this.pendingRestores.remove(pos);
        if (placeholder != null) {
            closeWindow(placeholder);
        }
    }

    private @Nullable HuiWindow findInventoryWindow() {
        for (HuiWindow window : this.windows) {
            if (HuiWindowTypes.isInventoryType(window.typeId())) {
                return window;
            }
        }
        return null;
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
        if (typeId.equals(HuiWindowTypes.BLOCK_ENTITY)) {
            return restoreBlockEntityWindow(stored);
        }
        WindowType type = HuiWindowTypes.get(typeId);
        if (type == null) {
            HuiMod.LOGGER.warn("Skipping window of unknown type {}", typeId);
            return null;
        }
        Component title;
        if (!stored.title.isEmpty()) {
            title = Component.literal(stored.title);
        } else if (!stored.titleKey.isEmpty()) {
            title = Component.translatable(stored.titleKey);
        } else {
            title = type.title().get();
        }
        HuiWindow window = new HuiWindow(stored.id, typeId, title, type.createContent());
        window.setBounds(stored.x, stored.y, window.width(), window.height());
        window.setMinimized(stored.minimized);
        window.setPinned(stored.pinned);
        return window;
    }

    /**
     * Restores a pinned container window: the menu itself cannot be restored without the server, so a placeholder window
     * is created and an open request is queued; when the container screen arrives it replaces the placeholder content.
     */
    private @Nullable HuiWindow restoreBlockEntityWindow(DesktopLayout.WindowLayout stored) {
        if (stored.pos == 0L || this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        String dimension = this.minecraft.level.dimension().identifier().toString();
        if (!stored.dimension.isEmpty() && !stored.dimension.equals(dimension)) {
            return null;
        }
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(stored.pos);
        Component title = Component.translatable("hui.window.block_entity.opening");
        HuiWindow placeholder = new HuiWindow(stored.id, HuiWindowTypes.BLOCK_ENTITY, title,
                new TextWindowContent(java.util.List.of(title), Math.max(80, stored.width - 8), 24));
        placeholder.setBounds(stored.x, stored.y, Math.max(80, stored.width), Math.max(40, stored.height));
        placeholder.setMinimized(stored.minimized);
        placeholder.setPinned(stored.pinned);
        placeholder.setEphemeral(true);
        this.pendingRestores.put(pos, placeholder);
        HuiRemoteContainers.request(pos);
        return placeholder;
    }
    private void captureActivePage() {
        DesktopLayout.PageLayout page = this.layout.activePage();
        page.windows.clear();
        for (HuiWindow window : this.windows) {
            MenuWindowContent embedded = window.content() instanceof MenuWindowContent content ? content : null;
            boolean restorableBlockEntity = window.ephemeral() && embedded != null && embedded.sourcePos() != null;
            if (window.ephemeral() && !restorableBlockEntity) {
                continue;
            }
            DesktopLayout.WindowLayout stored = new DesktopLayout.WindowLayout();
            stored.id = window.uid();
            stored.type = window.typeId().toString();
            stored.titleKey = "";
            stored.title = window.customTitle() ? window.title().getString() : "";
            stored.x = window.x();
            stored.y = window.y();
            stored.width = window.width();
            stored.height = window.height();
            stored.minimized = window.minimized();
            stored.pinned = window.pinned();
            if (restorableBlockEntity) {
                stored.pos = embedded.sourcePos().asLong();
                stored.dimension = this.minecraft == null || this.minecraft.level == null ? ""
                        : this.minecraft.level.dimension().identifier().toString();
            }
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

    /**
     * Opens an inventory style window from the desktop menus. When the current page already has an inventory window a
     * new desktop page is created first, so the existing inventory window stays on the previous page.
     */
    /** @return the index of the page that already has a window of the given inventory type, or -1. */
    private int findPageWithInventoryType(Identifier type) {
        String wanted = type.toString();
        for (int index = 0; index < this.layout.pages.size(); index++) {
            for (DesktopLayout.WindowLayout stored : this.layout.pages.get(index).windows) {
                if (wanted.equals(stored.type)) {
                    return index;
                }
            }
        }
        return -1;
    }

    private void openInventoryWindow(Identifier type) {
        for (HuiWindow window : new ArrayList<>(this.windows)) {
            if (!HuiWindowTypes.isInventoryType(window.typeId())) {
                continue;
            }
            if (window.typeId().equals(type)) {
                // Same inventory kind: reuse the window that is already on this page.
                bringToFront(window);
                return;
            }
            // A different inventory kind: give it its own desktop page.
            addPage();
            break;
        }
        addWindow(type);
    }

    private void ensureInventoryWindow(Identifier type) {
        for (HuiWindow window : new ArrayList<>(this.windows)) {
            if (!HuiWindowTypes.isInventoryType(window.typeId())) {
                continue;
            }
            if (window.typeId().equals(type)) {
                // A restored inventory window of the right type stays where it was saved.
                return;
            }
            closeWindow(window);
        }
        addWindow(type);
    }

    private @Nullable HuiWindow addWindow(Identifier typeId) {
        WindowType type = HuiWindowTypes.get(typeId);
        if (type == null) {
            return null;
        }
        HuiWindow window = new HuiWindow(UUID.randomUUID().toString(), typeId,
                Component.translatable(type.titleKey()), type.createContent());
        if (HuiWindowTypes.isInventoryType(typeId)) {
            // The inventory window is the "home" window: open it centered instead of cascaded.
            window.setPosition((this.width - window.width()) / 2, (this.height - window.height()) / 2);
        } else {
            int cascade = this.windows.size() % 6;
            window.setPosition(Math.max(0, (this.width - window.width()) / 2 - 60 + cascade * 18),
                    Math.max(TAB_HEIGHT + 8, (this.height - window.height()) / 2 - 40 + cascade * 18));
        }
        clamp(window);
        window.content().init(this.width, this.height);
        this.windows.add(window);
        bringToFront(window);
        return window;
    }

    private void closeWindow(HuiWindow window) {
        releaseBlockEntityMenu(window);
        window.content().removed();
        this.windows.remove(window);
        this.transientBlockWindows.values().remove(window);
        if (this.overlayContent == window.content()) {
            this.overlayContent = null;
        }
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

    /** Sends the server the request to close this window's container menu (if it has one). */
    private void releaseBlockEntityMenu(HuiWindow window) {
        if (!(window.content() instanceof MenuWindowContent embedded) || embedded.menuContainerId() == 0) {
            return;
        }
        ClientPacketDistributor.sendToServer(new CloseMenuPayload(embedded.menuContainerId()));
        if (this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.containerMenu.containerId == embedded.menuContainerId()) {
            this.minecraft.player.containerMenu = this.minecraft.player.inventoryMenu;
        }
    }

    /**
     * Makes the clicked window the active menu. The previous window's carried stack is only cleared locally when its
     * menu is server backed (the server moves the stack during activation); the client-only creative inventory keeps its
     * carried stack, otherwise the item would disappear when moving the mouse over another window.
     */
    private void activateContent(MenuWindowContent content) {
        if (this.activeContent != null && this.activeContent != content && !this.activeContent.serverBacked()
                && !this.activeContent.screenMenu().getCarried().isEmpty()) {
            // A creative item is on the cursor: keep that window active so the stack stays visible and does not
            // vanish when the mouse passes over another window.
            return;
        }
        if (this.activeContent != content && this.activeContent != null && this.activeContent.serverBacked()) {
            this.activeContent.screenMenu().setCarried(net.minecraft.world.item.ItemStack.EMPTY);
        }
        this.activeContent = content;
        this.overlayContent = content;
        content.activate();
    }

    /** Closes the block entity window whose menu is no longer valid (server side close notification). */
    public void closeBlockEntityWindow(int containerId) {
        for (HuiWindow window : new ArrayList<>(this.windows)) {
            if (window.typeId().equals(HuiWindowTypes.BLOCK_ENTITY)
                    && window.content() instanceof MenuWindowContent embedded
                    && embedded.menuContainerId() == containerId) {
                closeWindow(window);
            }
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
        // The carried stack and tooltip must be above every window and the context menu.
        WindowContent overlay = this.overlayContent;
        if (overlay == null && this.focused != null) {
            overlay = this.focused.content();
        }
        if (overlay != null) {
            for (HuiWindow window : this.windows) {
                if (!window.minimized() && window.content() == overlay) {
                    overlay.extractOverlay(graphics, mouseX, mouseY, partialTick, window.contentX(),
                            window.contentY());
                    break;
                }
            }
        }
    }

    private void renderWindow(GuiGraphicsExtractor graphics, HuiWindow window, int mouseX, int mouseY,
            float partialTick) {
        boolean isFocused = window == this.focused;
        int x = window.x();
        int y = window.y();
        int width = window.width();
        int height = window.height();

        if (window.content().paintsBodyBackground()) {
            graphics.fill(x, y, x + width, y + height, WINDOW_BODY);
        }
        if (window.content().paintsTitleBar()) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + HuiWindow.TITLE_HEIGHT,
                    isFocused ? WINDOW_HEADER_FOCUSED : WINDOW_HEADER);
        }
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
            this.overlayContent = window.content();
            if (rightClick && window.isInTitleBar(mouseX, mouseY)) {
                openWindowMenu(window, mouseX, mouseY);
                return true;
            }
            if (!rightClick && tryCreativeInsert(window, mouseX, mouseY)) {
                return true;
            }
            if (window.isInTitleBar(mouseX, mouseY)) {
                this.dragging = window;
                this.dragOffsetX = mouseX - window.x();
                this.dragOffsetY = mouseY - window.y();
                return true;
            }
            this.contentDragging = window;
            if (window.content() instanceof MenuWindowContent embedded) {
                activateContent(embedded);
            }
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
    public void mouseMoved(double mouseX, double mouseY) {
        if (this.contentDragging != null) {
            // Do not switch the active menu while the player is dragging a stack between windows.
            return;
        }
        for (int index = this.windows.size() - 1; index >= 0; index--) {
            HuiWindow window = this.windows.get(index);
            if (!window.minimized() && window.contains(mouseX, mouseY)) {
                if (window.content() instanceof MenuWindowContent embedded) {
                    activateContent(embedded);
                } else {
                    this.overlayContent = window.content();
                }
                return;
            }
        }
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
            HuiWindow source = this.contentDragging;
            this.contentDragging = null;
            if (source.content() instanceof MenuWindowContent sourceContent
                    && sourceContent.serverBacked() && !sourceContent.screenMenu().getCarried().isEmpty()) {
                HuiWindow target = windowAt(event.x(), event.y());
                if (target != null && target != source
                        && target.content() instanceof MenuWindowContent targetContent
                        && targetContent.serverBacked()) {
                    net.minecraft.world.item.ItemStack carried = sourceContent.screenMenu().getCarried().copy();
                    activateContent(targetContent);
                    // Keep the stack visible on the target window even if the click below is rejected by the menu.
                    targetContent.screenMenu().setCarried(carried);
                    if (targetContent.slotIndexAt((int) event.x(), (int) event.y(), target.contentX(),
                            target.contentY()) >= 0) {
                        targetContent.mouseClicked(event, target.contentX(), target.contentY(), false);
                    }
                    // Force a full state sync: if the target menu rejected the item it comes back to the cursor instead
                    // of disappearing until the next click.
                    targetContent.activate();
                    return true;
                }
            }
            source.content().mouseReleased(event, source.contentX(), source.contentY());
            return true;
        }
        return false;
    }

    /**
     * Creative mode has a client only inventory menu, so a stack taken from it cannot be placed by a vanilla click.
     * When such a stack is on the cursor and the player clicks a slot of a server backed container window, ask the
     * server to insert a copy into that slot.
     */
    private boolean tryCreativeInsert(HuiWindow targetWindow, double mouseX, double mouseY) {
        if (!(targetWindow.content() instanceof MenuWindowContent target) || !target.serverBacked()) {
            return false;
        }
        MenuWindowContent source = null;
        if (this.activeContent != null && !this.activeContent.serverBacked()
                && !this.activeContent.screenMenu().getCarried().isEmpty()) {
            source = this.activeContent;
        }
        if (source == null) {
            return false;
        }
        int slotIndex = target.slotIndexAt((int) mouseX, (int) mouseY, targetWindow.contentX(),
                targetWindow.contentY());
        if (slotIndex < 0) {
            return false;
        }
        net.minecraft.world.item.ItemStack stack = source.screenMenu().getCarried().copy();
        source.screenMenu().setCarried(net.minecraft.world.item.ItemStack.EMPTY);
        ClientPacketDistributor.sendToServer(
                new CreativeInsertPayload(target.menuContainerId(), slotIndex, stack));
        target.activate();
        return true;
    }

    /**
     * A server backed carried stack (e.g. picked up from a stone cutter) must be handed over to a client only window
     * (creative inventory) before the click is forwarded: the server menu drops its copy and the client window receives
     * it as its own carried stack, otherwise the stack would exist twice (slot + still following the cursor).
     */
    private void handOverServerCarriedToClientOnlyWindow() {
        net.minecraft.client.Minecraft minecraft = this.minecraft;
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        net.minecraft.world.inventory.AbstractContainerMenu menu = minecraft.player.containerMenu;
        if (menu == minecraft.player.inventoryMenu || menu.getCarried().isEmpty()) {
            return;
        }
        net.minecraft.world.item.ItemStack carried = menu.getCarried().copy();
        ClientPacketDistributor.sendToServer(new com.chai.hui.network.ClearCarriedPayload(menu.containerId));
        menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
        minecraft.player.inventoryMenu.setCarried(carried);
    }

    private @Nullable HuiWindow windowAt(double mouseX, double mouseY) {
        for (int index = this.windows.size() - 1; index >= 0; index--) {
            HuiWindow window = this.windows.get(index);
            if (!window.minimized() && window.contains(mouseX, mouseY)) {
                return window;
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (int index = this.windows.size() - 1; index >= 0; index--) {
            HuiWindow window = this.windows.get(index);
            if (!window.minimized() && window.contains(mouseX, mouseY)) {
                return window.content().mouseScrolled(mouseX, mouseY, scrollX, scrollY, window.contentX(),
                        window.contentY());
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.menu != null && event.key() == InputConstants.KEY_ESCAPE) {
            this.menu = null;
            return true;
        }
        // Screen handles ESC (close desktop) and other desktop level keys first.
        if (super.keyPressed(event)) {
            return true;
        }
        HuiWindow window = this.focused;
        if (window != null && !window.minimized() && window.content().keyPressed(event)) {
            return true;
        }
        if (this.minecraft != null
                && this.minecraft.options.keyInventory.isActiveAndMatches(InputConstants.getKey(event))) {
            this.onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        HuiWindow window = this.focused;
        return window != null && !window.minimized() && window.content().charTyped(event);
    }

    // --- Menus -----------------------------------------------------------------------------

    private void openMainMenu(double mouseX, double mouseY) {
        List<ContextMenu.Entry> entries = new ArrayList<>();
        entries.add(ContextMenu.Entry.of("hui.menu.open_container", this::openContainerList));
        entries.add(ContextMenu.Entry.of("hui.menu.window_manager", this::openWindowManager));
        entries.add(ContextMenu.Entry.of("hui.menu.new_page", this::addPage));
        entries.add(ContextMenu.Entry.of("hui.menu.demo_window", () -> addWindow(HuiWindowTypes.DEMO)));
        entries.add(ContextMenu.Entry.of("hui.menu.open_inventory",
                () -> openInventoryWindow(HuiWindowTypes.inventoryTypeForCurrentGameMode())));
        entries.add(ContextMenu.Entry.of("hui.menu.open_default_inventory",
                () -> openInventoryWindow(HuiWindowTypes.DEFAULT_INVENTORY)));
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
        entries.add(ContextMenu.Entry.of("hui.menu.rename_window", () -> openRenameWindow(window)));
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

    /** Opens a small Ui based window with a text input to rename the given window. */
    private void openRenameWindow(HuiWindow target) {
        TextInputWidget input = Ui.textInput(target.title().getString(), 32, value -> {}, () -> {});
        HuiWindow rename = new HuiWindow(UUID.randomUUID().toString(), PROPERTIES_WINDOW,
                Component.translatable("hui.rename_window.title"), new TextWindowContent(java.util.List.of(), 10, 10));
        PanelWidget root = Ui.column().padding(6).gap(4)
                .child(Ui.mutedLabel(() -> Component.translatable("hui.rename_window.label")))
                .child(input)
                .child(Ui.row().gap(4)
                        .child(Ui.button(Component.translatable("hui.rename_window.confirm"), () -> {
                            String value = input.value().trim();
                            target.setCustomTitle(Component.literal(value.isEmpty() ? target.title().getString() : value));
                            closeWindow(rename);
                        }))
                        .child(Ui.button(Component.translatable("hui.rename_window.cancel"), () -> closeWindow(rename))));
        rename.setContent(new UiPanelContent(root));
        rename.content().init(this.width, this.height);
        rename.setPosition(Math.max(4, target.x() + 24), Math.max(TAB_TOP + TAB_HEIGHT, target.y() + 24));
        clamp(rename);
        this.windows.add(rename);
        bringToFront(rename);
    }
    private void openWindowManager() {
        HuiScreens.open(this.minecraft, new HuiWindowManagerScreen(this));
    }

    private void openContainerList() {
        HuiScreens.open(this.minecraft, new HuiContainerListScreen());
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
        // Release every block entity menu before the desktop goes away.
        for (HuiWindow window : new ArrayList<>(this.windows)) {
            releaseBlockEntityMenu(window);
        }
        captureActivePage();
        // Every page keeps its windows between sessions: inventory windows are regular windows again and restorable
        // machine windows come back when their block is still there.
        this.layout.removeEmptyPages();
        DesktopLayoutStore.save(this.layout);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        // Keep the world running while the HUI desktop is open (like a vanilla container screen).
        return false;
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