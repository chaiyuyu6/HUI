package com.chai.hui.client.screen;

import com.chai.hui.HuiConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A small custom settings screen for HUI, opened with the {@code K} key or from the Mods menu.
 *
 * <p>It is written against the 26.3 screen API: rendering happens in {@link #extractRenderState} /
 * {@link #extractBackground} through {@link GuiGraphicsExtractor}, and the screen is pushed onto the new NeoForge
 * screen-layer stack.
 */
public final class HuiConfigScreen extends Screen {
    private static final int COLUMN_WIDTH = 150;
    private static final int WIDGET_HEIGHT = 20;
    private static final int GAP = 6;
    private static final int PANEL_WIDTH = COLUMN_WIDTH * 2 + GAP * 2;
    private static final int ROWS = 5;
    private static final int ACCENT = 0xFF3FC7FF;
    private static final int PANEL_BACKGROUND = 0xD8101218;
    private static final int HINT_COLOR = 0xFF8B98A9;

    public HuiConfigScreen() {
        super(Component.translatable("hui.screen.title"));
    }

    @Override
    protected void init() {
        int leftX = panelLeft() + GAP;
        int rightX = panelLeft() + PANEL_WIDTH - GAP - COLUMN_WIDTH;
        int step = WIDGET_HEIGHT + GAP;
        int y = contentTop();

        // --- Left column: panel appearance -------------------------------------------------
        addToggle(leftX, y, "hui.config.enabled", HuiConfig.ENABLED.get(),
                value -> HuiConfig.ENABLED.set(value));

        addAction(leftX, y + step,
                Component.translatable("hui.config.corner",
                        Component.translatable(HuiConfig.CORNER.get().translationKey())),
                () -> HuiConfig.CORNER.set(HuiConfig.CORNER.get().next()));

        addAction(leftX, y + step * 2,
                Component.translatable("hui.config.opacity", HuiConfig.BACKGROUND_OPACITY.get()),
                () -> HuiConfig.BACKGROUND_OPACITY.set((HuiConfig.BACKGROUND_OPACITY.get() + 16) % 256));

        addAction(leftX, y + step * 3,
                Component.translatable("hui.config.offset_x", HuiConfig.OFFSET_X.get()),
                () -> HuiConfig.OFFSET_X.set((HuiConfig.OFFSET_X.get() + 4) % 24));

        addAction(leftX, y + step * 4,
                Component.translatable("hui.config.offset_y", HuiConfig.OFFSET_Y.get()),
                () -> HuiConfig.OFFSET_Y.set((HuiConfig.OFFSET_Y.get() + 4) % 24));

        // --- Right column: panel contents --------------------------------------------------
        addToggle(rightX, y, "hui.config.show_fps", HuiConfig.SHOW_FPS.get(),
                value -> HuiConfig.SHOW_FPS.set(value));
        addToggle(rightX, y + step, "hui.config.show_coords", HuiConfig.SHOW_COORDS.get(),
                value -> HuiConfig.SHOW_COORDS.set(value));
        addToggle(rightX, y + step * 2, "hui.config.show_dimension", HuiConfig.SHOW_DIMENSION.get(),
                value -> HuiConfig.SHOW_DIMENSION.set(value));
        addToggle(rightX, y + step * 3, "hui.config.show_biome", HuiConfig.SHOW_BIOME.get(),
                value -> HuiConfig.SHOW_BIOME.set(value));
        addToggle(rightX, y + step * 4, "hui.config.show_time", HuiConfig.SHOW_TIME.get(),
                value -> HuiConfig.SHOW_TIME.set(value));

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(this.width / 2 - 60, doneY(), 120, WIDGET_HEIGHT)
                .build());
    }

    /** Adds a button that shows an on/off state and flips it when clicked. */
    private void addToggle(int x, int y, String labelKey, boolean current, java.util.function.Consumer<Boolean> setter) {
        this.addRenderableWidget(Button.builder(
                        Component.translatable(labelKey, onOff(current)),
                        button -> {
                            setter.accept(!current);
                            saveAndRefresh();
                        })
                .bounds(x, y, COLUMN_WIDTH, WIDGET_HEIGHT)
                .build());
    }

    /** Adds a button that displays the given value and runs {@code action} when clicked. */
    private void addAction(int x, int y, Component label, Runnable action) {
        this.addRenderableWidget(Button.builder(label, button -> {
                    action.run();
                    saveAndRefresh();
                })
                .bounds(x, y, COLUMN_WIDTH, WIDGET_HEIGHT)
                .build());
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "hui.value.on" : "hui.value.off");
    }

    private void saveAndRefresh() {
        if (HuiConfig.SPEC.isLoaded()) {
            HuiConfig.SPEC.save();
        }
        this.rebuildWidgets();
    }

    // --- Layout ---------------------------------------------------------------------------

    private int panelLeft() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelTop() {
        return Math.max(10, this.height / 2 - 132);
    }

    private int contentTop() {
        return panelTop() + 32;
    }

    private int doneY() {
        return contentTop() + ROWS * (WIDGET_HEIGHT + GAP) + 2;
    }

    private int panelBottom() {
        return doneY() + WIDGET_HEIGHT + 8;
    }

    // --- Rendering ------------------------------------------------------------------------

    @Override
    public boolean isInGameUi() {
        // Avoid the panorama/menu background when the screen is opened while playing.
        return this.minecraft != null && this.minecraft.level != null;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);

        int left = panelLeft();
        int top = panelTop();
        int right = left + PANEL_WIDTH;
        int bottom = panelBottom();
        graphics.fill(left, top, right, bottom, PANEL_BACKGROUND);
        graphics.outline(left, top, PANEL_WIDTH, bottom - top, ACCENT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.getTitle(), this.width / 2, panelTop() + 11, ACCENT);
        graphics.centeredText(this.font, Component.translatable("hui.screen.hint"),
                this.width / 2, panelBottom() + 6, HINT_COLOR);
    }

    @Override
    public void onClose() {
        if (HuiConfig.SPEC.isLoaded()) {
            HuiConfig.SPEC.save();
        }
        super.onClose();
    }
}
