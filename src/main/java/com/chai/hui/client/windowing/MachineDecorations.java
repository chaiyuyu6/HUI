package com.chai.hui.client.windowing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.InventoryMenu;
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

    @FunctionalInterface
    public interface Renderer {
        void render(GuiGraphicsExtractor graphics, AbstractContainerMenu menu, SlotPosition slotPosition, int mouseX,
                int mouseY, float partialTick);
    }

    private static final int ACCENT = 0xFF3FC7FF;
    private static final int ACCENT_GLOW = 0xFF7FD9FF;
    private static final int FUEL = 0xFFFFB74D;
    private static final int FUEL_GLOW = 0xFFFFD180;

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
