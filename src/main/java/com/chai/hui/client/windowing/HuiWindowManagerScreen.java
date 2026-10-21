package com.chai.hui.client.windowing;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Lists every window on the current page (including minimized ones) and lets the player bring one back to the front.
 *
 * <p>P1 scope: current page, first 8 entries; search and cross-page grouping are planned together with the window
 * manager overhaul.
 */
public final class HuiWindowManagerScreen extends Screen {
    private static final int ENTRY_WIDTH = 260;
    private static final int ENTRY_HEIGHT = 20;
    private static final int ENTRY_GAP = 4;
    private static final int MAX_ENTRIES = 8;
    private static final int ACCENT = 0xFF3FC7FF;
    private static final int PANEL_BACKGROUND = 0xE8101418;
    private static final int MUTED = 0xFF8B98A9;

    private final HuiDesktopScreen desktop;
    private int panelTop;
    private int panelHeight;
    private int visibleEntries;
    private boolean empty;

    public HuiWindowManagerScreen(HuiDesktopScreen desktop) {
        super(Component.translatable("hui.window_manager.title"));
        this.desktop = desktop;
    }

    @Override
    protected void init() {
        List<HuiWindow> windows = this.desktop.allWindows();
        this.visibleEntries = Math.min(windows.size(), MAX_ENTRIES);
        this.empty = windows.isEmpty();
        int rowsHeight = this.empty ? 30 : this.visibleEntries * (ENTRY_HEIGHT + ENTRY_GAP) - ENTRY_GAP;
        this.panelHeight = 34 + Math.max(rowsHeight, 20) + 14;
        this.panelTop = Math.max(10, (this.height - this.panelHeight) / 2);

        int x = this.width / 2 - ENTRY_WIDTH / 2;
        int y = this.panelTop + 34;
        for (int index = 0; index < this.visibleEntries; index++) {
            HuiWindow target = windows.get(index);
            Component label = target.minimized()
                    ? Component.translatable("hui.window_manager.entry_minimized", target.title())
                    : target.title();
            this.addRenderableWidget(Button.builder(label, button -> {
                        this.desktop.focusWindow(target);
                        this.onClose();
                    })
                    .bounds(x, y, ENTRY_WIDTH, ENTRY_HEIGHT)
                    .build());
            y += ENTRY_HEIGHT + ENTRY_GAP;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x99000000);
        int left = this.width / 2 - ENTRY_WIDTH / 2 - 10;
        int right = this.width / 2 + ENTRY_WIDTH / 2 + 10;
        graphics.fill(left, this.panelTop, right, this.panelTop + this.panelHeight, PANEL_BACKGROUND);
        graphics.outline(left, this.panelTop, right - left, this.panelHeight, ACCENT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.getTitle(), this.width / 2, this.panelTop + 10, ACCENT);
        if (this.empty) {
            graphics.centeredText(this.font, Component.translatable("hui.window_manager.empty"), this.width / 2,
                    this.panelTop + 38, MUTED);
        }
    }
}
