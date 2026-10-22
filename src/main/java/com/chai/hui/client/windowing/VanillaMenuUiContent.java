package com.chai.hui.client.windowing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.chai.hui.client.windowing.ui.GuiTextures;
import com.chai.hui.network.ActivateMenuPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The "vanilla to HUI UI" adapter: renders an {@link AbstractContainerMenu} with HUI's own panel/slots and translates
 * mouse/keyboard events into the exact {@link ContainerInput} sequences the vanilla container screen would send.
 *
 * <p>The data source stays the vanilla menu (slot contents, carried stack), so all synchronisation still goes through the
 * vanilla packets. Only rendering and input are HUI's own.
 */
public final class VanillaMenuUiContent implements MenuWindowContent {
    private static final int PADDING = 6;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_HIGHLIGHT = 0x557FD9FF;

    private final AbstractContainerMenu menu;
    private final Component title;
    private final List<Slot> visibleSlots = new ArrayList<>();
    private final int minX;
    private final int minY;
    private final int width;
    private final int height;
    private final @Nullable BlockPos sourcePos;
    private final @Nullable java.util.function.Function<Slot, int[]> layoutOverride;

    // Vanilla AbstractContainerScreen click state machine.
    private @Nullable Slot lastClickSlot;
    private boolean doubleclick;
    private boolean skipNextRelease;
    private boolean isQuickCrafting;
    private int quickCraftingType;
    private int quickCraftingButton;
    private final Set<Slot> quickCraftSlots = new HashSet<>();
    private int quickCraftingRemainder;
    private ItemStack lastQuickMoved = ItemStack.EMPTY;

    private @Nullable MachineDecorations.Renderer decoration;
    private @Nullable Slot hovered;
    private int originX;
    private int originY;

    public VanillaMenuUiContent(AbstractContainerMenu menu, Component title, Predicate<Slot> slotFilter,
            @Nullable BlockPos sourcePos) {
        this(menu, title, slotFilter, sourcePos, null, -1, -1);
    }

    /**
     * Variant with a custom slot layout: {@code layoutOverride} returns the panel relative [x, y] of a slot, which lets
     * the default inventory window show the armor slots in a HUI style layout instead of the vanilla screen positions.
     */
    public VanillaMenuUiContent(AbstractContainerMenu menu, Component title, Predicate<Slot> slotFilter,
            @Nullable BlockPos sourcePos, @Nullable java.util.function.Function<Slot, int[]> layoutOverride,
            int fixedWidth, int fixedHeight) {
        this.menu = menu;
        this.title = title;
        this.sourcePos = sourcePos;
        this.layoutOverride = layoutOverride;
        int minSlotX = Integer.MAX_VALUE;
        int minSlotY = Integer.MAX_VALUE;
        int maxSlotX = Integer.MIN_VALUE;
        int maxSlotY = Integer.MIN_VALUE;
        for (Slot slot : menu.slots) {
            if (!slotFilter.test(slot)) {
                continue;
            }
            this.visibleSlots.add(slot);
            minSlotX = Math.min(minSlotX, slot.x);
            minSlotY = Math.min(minSlotY, slot.y);
            maxSlotX = Math.max(maxSlotX, slot.x);
            maxSlotY = Math.max(maxSlotY, slot.y);
        }
        if (this.visibleSlots.isEmpty()) {
            for (Slot slot : menu.slots) {
                this.visibleSlots.add(slot);
                minSlotX = Math.min(minSlotX, slot.x);
                minSlotY = Math.min(minSlotY, slot.y);
                maxSlotX = Math.max(maxSlotX, slot.x);
                maxSlotY = Math.max(maxSlotY, slot.y);
            }
        }
        if (this.visibleSlots.isEmpty()) {
            minSlotX = 0;
            minSlotY = 0;
            maxSlotX = 0;
            maxSlotY = 0;
        }
        this.minX = minSlotX;
        this.minY = minSlotY;
        this.width = fixedWidth > 0 ? fixedWidth : (maxSlotX - minSlotX) + SLOT_SIZE + PADDING * 2;
        this.height = fixedHeight > 0 ? fixedHeight : (maxSlotY - minSlotY) + SLOT_SIZE + PADDING * 2;
    }

