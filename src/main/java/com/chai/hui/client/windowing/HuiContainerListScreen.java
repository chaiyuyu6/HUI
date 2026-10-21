package com.chai.hui.client.windowing;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The "open container" list: every nearby block entity that can provide a menu, with a search box.
 *
 * <p>Picking an entry asks the server to open that container and closes this screen; the resulting container screen is
 * turned into a HUI window by the client hooks.
 */
public final class HuiContainerListScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 3;
    private static final int MAX_ROWS = 10;
    private static final int ACCENT = 0xFF3FC7FF;
    private static final int PANEL_BACKGROUND = 0xE8101418;
    private static final int MUTED = 0xFF8B98A9;

    private final List<HuiRemoteContainers.ContainerEntry> entries;
    private String query = "";
    private EditBox search;
    private int panelTop;
    private int panelHeight;

    public HuiContainerListScreen() {
        super(Component.translatable("hui.container_list.title"));
        this.entries = HuiRemoteContainers.findNearby(Minecraft.getInstance());
    }

    @Override
    protected void init() {
        List<HuiRemoteContainers.ContainerEntry> filtered = filtered();
        int rows = Math.min(filtered.size(), MAX_ROWS);
        this.panelHeight = 34 + 24 + Math.max(rows, 1) * (ROW_HEIGHT + ROW_GAP) + 12;
        this.panelTop = Math.max(10, (this.height - this.panelHeight) / 2);

        int left = this.width / 2 - PANEL_WIDTH / 2 + 10;
        int contentWidth = PANEL_WIDTH - 20;
        this.search = new EditBox(this.font, left, this.panelTop + 22, contentWidth, 18,
                Component.translatable("hui.container_list.search"));
        this.search.setHint(Component.translatable("hui.container_list.search"));
        this.search.setMaxLength(64);
        this.search.setValue(this.query);
        this.addRenderableWidget(this.search);
        this.setFocused(this.search);
        this.search.setFocused(true);

        int y = this.panelTop + 22 + 24;
        for (int index = 0; index < rows; index++) {
            HuiRemoteContainers.ContainerEntry entry = filtered.get(index);
            Component label = Component.translatable("hui.container_list.entry",
                    entry.name().getString(), String.format(Locale.ROOT, "%.1f", entry.distance()));
            this.addRenderableWidget(Button.builder(label, button -> {
                        HuiRemoteContainers.request(entry.pos());
                        this.onClose();
                    })
                    .bounds(left, y, contentWidth, ROW_HEIGHT)
                    .build());
            y += ROW_HEIGHT + ROW_GAP;
        }

        this.search.setResponder(text -> {
            if (!text.equals(this.query)) {
                this.query = text;
                this.rebuildWidgets();
            }
        });
    }

    private List<HuiRemoteContainers.ContainerEntry> filtered() {
        if (this.query.isBlank()) {
            return this.entries;
        }
        String needle = this.query.toLowerCase(Locale.ROOT);
        return this.entries.stream()
                .filter(entry -> entry.name().getString().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x99000000);
        int left = this.width / 2 - PANEL_WIDTH / 2;
        graphics.fill(left, this.panelTop, left + PANEL_WIDTH, this.panelTop + this.panelHeight, PANEL_BACKGROUND);
        graphics.outline(left, this.panelTop, PANEL_WIDTH, this.panelHeight, ACCENT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.getTitle(), this.width / 2, this.panelTop + 8, ACCENT);
        if (filtered().isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("hui.container_list.empty"), this.width / 2,
                    this.panelTop + 60, MUTED);
        }
    }
}
