package com.chai.hui.client.windowing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.jetbrains.annotations.Nullable;

/**
 * HUI style decorations for the container widgets that {@link VanillaMenuUiContent} does not draw itself.
 *
 * <p>Progress values are drawn as HUI fills without any track border: the furnace flame glows inside its fuel slot and
 * the cooking progress is a bare accent bar between the input and the result slot; the brewing stand shows a bare
 * progress bar and rising HUI dots. Positions are derived from the HUI slot layout, not from the vanilla screen pixels.
 */
public final class MachineDecorations {
    /** Returns the screen position [x, y] of a menu slot index (supplied by {@link VanillaMenuUiContent}). */
    @FunctionalInterface
    public interface SlotPosition {
        int[] get(int slotIndex);
    }

    public interface Renderer {
        void render(GuiGraphicsExtractor graphics, AbstractContainerMenu menu, SlotPosition slotPosition, int mouseX,
                int mouseY, float partialTick);

        /** Optional: handle a click on a decoration; return true when the click was consumed. */
        default boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, AbstractContainerMenu menu,
                SlotPosition slotPosition) {
            return false;
        }
    }

    private static final int ACCENT = 0xFF3FC7FF;
    private static final int ACCENT_GLOW = 0xFF7FD9FF;
    private static final int FUEL = 0xFFFFB74D;
    private static final int FUEL_GLOW = 0xFFFFD180;

    // ===== 可调参数（想微调动画/候选列表的位置就改这里） =====
    /** 熔炉火焰相对燃料槽的 inset（像素）。 */
    private static final int FURNACE_FLAME_INSET = 2;
    /** 烧炼进度条离输入槽右边的距离。 */
    private static final int FURNACE_BAR_LEFT_GAP = 21;
    /** 烧炼进度条离产出槽左边的距离。 */
    private static final int FURNACE_BAR_RIGHT_GAP = 3;
    /** 烧炼进度条相对产出槽中心的 Y 偏移。 */
    private static final int FURNACE_BAR_Y_OFFSET = 7;
    /** 酿造燃料条相对燃料槽的 inset。 */
    private static final int BREW_FUEL_INSET_X = 3;
    private static final int BREW_FUEL_INSET_Y = 12;
    /** 酿造进度条相对材料槽右边缘的偏移。 */
    private static final int BREW_BAR_OFFSET_X = 22;
    private static final int BREW_BAR_OFFSET_Y = -2;
    /** 切石机候选格：相对输入槽的偏移、格子尺寸与可见数量。 */
    private static final int STONECUTTER_GRID_OFFSET_X = 24;
    private static final int STONECUTTER_GRID_OFFSET_Y = 0;
    private static final int STONECUTTER_CELL_W = 16;
    private static final int STONECUTTER_CELL_H = 18;
    private static final int STONECUTTER_COLUMNS = 4;
    private static final int STONECUTTER_VISIBLE = 12;

    private MachineDecorations() {
    }

    public static @Nullable Renderer forMenu(AbstractContainerMenu menu) {
        if (menu instanceof AbstractFurnaceMenu furnace) {
            return (graphics, ignored, slotPosition, mouseX, mouseY, partialTick) -> renderFurnace(graphics, furnace,
                    slotPosition);
        }
        if (menu instanceof BrewingStandMenu brewing) {
            return (graphics, ignored, slotPosition, mouseX, mouseY, partialTick) -> renderBrewing(graphics, brewing,
                    slotPosition);
        }
        if (menu instanceof StonecutterMenu stonecutter) {
            return new Renderer() {
                @Override
                public void render(GuiGraphicsExtractor graphics, AbstractContainerMenu ignored, SlotPosition slotPosition,
                        int mouseX, int mouseY, float partialTick) {
                    renderStonecutter(graphics, stonecutter, slotPosition, mouseX, mouseY);
                }

                @Override
                public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
                        AbstractContainerMenu ignored, SlotPosition slotPosition) {
                    return clickStonecutter(event, stonecutter, slotPosition);
                }
            };
        }
        if (menu instanceof InventoryMenu) {
            return MachineDecorations::renderPlayerPreview;
        }
        return null;
    }

    private static float wave() {
        return 0.5F + 0.5F * (float) Math.sin(System.nanoTime() / 180_000_000.0);
    }

    private static int withAlpha(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, alpha)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static void renderFurnace(GuiGraphicsExtractor graphics, AbstractFurnaceMenu menu,
            SlotPosition slotPosition) {
        boolean lit = menu.isLit();
        float litProgress = menu.getLitProgress();
        float burn = menu.getBurnProgress();

        // Flame inside the fuel slot: a bare amber fill with a soft pulse, no track or border.
        int[] fuel = slotPosition.get(AbstractFurnaceMenu.FUEL_SLOT);
        int flameX = fuel[0] + 2;
        int flameTop = fuel[1] + 2;
        int flameW = 14;
        int flameH = 14;
        graphics.fill(flameX, flameTop, flameX + flameW, flameTop + flameH, 0x22000000);
        int filled = Math.round(flameH * litProgress);
        if (filled > 0) {
            int bottom = flameTop + flameH;
            graphics.fill(flameX + 1, bottom - filled, flameX + flameW - 1, bottom,
                    withAlpha(FUEL, 0.55F + 0.15F * wave()));
            graphics.fill(flameX + 3, bottom - filled + 2, flameX + flameW - 3, bottom - 1,
                    withAlpha(FUEL_GLOW, lit ? 0.85F : 0.55F));
            if (lit) {
                int sparkY = bottom - filled + (int) ((filled - 2) * (1.0F - wave()));
                graphics.fill(flameX + 5, sparkY, flameX + flameW - 5, sparkY + 1,
                        withAlpha(FUEL_GLOW, 0.95F));
            }
        }

        // Cooking progress: a bare accent bar in the gap between the input and the result slot.
        int[] input = slotPosition.get(AbstractFurnaceMenu.INGREDIENT_SLOT);
        int[] result = slotPosition.get(AbstractFurnaceMenu.RESULT_SLOT);
        int barLeft = input[0] + 18 + 3;
        int barRight = result[0] - 3;
        if (barRight - barLeft < 8) {
            barLeft = input[0] + 18 + 1;
            barRight = Math.max(barLeft + 8, result[0] - 1);
        }
        int barWidth = barRight - barLeft;
        // Align the bar with the result slot so it visually points at the output.
        int barY = result[1] + 7;
        graphics.fill(barLeft, barY, barRight, barY + 3, 0x33000000);
        int fill = Math.round(barWidth * burn);
        if (fill > 0) {
            graphics.fill(barLeft, barY, barLeft + fill, barY + 3, withAlpha(ACCENT, 0.85F));
            int shimmerX = barLeft + (int) Math.max(0, (fill - 3) * wave());
            graphics.fill(shimmerX, barY, shimmerX + 2, barY + 3, withAlpha(ACCENT_GLOW, 0.95F));
        }
        if (burn >= 1.0F) {
            graphics.fill(barRight, barY - 1, barRight + 2, barY + 4,
                    withAlpha(ACCENT_GLOW, 0.45F + 0.45F * wave()));
        }
    }

    private static void renderBrewing(GuiGraphicsExtractor graphics, BrewingStandMenu menu,
            SlotPosition slotPosition) {
        int fuel = menu.getFuel();
        int totalFuel = menu.getTotalFuel();
        int[] fuelSlot = slotPosition.get(4);
        int fuelX = fuelSlot[0] + 3;
        int fuelY = fuelSlot[1] + 12;
        if (totalFuel > 0 && fuel > 0) {
            int width = Math.max(1, Math.min(12, Math.round(12.0F * fuel / totalFuel)));
            graphics.fill(fuelX, fuelY, fuelX + width, fuelY + 2, withAlpha(FUEL, 0.9F));
        }

        int brewingTicks = menu.getBrewingTicks();
        int totalTicks = menu.getTotalBrewingTicks();
        if (brewingTicks > 0 && totalTicks > 0) {
            float progress = 1.0F - (float) brewingTicks / totalTicks;
            int[] ingredient = slotPosition.get(3);
            int barX = ingredient[0] + 22;
            int barTop = ingredient[1] - 2;
            int barHeight = 26;
            graphics.fill(barX, barTop, barX + 4, barTop + barHeight, 0x33000000);
            int filled = Math.round(barHeight * progress);
            if (filled > 0) {
                graphics.fill(barX, barTop + barHeight - filled, barX + 4, barTop + barHeight,
                        withAlpha(ACCENT, 0.85F));
                int shimmerY = barTop + barHeight - filled + (int) Math.max(0, (filled - 2) * wave());
                graphics.fill(barX, shimmerY, barX + 4, shimmerY + 2, withAlpha(ACCENT_GLOW, 0.95F));
            }

            int[] bottle = slotPosition.get(0);
            int bubbles = 1 + (int) (3 * wave());
            for (int index = 0; index < bubbles; index++) {
                float offset = (wave() + index * 0.33F) % 1.0F;
                int dotX = bottle[0] + 3 + index * 4;
                int dotY = bottle[1] + 16 - (int) (14 * offset);
                graphics.fill(dotX, dotY, dotX + 2, dotY + 2, withAlpha(ACCENT_GLOW, 0.25F + 0.6F * (1 - offset)));
            }
        }
    }

    /** Layout used by the stone cutter window: input on the left, result on the right, candidate grid in between. */
    public static int[] stonecutterSlotPosition(net.minecraft.world.inventory.Slot slot) {
        if (slot.index == StonecutterMenu.INPUT_SLOT) {
            return new int[] {8, 8};
        }
        if (slot.index == StonecutterMenu.RESULT_SLOT) {
            return new int[] {160, 8};
        }
        return new int[] {8, 8};
    }

    private static int[] stonecutterGridBase(SlotPosition slotPosition) {
        int[] input = slotPosition.get(StonecutterMenu.INPUT_SLOT);
        return new int[] {input[0] + STONECUTTER_GRID_OFFSET_X, input[1] + STONECUTTER_GRID_OFFSET_Y};
    }

    private static void renderStonecutter(GuiGraphicsExtractor graphics, StonecutterMenu menu,
            SlotPosition slotPosition, int mouseX, int mouseY) {
        if (!menu.hasInputItem()) {
            return;
        }
        var recipes = menu.getVisibleRecipes();
        int count = menu.getNumberOfVisibleRecipes();
        if (count <= 0) {
            return;
        }
        var context = SlotDisplayContext.fromLevel(Minecraft.getInstance().level);
        int selected = menu.getSelectedRecipeIndex();
        int[] base = stonecutterGridBase(slotPosition);
        int visible = Math.min(STONECUTTER_VISIBLE, count);
        for (int index = 0; index < visible; index++) {
            int x = base[0] + (index % STONECUTTER_COLUMNS) * STONECUTTER_CELL_W;
            int y = base[1] + (index / STONECUTTER_COLUMNS) * STONECUTTER_CELL_H;
            boolean hover = mouseX >= x && mouseX < x + STONECUTTER_CELL_W && mouseY >= y
                    && mouseY < y + STONECUTTER_CELL_H;
            graphics.fill(x, y, x + STONECUTTER_CELL_W, y + STONECUTTER_CELL_H,
                    hover ? 0x553FC7FF : 0x22000000);
            graphics.outline(x, y, STONECUTTER_CELL_W, STONECUTTER_CELL_H,
                    index == selected ? ACCENT_GLOW : 0x33FFFFFF);
            SlotDisplay display = recipes.entries().get(index).recipe().optionDisplay();
            ItemStack stack = display.resolveForFirstStack(context);
            if (!stack.isEmpty()) {
                graphics.item(stack, x, y + 1);
                graphics.itemDecorations(Minecraft.getInstance().font, stack, x, y + 1);
                if (hover) {
                    graphics.setTooltipForNextFrame(Minecraft.getInstance().font, stack, mouseX, mouseY);
                }
            }
        }
    }

    private static boolean clickStonecutter(net.minecraft.client.input.MouseButtonEvent event, StonecutterMenu menu,
            SlotPosition slotPosition) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !menu.hasInputItem()) {
            return false;
        }
        int count = Math.min(STONECUTTER_VISIBLE, menu.getNumberOfVisibleRecipes());
        int[] base = stonecutterGridBase(slotPosition);
        for (int index = 0; index < count; index++) {
            int x = base[0] + (index % STONECUTTER_COLUMNS) * STONECUTTER_CELL_W;
            int y = base[1] + (index / STONECUTTER_COLUMNS) * STONECUTTER_CELL_H;
            if (event.x() >= x && event.x() < x + STONECUTTER_CELL_W && event.y() >= y
                    && event.y() < y + STONECUTTER_CELL_H) {
                if (menu.clickMenuButton(minecraft.player, index)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
                    return true;
                }
            }
        }
        return false;
    }

    private static void renderPlayerPreview(GuiGraphicsExtractor graphics, AbstractContainerMenu menu,
            SlotPosition slotPosition, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        int[] crafting = slotPosition.get(0);
        int left = crafting[0] + 18;
        int top = crafting[1] + 2;
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, left, top, left + 49, top + 70, 30, 0.0625F,
                mouseX, mouseY, minecraft.player);
    }
}
