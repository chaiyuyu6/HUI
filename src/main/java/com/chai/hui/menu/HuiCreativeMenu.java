package com.chai.hui.menu;

import java.util.List;

import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

/**
 * Server side creative inventory for HUI.
 *
 * <p>Vanilla's creative item picker is a client only menu; HUI uses a real menu instead so the carried stack, tab
 * contents and item placement are all server authoritative and cannot desync or duplicate.
 *
 * <p>Slots: 0-44 display slots (read only, clicked items are cloned to the carried stack), 45-53 the player hotbar.
 */
public final class HuiCreativeMenu extends AbstractContainerMenu {
    public static final int DISPLAY_COLUMNS = 9;
    public static final int DISPLAY_ROWS = 5;
    public static final int DISPLAY_SLOTS = DISPLAY_COLUMNS * DISPLAY_ROWS;
    public static final int HOTBAR_START = DISPLAY_SLOTS;

    private final SimpleContainer display = new SimpleContainer(DISPLAY_SLOTS);
    private int tabIndex;
    private String search = "";
    private int scrollRow;
    private int displayItemCount;

    public HuiCreativeMenu(int containerId, Inventory inventory) {
        super(HuiMenus.CREATIVE.get(), containerId);
        for (int row = 0; row < DISPLAY_ROWS; row++) {
            for (int column = 0; column < DISPLAY_COLUMNS; column++) {
                this.addSlot(new DisplaySlot(this.display, row * DISPLAY_COLUMNS + column, 8 + column * 18,
                        18 + row * 18));
            }
        }
        for (int index = 0; index < 9; index++) {
            this.addSlot(new Slot(inventory, index, 8 + index * 18, 112));
        }
        if (inventory.player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // The creative tab item lists are built by the server on demand; make sure they exist before reading them.
            CreativeModeTabs.tryRebuildTabContents(serverPlayer.level().enabledFeatures(),
                    serverPlayer.canUseGameMasterBlocks(), serverPlayer.level().registryAccess());
        }
        fillTab();
    }

    /** Rebuilds the display slots for the current tab (and search filter). */
    public void fillTab() {
        List<CreativeModeTab> tabs = sortedTabs();
        if (tabs.isEmpty()) {
            return;
        }
        if (this.tabIndex < 0 || this.tabIndex >= tabs.size()) {
            this.tabIndex = 0;
        }
        CreativeModeTab tab = tabs.get(this.tabIndex);
        List<ItemStack> items = new java.util.ArrayList<>();
        if (this.search.isBlank()) {
            items.addAll(tab.getDisplayItems());
        } else {
            String needle = this.search.toLowerCase(java.util.Locale.ROOT);
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (CreativeModeTab candidate : tabs) {
                for (ItemStack stack : candidate.getDisplayItems()) {
                    if (stack.isEmpty() || !stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT)
                            .contains(needle)) {
                        continue;
                    }
                    if (seen.add(stack.getItem() + "|" + stack.getComponents())) {
                        items.add(stack);
                    }
                }
            }
        }
        this.displayItemCount = items.size();
        int offset = this.scrollRow * DISPLAY_COLUMNS;
        for (int index = 0; index < DISPLAY_SLOTS; index++) {
            int itemIndex = offset + index;
            this.display.setItem(index, itemIndex < items.size() ? items.get(itemIndex) : ItemStack.EMPTY);
        }
        this.broadcastChanges();
    }

    private static List<CreativeModeTab> sortedTabs() {
        List<CreativeModeTab> tabs = new java.util.ArrayList<>();
        for (CreativeModeTab tab : net.neoforged.neoforge.common.CreativeModeTabRegistry.getSortedCreativeModeTabs()) {
            if (tab.hasAnyItems()) {
                tabs.add(tab);
            }
        }
        return tabs;
    }

    public int displayItemCount() {
        return this.displayItemCount;
    }

    public void setScrollRow(int scrollRow) {
        this.scrollRow = Math.max(0, scrollRow);
        fillTab();
    }

    public int tabIndex() {
        return this.tabIndex;
    }

    public void setTabIndex(int tabIndex) {
        this.tabIndex = tabIndex;
        fillTab();
    }

    public String search() {
        return this.search;
    }

    public void setSearch(String search) {
        this.search = search == null ? "" : search;
        fillTab();
    }

    public static Identifier tabId(CreativeModeTab tab) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        return id == null ? Identifier.fromNamespaceAndPath("minecraft", "building_blocks") : id;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        // Display slots are infinite: a click clones the stack into the carried slot (server authoritative), instead of
        // removing it from the slot.
        if (slotId >= 0 && slotId < DISPLAY_SLOTS && player.hasInfiniteMaterials()) {
            ItemStack clicked = this.slots.get(slotId).getItem();
            if (!clicked.isEmpty() && getCarried().isEmpty()) {
                setCarried(clicked.copyWithCount(clicked.getMaxStackSize()));
                return;
            }
        }
        super.clicked(slotId, button, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private static final class DisplaySlot extends Slot {
        private DisplaySlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