    // --- WindowContent ---------------------------------------------------------------------

    @Override
    public int preferredWidth() {
        return this.width;
    }

    @Override
    public int preferredHeight() {
        return this.height;
    }

    @Override
    public AbstractContainerMenu screenMenu() {
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
    public @Nullable BlockPos sourcePos() {
        return this.sourcePos;
    }

    @Override
    public void activate() {
        ClientPacketDistributor.sendToServer(new ActivateMenuPayload(this.menu.containerId));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.containerMenu = this.menu;
        }
    }

    /** Adds HUI style renders for vanilla widgets (furnace flame/arrow, brewing bubbles, player preview, ...). */
    public VanillaMenuUiContent withDecoration(@Nullable MachineDecorations.Renderer renderer) {
        this.decoration = renderer;
        return this;
    }

    // --- Layout ----------------------------------------------------------------------------

    private int slotX(Slot slot) {
        if (this.layoutOverride != null) {
            return this.originX + this.layoutOverride.apply(slot)[0];
        }
        return this.originX + PADDING + (slot.x - this.minX);
    }

    private int slotY(Slot slot) {
        if (this.layoutOverride != null) {
            return this.originY + this.layoutOverride.apply(slot)[1];
        }
        return this.originY + PADDING + (slot.y - this.minY);
    }

