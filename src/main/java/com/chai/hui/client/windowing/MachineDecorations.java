package com.chai.hui.client.windowing;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * HUI style decorations for the vanilla container widgets that {@link VanillaMenuUiContent} does not draw itself:
 * furnace fuel flame and cooking arrow, brewing stand bubbles/arrow/fuel bar and the survival inventory player preview.
 *
 * <p>Positions are given in vanilla screen coordinates and translated to the HUI slot layout with the slot offset.
 */
public final class MachineDecorations {
    @FunctionalInterface
    public interface Renderer {
        void render(GuiGraphicsExtractor graphics, AbstractContainerMenu menu, int originX, int originY, int minX,
                int minY, int mouseX, int mouseY, float partialTick);
    }

    private static final int[] BUBBLE_LENGTHS = {29, 24, 20, 16, 11, 6, 0};
    private static final Identifier FURNACE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final Identifier BLAST_FURNACE =
            Identifier.withDefaultNamespace("textures/gui/container/blast_furnace.png");
    private static final Identifier SMOKER = Identifier.withDefaultNamespace("textures/gui/container/smoker.png");
    private static final Identifier FURNACE_LIT = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final Identifier FURNACE_BURN = Identifier.withDefaultNamespace("container/furnace/burn_progress");
    private static final Identifier BLAST_LIT = Identifier.withDefaultNamespace("container/blast_furnace/lit_progress");
    private static final Identifier BLAST_BURN = Identifier.withDefaultNamespace("container/blast_furnace/burn_progress");
    private static final Identifier SMOKER_LIT = Identifier.withDefaultNamespace("container/smoker/lit_progress");
    private static final Identifier SMOKER_BURN = Identifier.withDefaultNamespace("container/smoker/burn_progress");
    private static final Identifier BREW_FUEL =
            Identifier.withDefaultNamespace("container/brewing_stand/fuel_length");
    private static final Identifier BREW_PROGRESS =
            Identifier.withDefaultNamespace("container/brewing_stand/brew_progress");
    private static final Identifier BREW_BUBBLES = Identifier.withDefaultNamespace("container/brewing_stand/bubbles");

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

    private static void renderFurnace(GuiGraphicsExtractor graphics, AbstractFurnaceMenu menu, int originX, int originY,
            int minX, int minY) {
        Identifier litSprite;
        Identifier burnSprite;
        if (menu instanceof BlastFurnaceMenu) {
            litSprite = BLAST_LIT;
            burnSprite = BLAST_BURN;
        } else if (menu instanceof SmokerMenu) {
            litSprite = SMOKER_LIT;
            burnSprite = SMOKER_BURN;
        } else if (menu instanceof FurnaceMenu) {
            litSprite = FURNACE_LIT;
            burnSprite = FURNACE_BURN;
        } else {
            litSprite = FURNACE_LIT;
            burnSprite = FURNACE_BURN;
        }
        int xo = originX + (56 - minX);
        int yo = originY + (36 - minY);
        if (menu.isLit()) {
            int height = Mth.ceil(menu.getLitProgress() * 13.0F) + 1;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, litSprite, 14, 14, 0, 14 - height, xo,
                    yo + 14 - height, 14, height);
        }
        int width = Mth.ceil(menu.getBurnProgress() * 24.0F);
        if (width > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, burnSprite, 24, 16, 0, 0,
                    originX + (79 - minX), originY + (34 - minY), width, 16);
        }
    }

    private static void renderBrewing(GuiGraphicsExtractor graphics, BrewingStandMenu menu, int originX, int originY,
            int minX, int minY) {
        int fuel = menu.getFuel();
        int totalFuel = menu.getTotalFuel();
        int fuelLength = totalFuel > 0 ? Mth.clamp(Mth.positiveCeilDiv(18 * fuel, totalFuel), 0, 18) : 0;
        if (fuelLength > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BREW_FUEL, 18, 4, 0, 0,
                    originX + (60 - minX), originY + (44 - minY), fuelLength, 4);
        }
        int brewingTicks = menu.getBrewingTicks();
        int totalTicks = menu.getTotalBrewingTicks();
        if (brewingTicks > 0 && totalTicks > 0) {
            int length = (int) (28.0F * (1.0F - (float) brewingTicks / totalTicks));
            if (length > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BREW_PROGRESS, 9, 28, 0, 0,
                        originX + (97 - minX), originY + (16 - minY), 9, length);
            }
            int bubbleLength = BUBBLE_LENGTHS[brewingTicks / 2 % 7];
            if (bubbleLength > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BREW_BUBBLES, 12, 29, 0, 29 - bubbleLength,
                        originX + (63 - minX), originY + (14 + 29 - bubbleLength - minY), 12, bubbleLength);
            }
        }
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

    /** Whether a slot should show vanilla style hints (used to avoid drawing HUI slot frames twice). */
    public static boolean hasVanillaHint(AbstractContainerMenu menu, int slotIndex) {
        return false;
    }

    /** Unused helper kept for future per-slot hints. */
    public static NonNullList<ItemStack> emptyList() {
        return NonNullList.create();
    }
}
