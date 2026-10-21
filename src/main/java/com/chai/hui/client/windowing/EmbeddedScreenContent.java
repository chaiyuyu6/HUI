package com.chai.hui.client.windowing;

import java.lang.reflect.Method;

import com.chai.hui.network.ActivateMenuPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Embeds an arbitrary {@link AbstractContainerScreen} inside a window.
 *
 * <p>Vanilla GUI is a render-state extraction model, so the content is rendered with a translated pose and a scissor
 * rectangle; mouse coordinates are transformed back into the screen's coordinate space before being forwarded. A
 * viewport ({@code srcX/srcY/srcWidth/srcHeight} relative to the screen's {@code leftPos/topPos}) decides which part of
 * the screen is shown, which is how the "default inventory" window crops the vanilla inventory panel down to the
 * 27 storage slots + hotbar row.
 *
 * <p>The carried stack and tooltip are drawn outside the scissor at the desktop cursor position.
 */
public final class EmbeddedScreenContent implements WindowContent {
    private static final Identifier INVENTORY_TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final int INVENTORY_WIDTH = 176;
    private static final int INVENTORY_HEIGHT = 166;

    /** Vanilla inventory panel region that contains the 27 storage slots plus the hotbar row. */
    private static final int CROP_X = 7;
    private static final int CROP_Y = 83;
    private static final int CROP_WIDTH = 162;
    private static final int CROP_HEIGHT = 76;

    /**
     * The creative tab row sits 28 GUI pixels above the panel; using exactly that as the top margin keeps the window
     * flush with the title bar (a larger margin showed as an empty black band above the tabs).
     */
    private static final int CREATIVE_TOP_MARGIN = 28;
    private static final int CREATIVE_BOTTOM_MARGIN = 28;

    private static final @Nullable Method EXTRACT_TOOLTIP = findTooltipMethod();

    /** Paints a screen background at the window origin; {@code localMouse*} is in the embedded screen's space. */
    public interface BackgroundPainter {
        void paint(GuiGraphicsExtractor graphics, int originX, int originY, int localMouseX, int localMouseY,
                float partialTick);
    }

    /**
     * A block entity container window: the vanilla container screen cropped to the container's own slots, so the player
     * inventory part of the screen is not shown (requirement: "裁掉物品栏" for block entity windows in HUI mode).
     */
    public static EmbeddedScreenContent blockEntity(AbstractContainerScreen<?> screen) {
        return blockEntity(screen, null);
    }

    public static EmbeddedScreenContent blockEntity(AbstractContainerScreen<?> screen,
            net.minecraft.core.BlockPos sourcePos) {
        int[] crop = containerCrop(screen);
        EmbeddedScreenContent content = new EmbeddedScreenContent(screen, crop[0], crop[1], crop[2], crop[3], null);
        content.sourcePos = sourcePos;
        return content;
    }

    public @Nullable net.minecraft.core.BlockPos sourcePos() {
        return this.sourcePos;
    }

    private static int[] containerCrop(AbstractContainerScreen<?> screen) {
        AbstractContainerMenu menu = screen.getMenu();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) {
                continue;
            }
            minX = Math.min(minX, slot.x);
            minY = Math.min(minY, slot.y);
            maxX = Math.max(maxX, slot.x);
            maxY = Math.max(maxY, slot.y);
        }
        int imageWidth = screen.getImageWidth();
        int imageHeight = screen.getImageHeight();
        if (minX == Integer.MAX_VALUE) {
            return new int[] {0, 0, imageWidth, imageHeight};
        }
        int left = Math.max(0, minX - 3);
        // The window chrome already shows the container title, so crop away the panel label and show only slots.
        int top = Math.max(0, minY - 3);
        int right = Math.min(imageWidth, maxX + 20);
        int bottom = Math.min(imageHeight, maxY + 18);
        return new int[] {left, top, right - left, bottom - top};
    }

    /** @return the embedded menu, used by the desktop for carried stack bookkeeping. */
    public AbstractContainerMenu screenMenu() {
        return this.screen.getMenu();
    }

    /** @return the container id of the embedded menu (0 for the player inventory). */
    public int menuContainerId() {
        return this.screen.getMenu().containerId;
    }

    /** @return true when this menu also exists on the server (false for the client-only creative inventory). */
    public boolean serverBacked() {
        return this.serverBacked;
    }

    /** Makes this window's menu the active server side menu, so vanilla click packets are routed to it. */
    public void activate() {
        int containerId = menuContainerId();
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketDistributor.sendToServer(new ActivateMenuPayload(containerId));
        if (minecraft.player != null) {
            minecraft.player.containerMenu = this.screen.getMenu();
        }
    }
    /** The full survival inventory window (crafting, armor, offhand, player preview). */
    public static EmbeddedScreenContent survival(Player player) {
        return new EmbeddedScreenContent(new InventoryScreen(player), 0, 0, INVENTORY_WIDTH, INVENTORY_HEIGHT,
                (graphics, originX, originY, localMouseX, localMouseY, partialTick) -> {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE, originX, originY, 0.0F, 0.0F,
                            INVENTORY_WIDTH, INVENTORY_HEIGHT, 256, 256);
                    InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, originX + 26, originY + 8,
                            originX + 75, originY + 78, 30, 0.0625F, localMouseX, localMouseY, player);
                });
    }

    /** Cropped "default inventory" window: only the 27 storage slots and the hotbar row of the player inventory. */
    public static EmbeddedScreenContent croppedInventory(Player player) {
        return new EmbeddedScreenContent(new InventoryScreen(player), CROP_X, CROP_Y, CROP_WIDTH, CROP_HEIGHT,
                (graphics, originX, originY, localMouseX, localMouseY, partialTick) -> graphics.blit(
                        RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE, originX - CROP_X, originY - CROP_Y, 0.0F,
                        0.0F, INVENTORY_WIDTH, INVENTORY_HEIGHT, 256, 256));
    }

    /** The creative inventory window, including its tab rows above and below the panel. */
    public static EmbeddedScreenContent creative(LocalPlayer player) {
        CreativeModeInventoryScreen screen = new CreativeModeInventoryScreen(player,
                player.connection.enabledFeatures(), Minecraft.getInstance().options.operatorItemsTab().get());
        return new EmbeddedScreenContent(screen, 0, -CREATIVE_TOP_MARGIN, screen.getImageWidth(),
                screen.getImageHeight() + CREATIVE_TOP_MARGIN + CREATIVE_BOTTOM_MARGIN, null, false);
    }

    private final AbstractContainerScreen<?> screen;
    private final int srcX;
    private final int srcY;
    private final int srcWidth;
    private final int srcHeight;
    private final @Nullable BackgroundPainter background;
    /** True when the menu also exists on the server (inventory and block entity menus); creative is client only. */
    private final boolean serverBacked;
    private boolean initialized;
    private @Nullable net.minecraft.core.BlockPos sourcePos;

    public EmbeddedScreenContent(AbstractContainerScreen<?> screen, int srcX, int srcY, int srcWidth, int srcHeight,
            @Nullable BackgroundPainter background) {
        this(screen, srcX, srcY, srcWidth, srcHeight, background, true);
    }

    private EmbeddedScreenContent(AbstractContainerScreen<?> screen, int srcX, int srcY, int srcWidth, int srcHeight,
            @Nullable BackgroundPainter background, boolean serverBacked) {
        this.screen = screen;
        this.srcX = srcX;
        this.srcY = srcY;
        this.srcWidth = srcWidth;
        this.srcHeight = srcHeight;
        this.background = background;
        this.serverBacked = serverBacked;
    }

    @Override
    public int preferredWidth() {
        return this.srcWidth;
    }

    @Override
    public int preferredHeight() {
        return this.srcHeight;
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

        int deltaX = this.deltaX(originX);
        int deltaY = this.deltaY(originY);
        int localMouseX = mouseX - deltaX;
        int localMouseY = mouseY - deltaY;

        graphics.enableScissor(originX, originY, originX + this.srcWidth, originY + this.srcHeight);
        if (this.background != null) {
            // Custom backgrounds are painted in window space, not in the embedded screen's space.
            this.background.paint(graphics, originX, originY, localMouseX, localMouseY, partialTick);
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(deltaX, deltaY);
        try {
            if (this.background == null) {
                this.screen.extractBackground(graphics, localMouseX, localMouseY, partialTick);
            }
            this.screen.extractContents(graphics, localMouseX, localMouseY, partialTick);
        } finally {
            graphics.pose().popMatrix();
            graphics.disableScissor();
        }

        // Carried stack and tooltip belong to the desktop cursor, not to the window region.
        this.screen.extractCarriedItem(graphics, mouseX, mouseY);
        extractTooltip(this.screen, graphics, mouseX, mouseY);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, int originX,
            int originY) {
        return this.screen.mouseScrolled(mouseX - this.deltaX(originX), mouseY - this.deltaY(originY), scrollX,
                scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return this.screen.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return this.screen.charTyped(event);
    }

    @Override
    public void tick() {
        if (this.initialized) {
            this.screen.tick();
        }
    }

    @Override
    public void removed() {
        this.screen.removed();
    }

    private MouseButtonEvent toScreenSpace(MouseButtonEvent event, int originX, int originY) {
        return new MouseButtonEvent(event.x() - this.deltaX(originX), event.y() - this.deltaY(originY),
                event.buttonInfo());
    }

    private int deltaX(int originX) {
        return originX - (this.screen.getLeftPos() + this.srcX);
    }

    private int deltaY(int originY) {
        return originY - (this.screen.getTopPos() + this.srcY);
    }

    private static @Nullable Method findTooltipMethod() {
        try {
            Method method = AbstractContainerScreen.class.getDeclaredMethod("extractTooltip",
                    GuiGraphicsExtractor.class, int.class, int.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static void extractTooltip(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX,
            int mouseY) {
        if (EXTRACT_TOOLTIP == null) {
            return;
        }
        try {
            EXTRACT_TOOLTIP.invoke(screen, graphics, mouseX, mouseY);
        } catch (ReflectiveOperationException ignored) {
            // Tooltips are cosmetic; never break rendering because of the reflective call.
        }
    }
}
