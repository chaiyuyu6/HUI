package com.chai.hui.client.windowing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SmokerMenu;
import org.jetbrains.annotations.Nullable;

/**
 * HUI style decorations for the vanilla container widgets that {@link VanillaMenuUiContent} does not draw itself.
 *
 * <p>Instead of blitting the vanilla progress sprites, HUI draws its own animated bars/flames: a pulsing fill, a moving
 * highlight and the theme colours. Positions use the vanilla screen coordinates translated to the HUI slot layout.
 */
public final class MachineDecorations {
    @FunctionalInterface
    public interface Renderer {
        void render(GuiGraphicsExtractor graphics, AbstractContainerMenu menu, int originX, int originY, int minX,
                int minY, int mouseX, int mouseY, float partialTick);
    }

    private static final int TRACK = 0xFF0B0F14;
    private static final int TRACK_BORDER = 0xFF2F4353;
    private static final int ACCENT = 0xFF3FC7FF;
    private static final int ACCENT_GLOW = 0xFF7FD9FF;
    private static final int FUEL = 0xFFFFB74D;
    private static final int FUEL_GLOW = 0xFFFFD180;

    private MachineDecorations() {
    }

    public static @Nullable Renderer forMenu(AbstractContainerMenu menu) {
        if (menu instanceof AbstractFurnaceMenu furnace) {
            return (graphics, ignored, originX, originY, minX, minY, mouseX, mouseY, partialTick) -> renderFurnace(
                    graphics, furnace, originX, originY, minX, minY);
        }
        if (menu instanceof BrewingStandMenu brewing) {
            return (graphics, ignored, originX, originY, minX, minY, mouseX, mouseY, partialTick) -> renderBrewing(
                    graphics, brewing, originX, originY, minX, minY);
        }
        if (menu instanceof InventoryMenu) {
            return MachineDecorations::renderPlayerPreview;
        }
        return null;
    }

    private static float wave() {
        // 0..1 pulsing value shared by the HUI progress animations.
        return 0.5F + 0.5F * (float) Math.sin(System.nanoTime() / 180_000_000.0);
    }

    private static int withAlpha(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, alpha)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static void renderFurnace(GuiGraphicsExtractor graphics, AbstractFurnaceMenu menu, int originX,
            int originY, int minX, int minY) {
        float burn = menu.getBurnProgress();
        boolean lit = menu.isLit();
        float litProgress = menu.getLitProgress();

        // Fuel meter: vertical HUI bar at the vanilla flame position.
        int fuelX = originX + (56 - minX);
        int fuelY = originY + (36 - minY);
        drawTrack(graphics, fuelX, fuelY, 14, 14);
        int fuelHeight = Math.round(13.0F * litProgress);
        if (fuelHeight > 0) {
            float glow = lit ? 0.65F + 0.35F * wave() : 0.5F;
            int color = lit ? withAlpha(FUEL_GLOW, 0.35F + 0.25F * wave()) : withAlpha(FUEL, 0.6F);
            graphics.fill(fuelX + 2, fuelY + 2 + (12 - fuelHeight), fuelX + 12, fuelY + 2 + 12,
                    withAlpha(FUEL, 0.75F));
            graphics.fill(fuelX + 3, fuelY + 3 + (12 - fuelHeight), fuelX + 11,
                    fuelY + 3 + 12, withAlpha(color, glow));
            if (lit) {
                // A small spark travelling up the fill.
                int sparkY = fuelY + 2 + (12 - fuelHeight) + (int) ((12 - fuelHeight) * (1.0F - wave()));
                graphics.fill(fuelX + 5, sparkY, fuelX + 9, sparkY + 1, withAlpha(FUEL_GLOW, 0.9F));
            }
        }

        // Cooking arrow: horizontal HUI bar at the vanilla arrow position with a moving highlight.
        int arrowX = originX + (79 - minX);
        int arrowY = originY + (34 - minY);
        int barY = arrowY + 6;
        drawTrack(graphics, arrowX, barY, 24, 5);
        int fillWidth = Math.round(23.0F * burn);
        if (fillWidth > 0) {
            graphics.fill(arrowX + 1, barY + 1, arrowX + 1 + fillWidth, barY + 4, withAlpha(ACCENT, 0.8F));
            int shimmerX = arrowX + 1 + (int) ((fillWidth - 2) * wave());
            graphics.fill(shimmerX, barY + 1, shimmerX + 2, barY + 4, withAlpha(ACCENT_GLOW, 0.9F));
        }
        // Tip glow when cooking is done.
        if (burn >= 1.0F) {
            graphics.fill(arrowX + 24, barY - 1, arrowX + 27, barY + 6, withAlpha(ACCENT_GLOW, 0.5F + 0.5F * wave()));
        }
    }

    private static void renderBrewing(GuiGraphicsExtractor graphics, BrewingStandMenu menu, int originX, int originY,
            int minX, int minY) {
        // Fuel bar: horizontal HUI bar at the vanilla fuel position.
        int fuel = menu.getFuel();
        int totalFuel = menu.getTotalFuel();
        int fuelX = originX + (60 - minX);
        int fuelY = originY + (44 - minY);
        drawTrack(graphics, fuelX, fuelY, 18, 4);
        if (totalFuel > 0 && fuel > 0) {
            int width = Math.max(1, Math.min(17, Math.round(17.0F * fuel / totalFuel)));
            graphics.fill(fuelX + 1, fuelY + 1, fuelX + 1 + width, fuelY + 3, withAlpha(FUEL, 0.85F));
        }

        int brewingTicks = menu.getBrewingTicks();
        int totalTicks = menu.getTotalBrewingTicks();
        if (brewingTicks > 0 && totalTicks > 0) {
            // Brew progress: vertical bar where the vanilla arrow is, filling from the bottom.
            float progress = 1.0F - (float) brewingTicks / totalTicks;
            int barX = originX + (99 - minX);
            int barY = originY + (18 - minY);
            drawTrack(graphics, barX, barY, 5, 26);
            int filled = Math.round(24.0F * progress);
            if (filled > 0) {
                graphics.fill(barX + 1, barY + 1 + (24 - filled), barX + 4, barY + 25,
                        withAlpha(ACCENT, 0.85F));
                int shimmerY = barY + 1 + (24 - filled) + (int) ((filled - 2) * wave());
                graphics.fill(barX + 1, shimmerY, barX + 4, shimmerY + 2, withAlpha(ACCENT_GLOW, 0.9F));
            }

            // Bubbles: three rising HUI dots instead of the vanilla bubble sprite.
            int bubbles = (int) (1 + 3 * wave());
            for (int index = 0; index < bubbles; index++) {
                float offset = (wave() + index * 0.33F) % 1.0F;
                int dotX = originX + (64 - minX) + index * 4;
                int dotY = originY + (40 - minY) - (int) (18 * offset);
                graphics.fill(dotX, dotY, dotX + 2, dotY + 2, withAlpha(ACCENT_GLOW, 0.35F + 0.5F * (1 - offset)));
            }
        }
    }

    private static void drawTrack(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, TRACK);
        graphics.outline(x, y, width, height, TRACK_BORDER);
    }

    private static void renderPlayerPreview(GuiGraphicsExtractor graphics, AbstractContainerMenu menu, int originX,
            int originY, int minX, int minY, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        int left = originX + (26 - minX);
        int top = originY + (8 - minY);
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, left, top, left + 49, top + 70, 30, 0.0625F,
                mouseX, mouseY, minecraft.player);
    }
}
