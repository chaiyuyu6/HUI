package com.chai.hui.client.windowing;

import com.chai.hui.HuiMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Embeds a vanilla {@link InventoryScreen}'s contents inside a window.
 *
 * <p>26.3's GUI is a render-state extraction model, so the panel is drawn at the window origin and the screen's own
 * content (slots, labels, widgets) is rendered with a translated pose and a scissor rectangle; mouse coordinates are
 * transformed back into the screen's coordinate space before being forwarded. The carried stack and the tooltip are
 * drawn outside the scissor at the desktop cursor position.
 *
 * <p>This is the P0 feasibility prototype: it proves that "translate + scissor + forward input" works for standard
 * container screens, which is the approach planned for block entity windows.
 */
public final class EmbeddedScreenContent implements WindowContent {
    private static final Identifier INVENTORY_TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final int WIDTH = 176;
    private static final int HEIGHT = 166;

    private final Player player;
    private final EmbeddableInventoryScreen screen;
    private boolean initialized;

    public EmbeddedScreenContent(Player player) {
        this.player = player;
        this.screen = new EmbeddableInventoryScreen(player);
    }

    @Override
    public int preferredWidth() {
        return WIDTH;
    }

    @Override
    public int preferredHeight() {
        return HEIGHT;
    }

    @Override
    public void init(int screenWidth, int screenHeight) {
        this.screen.init(screenWidth, screenHeight);
        this.initialized = true;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        if (!this.initialized) {
            return;
        }

        int leftPos = this.screen.getLeftPos();
        int topPos = this.screen.getTopPos();
        int localMouseX = mouseX - originX + leftPos;
        int localMouseY = mouseY - originY + topPos;

        // Panel and player preview at the window origin; the screen's slots/labels are translated to match below.
        graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE, originX, originY, 0.0F, 0.0F, WIDTH, HEIGHT, 256,
                256);
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, originX + 26, originY + 8, originX + 75,
                originY + 78, 30, 0.0625F, localMouseX, localMouseY, this.player);

        graphics.enableScissor(originX, originY, originX + WIDTH, originY + HEIGHT);
        graphics.pose().pushMatrix();
        graphics.pose().translate(originX - leftPos, originY - topPos);
        try {
            this.screen.extractContents(graphics, localMouseX, localMouseY, partialTick);
        } finally {
            graphics.pose().popMatrix();
            graphics.disableScissor();
        }

        // Carried stack and tooltip belong to the desktop cursor, not to the window region.
        this.screen.extractCarriedItem(graphics, mouseX, mouseY);
        this.screen.extractTooltipPublic(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, int originX, int originY, boolean doubleClick) {
        return this.screen.mouseClicked(toScreenSpace(event, originX, originY), doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, int originX, int originY, double dragX, double dragY) {
        return this.screen.mouseDragged(toScreenSpace(event, originX, originY), dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event, int originX, int originY) {
        return this.screen.mouseReleased(toScreenSpace(event, originX, originY));
    }

    @Override
    public void tick() {
        if (this.initialized) {
            this.screen.containerTickPublic();
        }
    }

    @Override
    public void removed() {
        this.screen.removed();
    }

    private MouseButtonEvent toScreenSpace(MouseButtonEvent event, int originX, int originY) {
        double x = event.x() - originX + this.screen.getLeftPos();
        double y = event.y() - originY + this.screen.getTopPos();
        return new MouseButtonEvent(x, y, event.buttonInfo());
    }

    /** Exposes the protected pieces of {@link InventoryScreen} needed by the embedder. */
    private static final class EmbeddableInventoryScreen extends InventoryScreen {
        private EmbeddableInventoryScreen(Player player) {
            super(player);
        }

        private void extractTooltipPublic(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            this.extractTooltip(graphics, mouseX, mouseY);
        }

        private void containerTickPublic() {
            this.containerTick();
        }
    }
}
