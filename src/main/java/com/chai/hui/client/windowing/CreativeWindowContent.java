package com.chai.hui.client.windowing;

import java.util.ArrayList;
import java.util.List;

import com.chai.hui.client.ui.MenuUiContext;
import com.chai.hui.menu.HuiCreativeMenu;
import com.chai.hui.network.CreativeTabPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.gui.CreativeTabsScreenPage;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Pixel faithful creative inventory window on top of HUI's server side {@link HuiCreativeMenu}.
 *
 * <p>Rendering follows vanilla {@code CreativeModeInventoryScreen}: the tab background texture of the selected tab, the
 * two tab rows (NeoForge tab pages), the page buttons, the borderless search box, the scrollbar and the tab icons; the
 * slots and the carried stack come from the real server menu instead of a client only picker.
 */
public final class CreativeWindowContent implements MenuWindowContent {
    private static final int IMAGE_WIDTH = 195;
    private static final int IMAGE_HEIGHT = 136;
    private static final int TOP_MARGIN = 56;
    private static final int BOTTOM_MARGIN = 32;
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 0;

    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier DESTROY_SPRITE =
            Identifier.withDefaultNamespace("container/creative_inventory/tab_item_search");
    private static final Identifier[] SELECTED_TOP = sprites("tab_top_selected_%d", 7);
    private static final Identifier[] UNSELECTED_TOP = sprites("tab_top_unselected_%d", 7);
    private static final Identifier[] SELECTED_BOTTOM = sprites("tab_bottom_selected_%d", 7);
    private static final Identifier[] UNSELECTED_BOTTOM = sprites("tab_bottom_unselected_%d", 7);

    private final HuiCreativeMenu menu;
    private final List<CreativeModeTab> tabs;
    private final List<CreativeTabsScreenPage> pages = new ArrayList<>();
    private CreativeTabsScreenPage page;
    private int tabIndex;
    private String search = "";
    private int scrollRow;
    private @Nullable EditBox searchBox;
    private int originX;
    private int originY;
    private @Nullable Slot hovered;
    private boolean draggingScrollbar;

    public CreativeWindowContent(HuiCreativeMenu menu) {
        this.menu = menu;
        this.tabs = new ArrayList<>();
        for (CreativeModeTab tab : CreativeModeTabRegistry.getSortedCreativeModeTabs()) {
            this.tabs.add(tab);
        }
        for (int start = 0; start < this.tabs.size(); start += 10) {
            this.pages.add(new CreativeTabsScreenPage(this.tabs.subList(start, Math.min(start + 10, this.tabs.size()))));
        }
        if (this.pages.isEmpty()) {
            this.pages.add(new CreativeTabsScreenPage(List.of()));
        }
        this.page = this.pages.get(0);
        this.tabIndex = Math.max(0, this.tabs.indexOf(this.page.getDefaultTab()));
    }

    private static Identifier[] sprites(String pattern, int count) {
        Identifier[] identifiers = new Identifier[count];
        for (int index = 0; index < count; index++) {
            identifiers[index] = Identifier.withDefaultNamespace(
                    "container/creative_inventory/" + String.format(pattern, index + 1));
        }
        return identifiers;
    }

    // --- MenuWindowContent -------------------------------------------------------------------

    @Override
    public boolean paintsBodyBackground() {
        return false;
    }

    @Override
    public int preferredWidth() {
        return IMAGE_WIDTH + PADDING * 2;
    }

    @Override
    public int preferredHeight() {
        return TOP_MARGIN + IMAGE_HEIGHT + BOTTOM_MARGIN;
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu screenMenu() {
        return this.menu;
    }

    @Override
    public int menuContainerId() {
        return this.menu.containerId;
    }

    @Override
    public boolean serverBacked() {
        return true;
    }

    @Override
    public void activate() {
        MenuUiContext.activateMenu(this.menu);
    }

    @Override
    public int slotIndexAt(int mouseX, int mouseY, int originX, int originY) {
        int localX = mouseX - originX - PADDING;
        int localY = mouseY - originY - TOP_MARGIN;
        int index = slotAt(localX, localY);
        return index < 0 ? -1 : index;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        this.originX = originX;
        this.originY = originY;
        Font font = Minecraft.getInstance().font;
        CreativeModeTab selectedTab = selectedTab();
        int px = originX + PADDING;
        int py = originY + TOP_MARGIN;

        graphics.pose().pushMatrix();
        graphics.pose().translate(px, py);

        // Tab background of the selected tab.
        graphics.blit(RenderPipelines.GUI_TEXTURED, selectedTab.getBackgroundTexture(), 0, 0, 0.0F, 0.0F,
                IMAGE_WIDTH, IMAGE_HEIGHT, 256, 256);

        // Page buttons.
        if (this.pages.size() > 1) {
            int pageIndex = this.pages.indexOf(this.page);
            drawPageButton(graphics, font, 0, -50, "<", pageIndex > 0);
            drawPageButton(graphics, font, IMAGE_WIDTH - 20, -50, ">", pageIndex < this.pages.size() - 1);
        }

        // Tab buttons: unselected first, selected last (it overlaps the panel border).
        for (CreativeModeTab tab : this.page.getVisibleTabs()) {
            if (tab != selectedTab) {
                drawTab(graphics, tab);
            }
        }
        drawTab(graphics, selectedTab);

        // Slots.
        this.hovered = null;
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 9; column++) {
                int slotIndex = row * 9 + column;
                Slot slot = this.menu.getSlot(slotIndex);
                drawSlot(graphics, slot, 8 + column * SLOT_SIZE, 18 + row * SLOT_SIZE, mouseX - px, mouseY - py);
            }
        }
        for (int index = 0; index < 9; index++) {
            Slot slot = this.menu.getSlot(HuiCreativeMenu.HOTBAR_START + index);
            drawSlot(graphics, slot, 8 + index * SLOT_SIZE, 112, mouseX - px, mouseY - py);
        }

