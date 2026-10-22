package com.chai.hui.client.hud.editor;

import java.util.ArrayList;
import java.util.List;

import com.chai.hui.client.hud.HudBlock;
import com.chai.hui.client.hud.HudLayout;
import com.chai.hui.client.hud.HudLayoutManager;
import com.chai.hui.client.hud.HudRect;
import com.chai.hui.client.ui.ContextMenu;
import com.chai.hui.client.screen.HuiConfigScreen;
import com.chai.hui.client.screen.HuiScreens;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The full screen HUD layout editor.
 *
 * <p>Flow (see also the feature notes in the repository):
 * <ul>
 *   <li>left click a block to select it and drag it around,</li>
 *   <li>right click a block to hide/show it or bring it to the front,</li>
 *   <li>right click empty space to save &amp; exit, cancel, or open the HUI settings,</li>
 *   <li>{@code ESC} behaves like "cancel".</li>
 * </ul>
 *
 * <p>The editor edits a working copy of the layout; "save &amp; exit" commits it, everything else discards it.
 */
public final class HudEditorScreen extends Screen {
    private static final int BACKGROUND = 0xB0000000;
    /** Idle outline of a block; kept subtle because some blocks (chat) cover a large part of the screen. */
    private static final int FRAME = 0x50FFFFFF;
    private static final int FRAME_HOVER = 0xFFFFFFFF;
    private static final int ACCENT = 0xFF3FC7FF;
    private static final int HIDDEN_OVERLAY = 0x99000000;
    private static final int HIDDEN_TEXT = 0xFFFF7070;
    /** The hint fades in once, stays briefly and fades out; it does not keep pulsing. */
    private static final int HINT_FADE_IN_MS = 250;
    private static final int HINT_HOLD_MS = 2200;
    private static final int HINT_FADE_OUT_MS = 700;
    private static final int HINT_MAX_ALPHA = 220;
    /** Distance of the hint baseline from the bottom edge; keeps it clear of the vanilla hotbar. */
    private static final int HINT_BOTTOM_OFFSET = 56;
    private static final int CORNER_LENGTH = 6;

    private final HudLayoutManager manager = HudLayoutManager.get();
    private final long openedAt = Util.getMillis();

    private @Nullable ContextMenu menu;
    private @Nullable HudBlock selected;
    private @Nullable HudBlock dragging;
    private double dragStartX;
    private double dragStartY;
    private int dragStartOffsetX;
    private int dragStartOffsetY;

    public HudEditorScreen() {
        super(Component.translatable("hui.editor.title"));
        this.manager.beginEditing();
    }

    @Override
    public boolean isInGameUi() {
        return this.minecraft != null && this.minecraft.level != null;
    }

    // --- Rendering -------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Full screen, semi transparent black, like the other in-game GUIs.
        graphics.fill(0, 0, this.width, this.height, BACKGROUND);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        HudLayout layout = this.manager.layout();

        renderBlocks(graphics, layout);
        renderFrames(graphics, layout, mouseX, mouseY);
        renderTitle(graphics);
        renderHint(graphics);

