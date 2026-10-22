package com.chai.hui.client.windowing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
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
    private static final net.minecraft.resources.Identifier SCALED_MAP_SPRITE =
            net.minecraft.resources.Identifier.withDefaultNamespace("container/cartography_table/scaled_map");
    private static final net.minecraft.resources.Identifier DUPLICATED_MAP_SPRITE =
            net.minecraft.resources.Identifier.withDefaultNamespace("container/cartography_table/duplicated_map");
    private static final net.minecraft.resources.Identifier MAP_SPRITE =
            net.minecraft.resources.Identifier.withDefaultNamespace("container/cartography_table/map");
    private static final net.minecraft.resources.Identifier LOCKED_SPRITE =
            net.minecraft.resources.Identifier.withDefaultNamespace("container/cartography_table/locked");

    // ===== 可调参数（想微调动画/候选列表的位置就改这里） =====
    /** 熔炉火焰相对燃料槽的 inset（像素）。 */
    private static final int FURNACE_FLAME_INSET = 18;
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
        if (menu instanceof CraftingMenu) {
            return MachineDecorations::renderCrafting;
        }
        if (menu instanceof GrindstoneMenu) {
            return MachineDecorations::renderGrindstone;
        }

        if (menu instanceof AnvilMenu) {
            return MachineDecorations::renderAnvil;
        }
        if (menu instanceof SmithingMenu) {
            return MachineDecorations::renderSmithing;
        }
        if (menu instanceof CartographyTableMenu cartography) {
            return new Renderer() {
                private final MapRenderState mapRenderState = new MapRenderState();

                @Override
                public void render(GuiGraphicsExtractor graphics, AbstractContainerMenu ignored,
                        SlotPosition slotPosition, int mouseX, int mouseY, float partialTick) {
                    renderCartography(graphics, cartography, slotPosition, this.mapRenderState);
                }
            };
        }
        if (menu instanceof EnchantmentMenu enchantment) {
            return new Renderer() {
                @Override
                public void render(GuiGraphicsExtractor graphics, AbstractContainerMenu ignored,
                        SlotPosition slotPosition, int mouseX, int mouseY, float partialTick) {
                    renderEnchanting(graphics, enchantment, slotPosition, mouseX, mouseY);
                }

                @Override
                public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
                        AbstractContainerMenu ignored, SlotPosition slotPosition) {
                    return clickEnchanting(event, enchantment, slotPosition);
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

    // --- crafting table / anvil / smithing table: HUI plus signs and static arrows -----------

    private static void drawPlus(GuiGraphicsExtractor graphics, int centerX, int centerY, int color) {
        graphics.fill(centerX - 1, centerY - 5, centerX + 1, centerY + 6, color);
        graphics.fill(centerX - 5, centerY - 1, centerX + 6, centerY + 1, color);
    }

    private static void drawArrow(GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
        int body = Math.max(4, width - 4);
        graphics.fill(x, y - 2, x + body, y + 2, color);
        for (int step = 0; step < 4; step++) {
            graphics.fill(x + body + step, y - (4 - step), x + body + step + 1, y + (4 - step), color);
        }
    }

    private static void renderCrafting(GuiGraphicsExtractor graphics, AbstractContainerMenu menu,
            SlotPosition slotPosition, int mouseX, int mouseY, float partialTick) {
        int[] gridRight = slotPosition.get(3);
        int[] middle = slotPosition.get(5);
        int[] result = slotPosition.get(0);
        int startX = gridRight[0] + 20;
        int width = result[0] - 4 - startX;
        if (width > 6) {
            drawArrow(graphics, startX, middle[1] + 8, width, ACCENT);
        }
    }

    private static void renderGrindstone(GuiGraphicsExtractor graphics, AbstractContainerMenu menu,
            SlotPosition slotPosition, int mouseX, int mouseY, float partialTick) {
        int[] input = slotPosition.get(GrindstoneMenu.INPUT_SLOT);
        int[] additional = slotPosition.get(GrindstoneMenu.ADDITIONAL_SLOT);
        int[] result = slotPosition.get(GrindstoneMenu.RESULT_SLOT);
        int startX = Math.max(input[0], additional[0]) + 20;
        int width = result[0] - 4 - startX;
        if (width > 6) {
            drawArrow(graphics, startX, result[1] + 8, width, ACCENT);
        }
    }


    private static void renderAnvil(GuiGraphicsExtractor graphics, AbstractContainerMenu menu,
            SlotPosition slotPosition, int mouseX, int mouseY, float partialTick) {
        int[] first = slotPosition.get(0);
        int[] second = slotPosition.get(1);
        int[] result = slotPosition.get(2);
        drawPlus(graphics, (first[0] + second[0] + 16) / 2, first[1] + 8, 0xFF8CA3B4);
        int startX = second[0] + 20;
        int width = result[0] - 4 - startX;
        if (width > 6) {
            drawArrow(graphics, startX, second[1] + 8, width, ACCENT);
        }
    }

    private static void renderSmithing(GuiGraphicsExtractor graphics, AbstractContainerMenu menu,
            SlotPosition slotPosition, int mouseX, int mouseY, float partialTick) {
        int[] template = slotPosition.get(SmithingMenu.TEMPLATE_SLOT);
        int[] base = slotPosition.get(SmithingMenu.BASE_SLOT);
        int[] addition = slotPosition.get(SmithingMenu.ADDITIONAL_SLOT);
        int[] result = slotPosition.get(SmithingMenu.RESULT_SLOT);
        drawPlus(graphics, (template[0] + base[0] + 16) / 2, template[1] + 8, 0xFF8CA3B4);
        drawPlus(graphics, (base[0] + addition[0] + 16) / 2, base[1] + 8, 0xFF8CA3B4);
        int startX = addition[0] + 20;
        int width = result[0] - 4 - startX;
        if (width > 6) {
            drawArrow(graphics, startX, addition[1] + 8, width, ACCENT);
        }
    }

    private static void renderCartography(GuiGraphicsExtractor graphics, CartographyTableMenu menu,
            SlotPosition slotPosition, MapRenderState mapRenderState) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack mapStack = menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem();
        ItemStack additional = menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem();
        MapId mapId = mapStack.get(DataComponents.MAP_ID);
        MapItemSavedData mapData = mapId == null ? null : MapItem.getSavedData(mapId, minecraft.level);
        boolean duplication = additional.is(Items.MAP);
        boolean scaling = additional.is(Items.PAPER);
        boolean locking = additional.is(Items.GLASS_PANE);
        boolean locked = mapData != null && mapData.locked;

        int[] base = slotPosition.get(CartographyTableMenu.MAP_SLOT);
        int px = base[0] + 40;
        int py = base[1];
        if (scaling && !locked) {
            graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, SCALED_MAP_SPRITE, px, py,
                    66, 66);
            renderMap(graphics, mapId, mapData, mapRenderState, px + 18, py + 18, 0.226F);
        } else if (duplication) {
            graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, DUPLICATED_MAP_SPRITE,
                    px + 16, py, 50, 66);
            renderMap(graphics, mapId, mapData, mapRenderState, px + 19, py + 3, 0.34F);
            graphics.nextStratum();
            graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, DUPLICATED_MAP_SPRITE, px,
                    py + 16, 50, 66);
            renderMap(graphics, mapId, mapData, mapRenderState, px + 3, py + 19, 0.34F);
        } else if (locking) {
            graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, MAP_SPRITE, px, py, 66, 66);
            renderMap(graphics, mapId, mapData, mapRenderState, px + 4, py + 4, 0.45F);
            graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOCKED_SPRITE, px + 51,
                    py + 47, 10, 14);
        } else {
            graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, MAP_SPRITE, px, py, 66, 66);
            renderMap(graphics, mapId, mapData, mapRenderState, px + 4, py + 4, 0.45F);
        }
    }

    private static void renderMap(GuiGraphicsExtractor graphics, MapId mapId, MapItemSavedData mapData,
            MapRenderState mapRenderState, int x, int y, float scale) {
        if (mapId == null || mapData == null) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        Minecraft.getInstance().getMapRenderer().extractRenderState(mapId, mapData, mapRenderState);
        graphics.map(mapRenderState);
        graphics.pose().popMatrix();
    }

    /** Layout for the cartography table window: map + additional slot on the left, map preview, result on the right. */
    public static int[] cartographySlotPosition(net.minecraft.world.inventory.Slot slot) {
        if (slot.index == CartographyTableMenu.MAP_SLOT) {
            return new int[] {8, 8};
        }
        if (slot.index == CartographyTableMenu.ADDITIONAL_SLOT) {
            return new int[] {8, 46};
        }
        if (slot.index == CartographyTableMenu.RESULT_SLOT) {
            return new int[] {160, 8};
        }
        return new int[] {8, 8};
    }
    // --- enchanting table ---------------------------------------------------------------------

    /** Layout for the enchanting table window: item slot + lapis slot on the left, three offer rows. */
    public static int[] enchantingSlotPosition(net.minecraft.world.inventory.Slot slot) {
        if (slot.index == 0) {
            return new int[] {8, 52};
        }
        if (slot.index == 1) {
            return new int[] {28, 52};
        }
        return new int[] {8, 52};
    }

    private static int[] enchantingOfferBase(SlotPosition slotPosition) {
        int[] lapis = slotPosition.get(1);
        return new int[] {lapis[0] + 16, lapis[1] - 44};
    }

    private static boolean enchantingOfferEnabled(EnchantmentMenu menu, int index) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        int cost = menu.costs[index];
        if (cost <= 0 || menu.enchantClue[index] == -1) {
            return false;
        }
        boolean hasLevels = minecraft.player.experienceLevel >= cost
                || minecraft.player.getAbilities().instabuild;
        boolean hasLapis = menu.getGoldCount() > index || minecraft.player.getAbilities().instabuild;
        return hasLevels && hasLapis;
    }

    private static void renderEnchanting(GuiGraphicsExtractor graphics, EnchantmentMenu menu,
            SlotPosition slotPosition, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        int[] base = enchantingOfferBase(slotPosition);
        for (int index = 0; index < 3; index++) {
            int x = base[0];
            int y = base[1] + index * 19;
            boolean enabled = enchantingOfferEnabled(menu, index);
            graphics.fill(x, y, x + 108, y + 18, enabled ? 0x443FC7FF : 0x22000000);
            graphics.outline(x, y, 108, 18, enabled ? ACCENT : 0xFF2F4353);
            if (menu.costs[index] > 0) {
                String name = EnchantmentNames.getInstance().getRandomName(font, menu.levelClue[index]).getString();
                graphics.text(font, name, x + 3, y + 5, enabled ? 0xFFE6EDF3 : 0xFF8CA3B4);
                String cost = String.valueOf(menu.costs[index]);
                graphics.text(font, cost, x + 108 - 4 - font.width(cost), y + 5,
                        enabled ? 0xFFFFD180 : 0xFF8CA3B4);
                if (mouseX >= x && mouseX < x + 108 && mouseY >= y && mouseY < y + 18) {
                    graphics.setComponentTooltipForNextFrame(font, enchantingTooltip(menu, index), mouseX, mouseY);
                }
            }
        }
    }

    /** Vanilla style enchantment hint tooltip (name, lapis cost, level cost and requirement). */
    private static java.util.List<Component> enchantingTooltip(EnchantmentMenu menu, int index) {
        Minecraft minecraft = Minecraft.getInstance();
        java.util.List<Component> texts = new java.util.ArrayList<>();
        if (minecraft.player == null || minecraft.level == null) {
            return texts;
        }
        java.util.Optional<net.minecraft.core.Holder.Reference<Enchantment>> enchant = minecraft.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).get(menu.enchantClue[index]);
        texts.add(Component.translatable("container.enchant.clue",
                enchant.isEmpty() ? "" : Enchantment.getFullname(enchant.get(), menu.levelClue[index]))
                .withStyle(ChatFormatting.WHITE));
        if (enchant.isEmpty()) {
            texts.add(Component.literal(""));
            texts.add(Component.translatable("neoforge.container.enchant.limitedEnchantability")
                    .withStyle(ChatFormatting.RED));
        }
        int lapisCost = index + 1;
        boolean creative = minecraft.player.getAbilities().instabuild;
        boolean hasLapis = menu.getGoldCount() >= lapisCost || creative;
        boolean hasLevels = minecraft.player.experienceLevel >= menu.costs[index] || creative;
        Component lapisLine = lapisCost == 1 ? Component.translatable("container.enchant.lapis.one")
                : Component.translatable("container.enchant.lapis.many", lapisCost);
        texts.add(lapisLine.copy().withStyle(hasLapis ? ChatFormatting.GRAY : ChatFormatting.RED));
        Component levelLine = menu.costs[index] == 1 ? Component.translatable("container.enchant.level.one")
                : Component.translatable("container.enchant.level.many", menu.costs[index]);
        texts.add(levelLine.copy().withStyle(hasLevels ? ChatFormatting.GRAY : ChatFormatting.RED));
        if (!hasLevels) {
            texts.add(Component.translatable("container.enchant.level.requirement", menu.costs[index])
                    .withStyle(ChatFormatting.RED));
        }
        return texts;
    }

    private static boolean clickEnchanting(net.minecraft.client.input.MouseButtonEvent event, EnchantmentMenu menu,
            SlotPosition slotPosition) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        int[] base = enchantingOfferBase(slotPosition);
        for (int index = 0; index < 3; index++) {
            int x = base[0];
            int y = base[1] + index * 19;
            if (event.x() >= x && event.x() < x + 108 && event.y() >= y && event.y() < y + 18
                    && enchantingOfferEnabled(menu, index)) {
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
        // Armor slot (index 5) marks the top left of the vanilla inventory screen; the preview sits to its right.
        int reference = Math.min(5, menu.slots.size() - 1);
        int[] anchor = slotPosition.get(reference);
        int left = anchor[0] + 18;
        int top = anchor[1];
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, left, top, left + 49, top + 70, 30, 0.0625F,
                mouseX, mouseY, minecraft.player);
    }
}