        // Search box and scrollbar.
        if (this.searchBox == null) {
            this.searchBox = new EditBox(font, 82, 6, 80, 9, Component.translatable("itemGroup.search"));
            this.searchBox.setMaxLength(50);
            this.searchBox.setBordered(false);
            this.searchBox.setTextColor(0xFFFFFFFF);
            this.searchBox.setResponder(value -> {
                this.search = value;
                this.scrollRow = 0;
                sendTabUpdate();
            });
            this.searchBox.setValue(this.search);
        }
        this.searchBox.setVisible(selectedTab.hasSearchBar());
        this.searchBox.extractWidgetRenderState(graphics, mouseX - px, mouseY - py, partialTick);

        if (selectedTab.canScroll()) {
            int trackX = 175;
            int trackTop = 18;
            int trackBottom = trackTop + 112;
            int thumbY = trackTop + (int) ((trackBottom - trackTop - 17) * scrollFraction());
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, selectedTab.getScrollerSprite(), trackX, thumbY, 12, 15);
        }

        // Player preview for the inventory tab (vanilla style).
        if (selectedTab.getType() == CreativeModeTab.Type.INVENTORY && Minecraft.getInstance().player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, 73, 6, 105, 49, 20, 0.0625F,
                    mouseX - px, mouseY - py, Minecraft.getInstance().player);
        }

        graphics.pose().popMatrix();

        if (this.hovered != null && this.hovered.hasItem()) {
            graphics.setTooltipForNextFrame(font, this.hovered.getItem(), mouseX, mouseY);
        }
    }

    @Override
    public void extractOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        ItemStack carried = this.menu.getCarried();
        if (!carried.isEmpty()) {
            graphics.nextStratum();
            graphics.item(carried, mouseX - 8, mouseY - 8);
            graphics.itemDecorations(Minecraft.getInstance().font, carried, mouseX - 8, mouseY - 8);
        }
    }

    private void drawPageButton(GuiGraphicsExtractor graphics, Font font, int x, int y, String label, boolean enabled) {
        graphics.fill(x, y, x + 20, y + 20, 0xFF10161C);
        graphics.outline(x, y, 20, 20, enabled ? 0xFF3FC7FF : 0xFF2F4353);
        graphics.centeredText(font, label, x + 10, y + 6, enabled ? 0xFFE6EDF3 : 0xFF5A6B78);
    }

    private void drawTab(GuiGraphicsExtractor graphics, CreativeModeTab tab) {
        boolean selected = tab == selectedTab();
        boolean top = this.page.isTop(tab);
        int pos = this.page.getColumn(tab);
        int x = tabX(tab);
        int y = top ? -28 : IMAGE_HEIGHT - 4;
        Identifier[] sprites = top ? (selected ? SELECTED_TOP : UNSELECTED_TOP)
                : (selected ? SELECTED_BOTTOM : UNSELECTED_BOTTOM);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprites[Math.max(0, Math.min(pos, sprites.length - 1))], x, y,
                26, 32);
        graphics.item(tab.getIconItem(), x + 5, y + (top ? 9 : 7));
    }

    private void drawSlot(GuiGraphicsExtractor graphics, Slot slot, int x, int y, int localMouseX, int localMouseY) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x - 1, y - 1, 18, 18);
        ItemStack stack = slot.getItem();
        if (!stack.isEmpty()) {
            graphics.item(stack, x, y);
            graphics.itemDecorations(Minecraft.getInstance().font, stack, x, y);
        }
        if (localMouseX >= x - 1 && localMouseX < x + 17 && localMouseY >= y - 1 && localMouseY < y + 17) {
            this.hovered = slot;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x55FFFFFF);
        }
    }

    private CreativeModeTab selectedTab() {
        if (this.tabs.isEmpty()) {
            return CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0).title(Component.empty()).build();
        }
        int index = Math.max(0, Math.min(this.tabIndex, this.tabs.size() - 1));
        return this.tabs.get(index);
    }

    private int tabX(CreativeModeTab tab) {
        int pos = this.page.getColumn(tab);
        int x = 27 * pos;
        if (tab.isAlignedRight()) {
            x = IMAGE_WIDTH - 27 * (7 - pos) + 1;
        }
        return x;
    }

    private float scrollFraction() {
        int maxRow = Math.max(1, Math.max(0, (this.menu.displayItemCount() + 8) / 9 - 5));
        return Math.min(1.0F, Math.max(0.0F, (float) this.scrollRow / maxRow));
    }

    private int slotAt(int localX, int localY) {
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 9; column++) {
                int x = 8 + column * SLOT_SIZE;
                int y = 18 + row * SLOT_SIZE;
                if (localX >= x - 1 && localX < x + 17 && localY >= y - 1 && localY < y + 17) {
                    return row * 9 + column;
                }
            }
        }
        for (int index = 0; index < 9; index++) {
            int x = 8 + index * SLOT_SIZE;
            if (localX >= x - 1 && localX < x + 17 && localY >= 111 && localY < 129) {
                return HuiCreativeMenu.HOTBAR_START + index;
            }
        }
        return -1;
    }

    // --- Input -------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, int originX, int originY, boolean doubleClick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        int px = originX + PADDING;
        int py = originY + TOP_MARGIN;
        int localX = (int) event.x() - px;
        int localY = (int) event.y() - py;

        // Tabs.
        if (localY < 0 || localY > IMAGE_HEIGHT) {
            for (CreativeModeTab tab : this.page.getVisibleTabs()) {
                int x = tabX(tab);
                int y = this.page.isTop(tab) ? -32 : IMAGE_HEIGHT - 8;
                if (localX >= x && localX <= x + 26 && localY >= y && localY <= y + 32) {
                    selectTab(tab);
                    return true;
                }
            }
        }

        // Page buttons.
        if (this.pages.size() > 1 && localY >= -50 && localY < -30) {
            int pageIndex = this.pages.indexOf(this.page);
            if (localX >= 0 && localX < 20 && pageIndex > 0) {
                setPage(pageIndex - 1);
                return true;
            }
            if (localX >= IMAGE_WIDTH - 20 && localX < IMAGE_WIDTH && pageIndex < this.pages.size() - 1) {
                setPage(pageIndex + 1);
                return true;
            }
        }

        // Search box.
        if (this.searchBox != null && selectedTab().hasSearchBar() && localX >= 82 && localX < 162 && localY >= 6
                && localY < 15) {
            this.searchBox.onClick(new MouseButtonEvent(localX, localY, event.buttonInfo()), doubleClick);
            return true;
        }

        // Scrollbar.
        if (selectedTab().canScroll() && localX >= 175 && localX < 187 && localY >= 18 && localY < 130) {
            this.draggingScrollbar = true;
            scrollToMouse(localY);
            return true;
        }

        // Slots.
        int slotIndex = slotAt(localX, localY);
        if (slotIndex < 0) {
            return false;
        }
        com.chai.hui.HuiMod.LOGGER.info("[HUI] creative slot click slot={} menu={}", slotIndex,
                this.menu.containerId);
        activate();
        int button = event.button() == 1 ? 1 : 0;
        ContainerInput input = event.hasShiftDown() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
        minecraft.gameMode.handleContainerInput(this.menu.containerId, slotIndex, button, input, minecraft.player);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, int originX, int originY, double dragX, double dragY) {
        if (this.draggingScrollbar) {
            int localY = (int) event.y() - (originY + TOP_MARGIN);
            scrollToMouse(localY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event, int originX, int originY) {
        this.draggingScrollbar = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, int originX,
            int originY) {
        if (selectedTab().canScroll()) {
            int maxRow = Math.max(0, (this.menu.displayItemCount() + 8) / 9 - 5);
            this.scrollRow = Math.max(0, Math.min(maxRow, this.scrollRow - (int) Math.round(scrollY)));
            sendTabUpdate();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return this.searchBox != null && this.searchBox.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return this.searchBox != null && this.searchBox.charTyped(event);
    }

    private void scrollToMouse(int localY) {
        int delta = Math.max(0, Math.min(112, localY - 18));
        int maxRow = Math.max(0, (this.menu.displayItemCount() + 8) / 9 - 5);
        this.scrollRow = Math.max(0, Math.min(maxRow, (int) Math.round(delta / 112.0F * maxRow)));
        sendTabUpdate();
    }

    private void selectTab(CreativeModeTab tab) {
        int index = this.tabs.indexOf(tab);
        if (index >= 0) {
            this.tabIndex = index;
        }
        this.scrollRow = 0;
        this.page = this.pages.stream().filter(candidate -> candidate.getVisibleTabs().contains(tab)).findFirst()
                .orElse(this.page);
        sendTabUpdate();
    }

    private void setPage(int index) {
        this.page = this.pages.get(index);
        CreativeModeTab first = this.page.getVisibleTabs().isEmpty() ? null : this.page.getVisibleTabs().get(0);
        if (first != null) {
            this.tabIndex = this.tabs.indexOf(first);
            this.scrollRow = 0;
            sendTabUpdate();
        }
    }

    private void sendTabUpdate() {
        ClientPacketDistributor.sendToServer(new CreativeTabPayload(this.tabIndex, this.search, this.scrollRow));
    }

    @Override
    public void removed() {
        if (this.searchBox != null) {
            this.searchBox.setFocused(false);
        }
    }
}
