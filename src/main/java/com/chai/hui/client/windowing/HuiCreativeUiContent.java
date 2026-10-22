package com.chai.hui.client.windowing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chai.hui.client.ui.framework.Ui;
import com.chai.hui.client.ui.framework.UiNode;
import com.chai.hui.client.ui.framework.UiStyle;
import com.chai.hui.client.ui.framework.UiWindowContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * HUI's own creative inventory, built with the HUI UI framework.
 *
 * <p>Tabs come from {@link CreativeModeTabs#allTabs()}, so tabs registered by other mods show up automatically and their
 * item lists are used as-is. Items are rendered/clicked by HUI; the creative item add/remove operations use the vanilla
 * creative mode helpers ({@code handleCreativeModeItemAdd}), so no server menu is needed.
 */
public final class HuiCreativeUiContent implements WindowContent {
    private static final int SLOT = 18;
    private static final int COLUMNS = 9;

    private final Minecraft minecraft = Minecraft.getInstance();
    private CreativeModeTab selectedTab = CreativeModeTabs.getDefaultTab();
    private String query = "";
    private int pageScroll;
    private boolean searchFocused;

    private UiNode root;
    private UiWindowContent delegate;
    private List<ItemStack> visibleItems = List.of();

    public HuiCreativeUiContent() {
        rebuild();
    }

    private void rebuild() {
        this.visibleItems = collectItems();
        this.pageScroll = 0;

        UiNode tabs = Ui.scroll(new UiStyle().percentWidth(1.0).height(150).gap(2),
                buildTabNodes().toArray(UiNode[]::new));
        UiNode search = Ui.textInput("Search items…", () -> this.query, value -> {
            this.query = value;
            this.searchFocused = true;
            rebuild();
        });
        search.style.widthPercent = 1.0;
        search.style.height = 16;
        search.style.align = UiStyle.Align.STRETCH;

        UiNode grid = Ui.scroll(new UiStyle().percentWidth(1.0).height(96).gap(1),
                buildGridNodes().toArray(UiNode[]::new));
        grid.style.align = UiStyle.Align.STRETCH;

        UiNode hotbar = Ui.row(new UiStyle().gap(1), buildHotbarNodes().toArray(UiNode[]::new));
        hotbar.style.align = UiStyle.Align.STRETCH;

        UiNode sidebar = Ui.column(new UiStyle().width(112).gap(2),
                Ui.label("Categories"), tabs);
        sidebar.style.align = UiStyle.Align.STRETCH;

        UiNode main = Ui.column(new UiStyle().gap(4), search, grid, hotbar);
        main.style.align = UiStyle.Align.STRETCH;
        main.style.widthPercent = 1.0;

        this.root = Ui.row(new UiStyle().size(340, 210).padding(6).gap(6).background(Ui.PANEL)
                .border(Ui.BORDER, 1), sidebar, main);
        if (this.searchFocused) {
            search.focused = true;
        }
        this.delegate = new UiWindowContent(this.root);
        this.delegate.setOverlay(this::drawCarried);
    }

    private List<ItemStack> collectItems() {
        if (this.query.isBlank()) {
            return new ArrayList<>(this.selectedTab.getDisplayItems());
        }
        String needle = this.query.toLowerCase(java.util.Locale.ROOT);
        Map<String, ItemStack> unique = new LinkedHashMap<>();
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            for (ItemStack stack : tab.getDisplayItems()) {
                String name = stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
                if (!name.contains(needle)) {
                    continue;
                }
                String key = stack.getItem() + "|" + stack.getComponents();
                unique.putIfAbsent(key, stack);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private List<UiNode> buildTabNodes() {
        List<UiNode> nodes = new ArrayList<>();
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            boolean selected = tab == this.selectedTab;
            UiNode node = Ui.row(new UiStyle().percentWidth(1.0).height(18).gap(3).padding(2, 1)
                    .align(UiStyle.Align.CENTER)
                    .background(selected ? 0x553FC7FF : 0xFF10161C)
                    .border(selected ? Ui.ACCENT : Ui.BORDER, 1));
            UiNode icon = Ui.node(new UiStyle().size(16, 16));
            ItemStack iconStack = tab.getIconItem();
            icon.draw = (n, graphics) -> drawItem(graphics, iconStack, n.x, n.y);
            UiNode label = Ui.label(tab.getDisplayName().getString());
            label.style.widthPercent = 1.0;
            node.add(icon, label);
            node.onClick = n -> {
                this.selectedTab = tab;
                rebuild();
            };
            node.tooltipSupplier = () -> tab.getDisplayName().getString();
            nodes.add(node);
        }
        return nodes;
    }

    private List<UiNode> buildGridNodes() {
        List<UiNode> nodes = new ArrayList<>();
        int rows = Math.max(5, (this.visibleItems.size() + COLUMNS - 1) / COLUMNS);
        for (int row = 0; row < rows; row++) {
            UiNode rowNode = Ui.row(new UiStyle().gap(1).height(SLOT));
            rowNode.style.align = UiStyle.Align.STRETCH;
            for (int column = 0; column < COLUMNS; column++) {
                int index = row * COLUMNS + column;
                ItemStack stack = index < this.visibleItems.size() ? this.visibleItems.get(index) : ItemStack.EMPTY;
                rowNode.add(buildItemNode(stack, () -> handlePickerClick(stack)));
            }
            nodes.add(rowNode);
        }
        return nodes;
    }

    private UiNode buildItemNode(ItemStack stack, Runnable onClick) {
        UiNode node = Ui.node(new UiStyle().size(SLOT, SLOT));
        node.draw = (n, graphics) -> {
            graphics.fill(n.x, n.y, n.x + SLOT, n.y + SLOT, 0xFF0B0F14);
            graphics.outline(n.x, n.y, SLOT, SLOT, 0xFF2F4353);
            if (!stack.isEmpty()) {
                graphics.item(stack, n.x + 1, n.y + 1);
                graphics.itemDecorations(Minecraft.getInstance().font, stack, n.x + 1, n.y + 1);
            }
        };
        node.onClick = n -> onClick.run();
        node.onRightClick = n -> onClick.run();
        if (!stack.isEmpty()) {
            node.tooltipSupplier = () -> stack.getHoverName().getString();
        }
        return node;
    }

    private List<UiNode> buildHotbarNodes() {
        Player player = this.minecraft.player;
        List<UiNode> nodes = new ArrayList<>();
        if (player == null) {
            return nodes;
        }
        for (int index = 0; index < 9; index++) {
            int inventoryIndex = index;
            ItemStack stack = player.getInventory().getItem(inventoryIndex);
            UiNode node = Ui.node(new UiStyle().size(SLOT, SLOT));
            node.draw = (n, graphics) -> {
                graphics.fill(n.x, n.y, n.x + SLOT, n.y + SLOT, 0xFF0B0F14);
                graphics.outline(n.x, n.y, SLOT, SLOT, 0xFF2F4353);
                ItemStack current = player.getInventory().getItem(inventoryIndex);
                if (!current.isEmpty()) {
                    graphics.item(current, n.x + 1, n.y + 1);
                    graphics.itemDecorations(Minecraft.getInstance().font, current, n.x + 1, n.y + 1);
                }
            };
            node.onClick = n -> handleHotbarClick(inventoryIndex);
            node.onRightClick = n -> handleHotbarClick(inventoryIndex);
            nodes.add(node);
        }
        return nodes;
    }

    // --- Vanilla creative click semantics ----------------------------------------------------

    private ItemStack carried() {
        Player player = this.minecraft.player;
        return player == null ? ItemStack.EMPTY : player.inventoryMenu.getCarried();
    }

    private void setCarried(ItemStack stack) {
        Player player = this.minecraft.player;
        if (player != null) {
            player.inventoryMenu.setCarried(stack);
        }
    }

    private void handlePickerClick(ItemStack clicked) {
        if (this.minecraft.player == null || clicked.isEmpty()) {
            return;
        }
        boolean quickKey = this.delegate.lastClickShift;
        int button = this.delegate.lastClickButton == 1 ? 1 : 0;
        ItemStack carried = carried();
        if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, clicked)) {
            if (button == 0) {
                if (quickKey) {
                    carried.setCount(carried.getMaxStackSize());
                } else if (carried.getCount() < carried.getMaxStackSize()) {
                    carried.grow(1);
                }
            } else {
                carried.shrink(1);
            }
        } else if (carried.isEmpty()) {
            int count = quickKey ? clicked.getMaxStackSize() : clicked.getCount();
            setCarried(clicked.copyWithCount(count));
        } else if (button == 0) {
            setCarried(ItemStack.EMPTY);
        } else {
            carried.shrink(1);
        }
    }

    private void handleHotbarClick(int inventoryIndex) {
        Player player = this.minecraft.player;
        if (player == null) {
            return;
        }
        ItemStack carried = carried();
        ItemStack slotStack = player.getInventory().getItem(inventoryIndex);
        if (!carried.isEmpty()) {
            player.getInventory().setItem(inventoryIndex, carried.copy());
            this.minecraft.gameMode.handleCreativeModeItemAdd(carried.copy(), 36 + inventoryIndex);
            setCarried(ItemStack.EMPTY);
        } else if (!slotStack.isEmpty()) {
            setCarried(slotStack.copy());
            player.getInventory().setItem(inventoryIndex, ItemStack.EMPTY);
            this.minecraft.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, 36 + inventoryIndex);
        }
        player.inventoryMenu.broadcastChanges();
        rebuild();
    }

    private void drawItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) {
            graphics.item(stack, x, y);
            graphics.itemDecorations(this.minecraft.font, stack, x, y);
        }
    }

    private void drawCarried(GuiGraphicsExtractor graphics, int[] mouse) {
        ItemStack carried = carried();
        if (!carried.isEmpty()) {
            graphics.nextStratum();
            graphics.item(carried, mouse[0] - 8, mouse[1] - 8);
            graphics.itemDecorations(this.minecraft.font, carried, mouse[0] - 8, mouse[1] - 8);
        }
    }

    // --- WindowContent -----------------------------------------------------------------------

    @Override
    public int preferredWidth() {
        return 340;
    }

    @Override
    public int preferredHeight() {
        return 210;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        this.delegate.extract(graphics, mouseX, mouseY, partialTick, originX, originY);
    }

    @Override
    public void extractOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        this.delegate.extractOverlay(graphics, mouseX, mouseY, partialTick, originX, originY);
    }

    @Override
    public void tick() {
        this.delegate.tick();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, int originX, int originY, boolean doubleClick) {
        return this.delegate.mouseClicked(event, originX, originY, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, int originX,
            int originY) {
        return this.delegate.mouseScrolled(mouseX, mouseY, scrollX, scrollY, originX, originY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return this.delegate.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return this.delegate.charTyped(event);
    }

    @Override
    public void removed() {
        // Creative inventory carried stacks are client only: do not leave one hanging when the window closes.
        setCarried(ItemStack.EMPTY);
    }
}
