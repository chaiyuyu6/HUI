package com.chai.hui.client.windowing;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import com.chai.hui.network.ActivateMenuPayload;
import org.jetbrains.annotations.Nullable;

/**
 * HUI-native rendering of a container menu (block entity containers, anvils, stone cutters, ...).
 *
 * <p>The vanilla screen created for the menu is used only as a menu carrier and is never rendered: HUI draws its own
 * panel, slot frames, items and tooltips and sends clicks through {@code handleContainerInput}. This keeps the HUI UI
 * isolated from the vanilla UI while synchronising the same menu data.
 */
public final class HuiContainerContent implements MenuWindowContent {
    private static final int PADDING = 6;
    private static final int SLOT = 18;
    private static final int PANEL_BACKGROUND = 0xE610161C;
    private static final int SLOT_BACKGROUND = 0xFF0B0F14;
    private static final int SLOT_BORDER = 0xFF2F4353;
    private static final int SLOT_BORDER_HOVER = 0xFF7FD9FF;
    private static final int BORDER = 0xFF3FC7FF;

    private final AbstractContainerMenu menu;
    private final Component title;
    private final List<Slot> visibleSlots = new ArrayList<>();
    private final int minX;
    private final int minY;
    private final int width;
    private final int height;
    private final @Nullable BlockPos sourcePos;

    private @Nullable Slot hovered;
    private int originX;
    private int originY;

    public HuiContainerContent(AbstractContainerScreen<?> carrier, @Nullable BlockPos sourcePos) {
        this.menu = carrier.getMenu();
        this.title = carrier.getTitle();
        this.sourcePos = sourcePos;
        int minSlotX = Integer.MAX_VALUE;
        int minSlotY = Integer.MAX_VALUE;
        int maxSlotX = Integer.MIN_VALUE;
        int maxSlotY = Integer.MIN_VALUE;
        for (Slot slot : this.menu.slots) {
            if (slot.container instanceof Inventory) {
                continue;
            }
            this.visibleSlots.add(slot);
            minSlotX = Math.min(minSlotX, slot.x);
            minSlotY = Math.min(minSlotY, slot.y);
            maxSlotX = Math.max(maxSlotX, slot.x);
            maxSlotY = Math.max(maxSlotY, slot.y);
        }
        if (this.visibleSlots.isEmpty()) {
            for (Slot slot : this.menu.slots) {
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
        this.width = (maxSlotX - minSlotX) + SLOT + PADDING * 2;
        this.height = (maxSlotY - minSlotY) + SLOT + PADDING * 2;
    }

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

    private int slotScreenX(Slot slot) {
        return this.originX + PADDING + (slot.x - this.minX);
    }

    private int slotScreenY(Slot slot) {
        return this.originY + PADDING + (slot.y - this.minY);
    }

    @Override
    public int slotIndexAt(int mouseX, int mouseY, int originX, int originY) {
        this.originX = originX;
        this.originY = originY;
        for (Slot slot : this.visibleSlots) {
            int x = slotScreenX(slot);
            int y = slotScreenY(slot);
            if (slot.isActive() && mouseX >= x - 1 && mouseX < x + SLOT - 1 && mouseY >= y - 1
                    && mouseY < y + SLOT - 1) {
                return slot.index;
            }
        }
        return -1;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        Font font = Minecraft.getInstance().font;
        this.originX = originX;
        this.originY = originY;
        this.hovered = null;

        graphics.fill(originX, originY, originX + this.width, originY + this.height, PANEL_BACKGROUND);
        graphics.outline(originX, originY, this.width, this.height, BORDER);

        for (Slot slot : this.visibleSlots) {
            int x = slotScreenX(slot);
            int y = slotScreenY(slot);
            boolean slotHovered = slot.isActive() && mouseX >= x - 1 && mouseX < x + SLOT - 1 && mouseY >= y - 1
                    && mouseY < y + SLOT - 1;
            if (slotHovered) {
                this.hovered = slot;
            }
            graphics.fill(x - 1, y - 1, x + SLOT - 1, y + SLOT - 1, SLOT_BACKGROUND);
            graphics.outline(x - 1, y - 1, SLOT, SLOT, slotHovered ? SLOT_BORDER_HOVER : SLOT_BORDER);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                graphics.item(stack, x, y);
                graphics.itemDecorations(font, stack, x, y);
            }
        }
    }

    @Override
    public void extractOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        Font font = Minecraft.getInstance().font;
        boolean overPanel = mouseX >= originX && mouseX < originX + this.width && mouseY >= originY
                && mouseY < originY + this.height;
        if (overPanel && this.hovered != null && this.hovered.hasItem()) {
            graphics.setTooltipForNextFrame(font, this.hovered.getItem(), mouseX, mouseY);
        }
        ItemStack carried = this.menu.getCarried();
        if (!carried.isEmpty()) {
            graphics.item(carried, mouseX - 8, mouseY - 8);
            graphics.itemDecorations(font, carried, mouseX - 8, mouseY - 8);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, int originX, int originY, boolean doubleClick) {
        int slotIndex = slotIndexAt((int) event.x(), (int) event.y(), originX, originY);
        if (slotIndex < 0) {
            return false;
        }
        Slot slot = this.menu.getSlot(slotIndex);
        if (!slot.isActive()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        int button = event.button() == 1 ? 1 : 0;
        ContainerInput input = event.hasShiftDown() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
        activate();
        minecraft.gameMode.handleContainerInput(this.menu.containerId, slotIndex, button, input, minecraft.player);
        return true;
    }

    @Override
    public void removed() {
        // Nothing to clean up: the vanilla screen was never initialised.
    }
}