    private @Nullable Slot getHoveredSlot(double mouseX, double mouseY) {
        for (Slot slot : this.visibleSlots) {
            if (!slot.isActive()) {
                continue;
            }
            int x = slotX(slot) - 1;
            int y = slotY(slot) - 1;
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                return slot;
            }
        }
        return null;
    }

    @Override
    public int slotIndexAt(int mouseX, int mouseY, int originX, int originY) {
        this.originX = originX;
        this.originY = originY;
        Slot slot = getHoveredSlot(mouseX, mouseY);
        return slot == null ? -1 : slot.index;
    }

    @Override
    public int @Nullable [] slotScreenArea(int slotIndex, int originX, int originY) {
        if (slotIndex < 0 || slotIndex >= this.menu.slots.size()) {
            return null;
        }
        Slot slot = this.menu.getSlot(slotIndex);
        if (!this.visibleSlots.contains(slot) || !slot.isActive()) {
            return null;
        }
        int x = originX + PADDING + (this.layoutOverride != null ? this.layoutOverride.apply(slot)[0] : slot.x - this.minX);
        int y = originY + PADDING + (this.layoutOverride != null ? this.layoutOverride.apply(slot)[1] : slot.y - this.minY);
        if (this.layoutOverride != null) {
            x = originX + this.layoutOverride.apply(slot)[0];
            y = originY + this.layoutOverride.apply(slot)[1];
        }
        return new int[] {x - 1, y - 1, SLOT_SIZE, SLOT_SIZE};
    }

    // --- Rendering -------------------------------------------------------------------------

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        Font font = Minecraft.getInstance().font;
        this.originX = originX;
        this.originY = originY;

        GuiTextures.drawWindowFrame(graphics, originX, originY, this.width, this.height);

        this.hovered = getHoveredSlot(mouseX, mouseY);
        ItemStack carried = this.menu.getCarried();

        // Pass 1: slot frames and highlights.
        for (Slot slot : this.visibleSlots) {
            if (!slot.isActive()) {
                continue;
            }
            int x = slotX(slot);
            int y = slotY(slot);
            boolean isHovered = slot == this.hovered;
            GuiTextures.drawSlot(graphics, x - 1, y - 1);
            if (isHovered) {
                GuiTextures.drawSlotHover(graphics, x - 1, y - 1);
            }
            if (slot.isHighlightable() && isHovered) {
                graphics.fill(x - 1, y - 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, SLOT_HIGHLIGHT);
            }
        }

        // Pass 2: machine decorations between the slot frames and the items, so the flame glow is visible but item
        // icons stay on top of it.
        if (this.decoration != null) {
            graphics.enableScissor(this.originX, this.originY, this.originX + this.width,
                    this.originY + this.height);
            this.decoration.render(graphics, this.menu,
                    slotIndex -> new int[] {slotX(this.menu.getSlot(slotIndex)),
                            slotY(this.menu.getSlot(slotIndex))},
                    mouseX, mouseY, partialTick);
            graphics.disableScissor();
        }

        // Pass 3: item stacks (including the quick craft preview).
        for (Slot slot : this.visibleSlots) {
            if (!slot.isActive()) {
                continue;
            }
            int x = slotX(slot);
            int y = slotY(slot);
            ItemStack stack = slot.getItem();
            String itemCount = null;
            if (this.isQuickCrafting && this.quickCraftSlots.contains(slot) && !carried.isEmpty()) {
                if (this.quickCraftSlots.size() == 1) {
                    continue;
                }
                if (AbstractContainerMenu.canItemQuickReplace(slot, carried, true) && this.menu.canDragTo(slot)) {
                    int maxSize = Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried));
                    int current = stack.isEmpty() ? 0 : stack.getCount();
                    int newCount = AbstractContainerMenu.getQuickCraftPlaceCount(this.quickCraftSlots.size(),
                            this.quickCraftingType, carried) + current;
                    if (newCount > maxSize) {
                        newCount = maxSize;
                        itemCount = ChatFormatting.YELLOW.toString() + maxSize;
                    }
                    stack = carried.copyWithCount(newCount);
                } else {
                    this.quickCraftSlots.remove(slot);
                }
            }
            if (!stack.isEmpty()) {
                graphics.item(stack, x, y);
                if (itemCount != null) {
                    graphics.itemDecorations(font, stack, x, y, itemCount);
                } else {
                    graphics.itemDecorations(font, stack, x, y);
                }
            }
        }

    }

    @Override
    public void extractOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        Font font = Minecraft.getInstance().font;
        boolean overPanel = mouseX >= originX && mouseX < originX + this.width && mouseY >= originY
                && mouseY < originY + this.height;

        ItemStack carried = this.menu.getCarried();
        if (!carried.isEmpty()) {
            String itemCount = null;
            if (this.isQuickCrafting && this.quickCraftSlots.size() > 1) {
                carried = carried.copyWithCount(this.quickCraftingRemainder);
                if (carried.isEmpty()) {
                    itemCount = ChatFormatting.YELLOW + "0";
                }
            }
            graphics.nextStratum();
            graphics.item(carried, mouseX - 8, mouseY - 8);
            if (itemCount != null) {
                graphics.itemDecorations(font, carried, mouseX - 8, mouseY - 8, itemCount);
            } else {
                graphics.itemDecorations(font, carried, mouseX - 8, mouseY - 8);
            }
        } else if (overPanel && this.hovered != null && this.hovered.hasItem()) {
            graphics.setTooltipForNextFrame(font, this.hovered.getItem(), mouseX, mouseY);
        }
    }

    // --- Input -----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, int originX, int originY, boolean doubleClick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        this.originX = originX;
        this.originY = originY;
        if (this.decoration != null && this.decoration.mouseClicked(event, this.menu,
                index -> new int[] {slotX(this.menu.getSlot(index)), slotY(this.menu.getSlot(index))})) {
            return true;
        }
        InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(event.button());
        boolean cloning = minecraft.options.keyPickItem.isActiveAndMatches(mouseKey)
                && minecraft.player.hasInfiniteMaterials();
        Slot slot = getHoveredSlot(event.x(), event.y());
        this.doubleclick = this.lastClickSlot == slot && doubleClick;
        this.skipNextRelease = false;
        if (event.button() != 1 && event.button() != 3 && !cloning) {
            checkHotbarMouseClicked(event);
        } else {
            int slotId = slot == null ? -1 : slot.index;
            if (slotId != -1 && !this.isQuickCrafting) {
                if (this.menu.getCarried().isEmpty()) {
                    if (cloning) {
                        slotClicked(slot, slotId, event, ContainerInput.CLONE);
                    } else {
                        boolean quickKey = event.hasShiftDown();
                        ContainerInput input = quickKey ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
                        if (quickKey) {
                            this.lastQuickMoved = slot.hasItem() ? slot.getItem().copy() : ItemStack.EMPTY;
                        }
                        slotClicked(slot, slotId, event, input);
                    }
                    this.skipNextRelease = true;
                } else {
                    this.isQuickCrafting = true;
                    this.quickCraftingButton = event.button();
                    this.quickCraftSlots.clear();
                    if (event.button() == 1) {
                        this.quickCraftingType = 0;
                    } else if (event.button() == 3) {
                        this.quickCraftingType = 1;
                    } else if (cloning) {
                        this.quickCraftingType = 2;
                    }
                }
            }
        }
        this.lastClickSlot = slot;
        return true;
    }

    private void checkHotbarMouseClicked(MouseButtonEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (this.hovered != null && this.menu.getCarried().isEmpty()) {
            if (minecraft.options.keySwapOffhand.matchesMouse(event)) {
                slotClicked(this.hovered, this.hovered.index, 40, ContainerInput.SWAP);
                return;
            }
            for (int i = 0; i < 9; i++) {
                if (minecraft.options.keyHotbarSlots[i].matchesMouse(event)) {
                    slotClicked(this.hovered, this.hovered.index, i, ContainerInput.SWAP);
                }
            }
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, int originX, int originY, double dragX, double dragY) {
        this.originX = originX;
        this.originY = originY;
        Slot slot = getHoveredSlot(event.x(), event.y());
        ItemStack carried = this.menu.getCarried();
        if (slot != null && shouldAddSlotToQuickCraft(slot, carried) && this.quickCraftSlots.add(slot)) {
            recalculateQuickCraftRemaining();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event, int originX, int originY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        this.originX = originX;
        this.originY = originY;
        Slot slot = getHoveredSlot(event.x(), event.y());
        InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(event.button());
        int slotId = slot == null ? -1 : slot.index;

        if (this.doubleclick && slot != null && event.button() == 1
                && this.menu.canTakeItemForPickAll(ItemStack.EMPTY, slot)) {
            if (event.hasShiftDown()) {
                if (!this.lastQuickMoved.isEmpty()) {
                    for (Slot target : this.menu.slots) {
                        if (target.mayPickup(minecraft.player) && target.hasItem() && target.isSameInventory(slot)
                                && AbstractContainerMenu.canItemQuickReplace(target, this.lastQuickMoved, true)) {
                            slotClicked(target, target.index, event, ContainerInput.QUICK_MOVE);
                        }
                    }
                }
            } else {
                slotClicked(slot, slotId, event, ContainerInput.PICKUP_ALL);
            }
            this.doubleclick = false;
        } else {
            if (this.isQuickCrafting && this.quickCraftingButton != event.button()) {
                this.isQuickCrafting = false;
                this.quickCraftSlots.clear();
                this.skipNextRelease = true;
                return true;
            }
            if (this.skipNextRelease) {
                this.skipNextRelease = false;
                return true;
            }
            if (this.isQuickCrafting && !this.quickCraftSlots.isEmpty()) {
                quickCraftToSlots();
            } else if (!this.menu.getCarried().isEmpty()) {
                if (minecraft.options.keyPickItem.isActiveAndMatches(mouseKey)) {
                    slotClicked(slot, slotId, event, ContainerInput.CLONE);
                } else {
                    boolean quickKey = slotId != -1 && event.hasShiftDown();
                    if (quickKey) {
                        this.lastQuickMoved = slot != null && slot.hasItem() ? slot.getItem().copy() : ItemStack.EMPTY;
                    }
                    slotClicked(slot, slotId, event, quickKey ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
                }
            }
        }
        this.isQuickCrafting = false;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        InputConstants.Key key = InputConstants.getKey(event);
        boolean handled = false;
        if (this.menu.getCarried().isEmpty() && this.hovered != null) {
            if (minecraft.options.keySwapOffhand.isActiveAndMatches(key)) {
                slotClicked(this.hovered, this.hovered.index, 40, ContainerInput.SWAP);
                return true;
            }
            for (int i = 0; i < 9; i++) {
                if (minecraft.options.keyHotbarSlots[i].isActiveAndMatches(key)) {
                    slotClicked(this.hovered, this.hovered.index, i, ContainerInput.SWAP);
                    handled = true;
                }
            }
        }
        if (this.hovered != null && this.hovered.hasItem()) {
            if (minecraft.options.keyPickItem.isActiveAndMatches(key)) {
                slotClicked(this.hovered, this.hovered.index, 0, ContainerInput.CLONE);
                handled = true;
            } else if (minecraft.options.keyDrop.matches(event)) {
                slotClicked(this.hovered, this.hovered.index, event.hasControlDown() ? 1 : 0,
                        ContainerInput.THROW);
                handled = true;
            }
        }
        return handled;
    }

    private void slotClicked(@Nullable Slot slot, int slotId, MouseButtonEvent event, ContainerInput input) {
        slotClicked(slot, slotId, getContainerClickButton(event), input);
    }

    private void slotClicked(@Nullable Slot slot, int slotId, int button, ContainerInput input) {
        if (slot != null) {
            slotId = slot.index;
        }
        activate();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.gameMode.handleContainerInput(this.menu.containerId, slotId, button, input, minecraft.player);
        }
    }

    private static int getContainerClickButton(MouseButtonEvent event) {
        return switch (event.button()) {
            case 1 -> 0;
            case 3 -> 1;
            default -> event.button();
        };
    }

    private boolean shouldAddSlotToQuickCraft(Slot slot, ItemStack carried) {
        return this.isQuickCrafting && !carried.isEmpty()
                && (carried.getCount() > this.quickCraftSlots.size() || this.quickCraftingType == 2)
                && AbstractContainerMenu.canItemQuickReplace(slot, carried, true) && slot.mayPlace(carried)
                && this.menu.canDragTo(slot);
    }

    private void recalculateQuickCraftRemaining() {
        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty() || !this.isQuickCrafting) {
            return;
        }
        if (this.quickCraftingType == 2) {
            this.quickCraftingRemainder = carried.getMaxStackSize();
            return;
        }
        this.quickCraftingRemainder = carried.getCount();
        for (Slot slot : this.quickCraftSlots) {
            ItemStack slotStack = slot.getItem();
            int current = slotStack.isEmpty() ? 0 : slotStack.getCount();
            int maxSize = Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried));
            int newCount = Math.min(AbstractContainerMenu.getQuickCraftPlaceCount(this.quickCraftSlots.size(),
                    this.quickCraftingType, carried) + current, maxSize);
            this.quickCraftingRemainder -= newCount - current;
        }
    }

    private void quickCraftToSlots() {
        slotClicked(null, -999, AbstractContainerMenu.getQuickcraftMask(0, this.quickCraftingType),
                ContainerInput.QUICK_CRAFT);
        for (Slot quickSlot : this.quickCraftSlots) {
            slotClicked(quickSlot, quickSlot.index, AbstractContainerMenu.getQuickcraftMask(1, this.quickCraftingType),
                    ContainerInput.QUICK_CRAFT);
        }
        slotClicked(null, -999, AbstractContainerMenu.getQuickcraftMask(2, this.quickCraftingType),
                ContainerInput.QUICK_CRAFT);
    }

    @Override
    public void removed() {
        // Nothing to clean up: the vanilla screen was never initialised.
    }
}