        if (this.menu != null) {
            this.menu.render(graphics, this.font, mouseX, mouseY);
        }
    }

    private void renderBlocks(GuiGraphicsExtractor graphics, HudLayout layout) {
        for (HudBlock block : this.manager.blocksInDrawOrder(layout, this.width, this.height)) {
            this.manager.renderBlock(graphics, this.minecraft.getDeltaTracker(), block, layout);
            if (layout.isHidden(block.id())) {
                HudRect rect = this.manager.boundsOf(block, layout, this.width, this.height);
                graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), HIDDEN_OVERLAY);
            }
        }
    }

    private void renderFrames(GuiGraphicsExtractor graphics, HudLayout layout, int mouseX, int mouseY) {
        HudBlock hovered = this.manager.blockAt(mouseX, mouseY, layout, this.width, this.height);

        for (HudBlock block : this.manager.blocksInDrawOrder(layout, this.width, this.height)) {
            HudRect rect = this.manager.boundsOf(block, layout, this.width, this.height);
            boolean isSelected = this.selected != null && this.selected.id().equals(block.id());
            boolean isHovered = hovered != null && hovered.id().equals(block.id());
            int frameColor = isSelected ? ACCENT : isHovered ? FRAME_HOVER : FRAME;

            graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), frameColor);
            if (isSelected) {
                drawCorners(graphics, rect);
            }

            // Only label the block the player is looking at or dragging; permanent labels of the blocks in the
            // bottom centre would overlap each other.
            if (isSelected || isHovered) {
                Component label = Component.translatable("hui.editor.block_label",
                        block.label(),
                        layout.offsetX(block.id()),
                        layout.offsetY(block.id()));
                graphics.text(this.font, label, rect.x(), Math.max(2, rect.y() - 11), frameColor, true);
            }

            if (layout.isHidden(block.id())) {
                graphics.text(this.font, Component.translatable("hui.editor.hidden"),
                        rect.x(), rect.y() + 2, HIDDEN_TEXT, true);
            }
        }
    }

    private static void drawCorners(GuiGraphicsExtractor graphics, HudRect rect) {
        graphics.fill(rect.x(), rect.y(), rect.x() + CORNER_LENGTH, rect.y() + 1, ACCENT);
        graphics.fill(rect.x(), rect.y(), rect.x() + 1, rect.y() + CORNER_LENGTH, ACCENT);
        graphics.fill(rect.right() - CORNER_LENGTH, rect.y(), rect.right(), rect.y() + 1, ACCENT);
        graphics.fill(rect.right() - 1, rect.y(), rect.right(), rect.y() + CORNER_LENGTH, ACCENT);
        graphics.fill(rect.x(), rect.bottom() - 1, rect.x() + CORNER_LENGTH, rect.bottom(), ACCENT);
        graphics.fill(rect.x(), rect.bottom() - CORNER_LENGTH, rect.x() + 1, rect.bottom(), ACCENT);
        graphics.fill(rect.right() - CORNER_LENGTH, rect.bottom() - 1, rect.right(), rect.bottom(), ACCENT);
        graphics.fill(rect.right() - 1, rect.bottom() - CORNER_LENGTH, rect.right(), rect.bottom(), ACCENT);
    }

    private void renderTitle(GuiGraphicsExtractor graphics) {
        graphics.centeredText(this.font, this.getTitle(), this.width / 2, 12, ACCENT);
    }

    /** Hint above the vanilla hotbar that softly fades in and out, as requested by the design notes. */
    /** Draws the control hint once: fade in, hold, fade out, then stay hidden. */
    private void renderHint(GuiGraphicsExtractor graphics) {
        if (this.menu != null) {
            return;
        }
        long elapsed = Util.getMillis() - this.openedAt;
        int holdEnd = HINT_FADE_IN_MS + HINT_HOLD_MS;
        int fadeOutEnd = holdEnd + HINT_FADE_OUT_MS;
        if (elapsed >= fadeOutEnd) {
            return;
        }

        float progress;
        if (elapsed < HINT_FADE_IN_MS) {
            progress = elapsed / (float) HINT_FADE_IN_MS;
        } else if (elapsed < holdEnd) {
            progress = 1.0F;
        } else {
            progress = 1.0F - (elapsed - holdEnd) / (float) HINT_FADE_OUT_MS;
        }
        int alpha = Math.max(0, Math.min(HINT_MAX_ALPHA, (int) (progress * HINT_MAX_ALPHA)));
        int color = (alpha << 24) | 0xFFFFFF;

        Component hint = Component.translatable("hui.editor.hint");
        int hintWidth = this.font.width(hint);
        int hintX = (this.width - hintWidth) / 2;
        int hintY = this.height - HINT_BOTTOM_OFFSET;
        // A backdrop keeps the hint readable above the bright vanilla HUD.
        graphics.textWithBackdrop(this.font, hint, hintX, hintY, hintWidth, color);
    }

    // --- Input -----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.menu != null) {
            ContextMenu.Entry entry = this.menu.entryAt(event.x(), event.y());
            this.menu = null;
            if (entry != null && entry.enabled()) {
                entry.action().run();
            }
            return true;
        }

        HudLayout layout = this.manager.layout();
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            openMenu(event.x(), event.y(), layout);
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            HudBlock hit = this.manager.blockAt(event.x(), event.y(), layout, this.width, this.height);
            this.selected = hit;
            this.dragging = hit;
            if (hit != null) {
                this.dragStartX = event.x();
                this.dragStartY = event.y();
                this.dragStartOffsetX = layout.offsetX(hit.id());
                this.dragStartOffsetY = layout.offsetY(hit.id());
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        HudBlock block = this.dragging;
        if (block == null) {
            return super.mouseDragged(event, dragX, dragY);
        }
        HudLayout layout = this.manager.layout();
        int offsetX = this.dragStartOffsetX + (int) Math.round(event.x() - this.dragStartX);
        int offsetY = this.dragStartOffsetY + (int) Math.round(event.y() - this.dragStartY);
        layout.setOffset(block.id(), offsetX, offsetY);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = null;
        return super.mouseReleased(event);
    }

    private void openMenu(double mouseX, double mouseY, HudLayout layout) {
        HudBlock hit = this.manager.blockAt(mouseX, mouseY, layout, this.width, this.height);
        this.selected = hit;

        List<ContextMenu.Entry> entries;
        if (hit != null) {
            boolean hidden = layout.isHidden(hit.id());
            entries = new ArrayList<>(3);
            entries.add(ContextMenu.Entry.of(hidden ? "hui.menu.show" : "hui.menu.hide",
                    () -> layout.setHidden(hit.id(), !hidden)));
            // Auto-detected layers are rendered by NeoForge's own pipeline to keep third-party HUD order intact, so
            // only blocks HUI renders itself can be brought to the front.
            if (hit.orderable()) {
                entries.add(ContextMenu.Entry.of("hui.menu.bring_front",
                        () -> layout.bringToFront(hit.id())));
            }
            entries.add(ContextMenu.Entry.of("hui.menu.reset_block",
                    () -> this.manager.resetBlock(layout, hit.id())));
        } else {
            entries = new ArrayList<>(4);
            entries.add(ContextMenu.Entry.of("hui.menu.save_exit", this::saveAndClose));
            entries.add(ContextMenu.Entry.of("hui.menu.cancel", this::cancelAndClose));
            entries.add(ContextMenu.Entry.of("hui.menu.reset_all", () -> this.manager.resetAll(layout)));
            entries.add(ContextMenu.Entry.of("hui.menu.hui_config", this::openHuiConfig));
        }
        this.menu = new ContextMenu(this.font, (int) mouseX, (int) mouseY, this.width, this.height, entries);
    }

    // --- Closing ---------------------------------------------------------------------------

    private void saveAndClose() {
        this.manager.commit();
        super.onClose();
    }

    private void cancelAndClose() {
        this.manager.cancel();
        super.onClose();
    }

    private void openHuiConfig() {
        HuiScreens.open(this.minecraft, new HuiConfigScreen());
    }

    /** {@code ESC} discards the working layout, exactly like the "cancel" menu entry. */
    @Override
    public void onClose() {
        this.manager.cancel();
        super.onClose();
    }
}
