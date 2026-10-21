package com.chai.hui.client.windowing;

import java.util.ArrayList;
import java.util.List;

import com.chai.hui.HuiMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The HUI desktop: a full screen, semi transparent surface that hosts draggable windows.
 *
 * <p>Prototype scope (P0): one window, dragging by its title bar, z-order by focus, scissored embedded content and input
 * forwarding. Pages, pinning, minimizing, the window manager and persistence are described in
 * {@code docs/windowing-design.md} and will be layered on top of this class.
 */
public final class HuiDesktopScreen extends Screen {
    private static final Identifier INVENTORY_WINDOW_ID =
            Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "window.inventory");
    private static final int BACKGROUND = 0x90000000;
    private static final int WINDOW_BODY = 0xD8101418;
    private static final int WINDOW_BODY_FOCUSED = 0xE0101418;
    private static final int WINDOW_HEADER = 0xFF16202B;
    private static final int WINDOW_HEADER_FOCUSED = 0xFF1F3346;
    private static final int BORDER = 0xFF3FC7FF;
    private static final int BORDER_FOCUSED = 0xFF7FD9FF;
    private static final int TITLE_COLOR = 0xFFE6EDF3;
    private static final int HINT_COLOR = 0x80FFFFFF;

    private final List<HuiWindow> windows = new ArrayList<>();
    private @Nullable HuiWindow focused;
    private @Nullable HuiWindow dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public HuiDesktopScreen() {
        super(Component.translatable("hui.desktop.title"));
    }

    @Override
    protected void init() {
        if (this.windows.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
            HuiWindow window = new HuiWindow(INVENTORY_WINDOW_ID,
                    Component.translatable("hui.window.inventory"),
                    new EmbeddedScreenContent(this.minecraft.player));
            window.setPosition((this.width - window.width()) / 2, (this.height - window.height()) / 2);
            this.windows.add(window);
            this.focused = window;
        }
        for (HuiWindow window : this.windows) {
            window.content().init(this.width, this.height);
        }
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, BACKGROUND);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        for (HuiWindow window : this.windows) {
            renderWindow(graphics, window, mouseX, mouseY, partialTick);
        }
        graphics.centeredText(this.font, Component.translatable("hui.desktop.hint"), this.width / 2,
                this.height - 14, HINT_COLOR);
    }

    private void renderWindow(GuiGraphicsExtractor graphics, HuiWindow window, int mouseX, int mouseY,
            float partialTick) {
        boolean isFocused = window == this.focused;
        int x = window.x();
        int y = window.y();
        int width = window.width();
        int height = window.height();

        graphics.fill(x, y, x + width, y + height, isFocused ? WINDOW_BODY_FOCUSED : WINDOW_BODY);
        graphics.fill(x + 1, y + 1, x + width - 1, y + HuiWindow.TITLE_HEIGHT,
                isFocused ? WINDOW_HEADER_FOCUSED : WINDOW_HEADER);
        graphics.outline(x, y, width, height, isFocused ? BORDER_FOCUSED : BORDER);
        graphics.text(this.font, window.title(), x + 5, y + (HuiWindow.TITLE_HEIGHT - 8) / 2, TITLE_COLOR, true);

        window.content().extract(graphics, mouseX, mouseY, partialTick, window.contentX(), window.contentY());
    }

    // --- Input -----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        for (int index = this.windows.size() - 1; index >= 0; index--) {
            HuiWindow window = this.windows.get(index);
            if (!window.contains(mouseX, mouseY)) {
                continue;
            }
            bringToFront(window);
            if (window.isInTitleBar(mouseX, mouseY)) {
                this.dragging = window;
                this.dragOffsetX = mouseX - window.x();
                this.dragOffsetY = mouseY - window.y();
                return true;
            }
            return window.content().mouseClicked(event, window.contentX(), window.contentY(), doubleClick);
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging != null) {
            int newX = (int) Math.round(event.x() - this.dragOffsetX);
            int newY = (int) Math.round(event.y() - this.dragOffsetY);
            this.dragging.setPosition(clampX(this.dragging, newX), clampY(this.dragging, newY));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging != null) {
            this.dragging = null;
            return true;
        }
        return false;
    }

    private void bringToFront(HuiWindow window) {
        this.windows.remove(window);
        this.windows.add(window);
        this.focused = window;
    }

    private int clampX(HuiWindow window, int x) {
        return Math.max(-window.width() + 40, Math.min(this.width - 40, x));
    }

    private int clampY(HuiWindow window, int y) {
        return Math.max(0, Math.min(this.height - HuiWindow.TITLE_HEIGHT, y));
    }

    // --- Lifecycle -------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        for (HuiWindow window : this.windows) {
            window.content().tick();
        }
    }

    @Override
    public void removed() {
        for (HuiWindow window : this.windows) {
            window.content().removed();
        }
        this.windows.clear();
        this.focused = null;
        this.dragging = null;
    }
}
