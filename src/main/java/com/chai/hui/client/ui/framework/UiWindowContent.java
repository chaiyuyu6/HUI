package com.chai.hui.client.ui.framework;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.chai.hui.client.windowing.WindowContent;
import com.chai.hui.client.windowing.ui.GuiTextures;
import org.jetbrains.annotations.Nullable;

/**
 * A HUI window whose content is a {@link UiNode} tree: flex layout, scroll containers, hover/click/right click/key
 * events and keyframe animations. This is the entry point mods use to build web-like GUIs.
 */
public final class UiWindowContent implements WindowContent {
    private final UiNode root;
    private long lastTickNanos = System.nanoTime();
    private int lastOriginX;
    private int lastOriginY;
    private int lastMouseX;
    private int lastMouseY;
    private boolean mouseInside;
    private @Nullable java.util.function.BiConsumer<GuiGraphicsExtractor, int[]> overlay;
    public boolean lastClickShift;
    public int lastClickButton;

    public UiWindowContent(UiNode root) {
        this.root = root;
    }

    /** Extra overlay drawing (e.g. the carried stack) after all windows. */
    public void setOverlay(java.util.function.BiConsumer<GuiGraphicsExtractor, int[]> renderer) {
        this.overlay = renderer;
    }

    public UiNode root() {
        return this.root;
    }

    @Override
    public int preferredWidth() {
        Font font = Minecraft.getInstance().font;
        return Math.max(this.root.style.minWidth, measure(font, this.root, 10000, 10000)[0]);
    }

    @Override
    public int preferredHeight() {
        Font font = Minecraft.getInstance().font;
        return Math.max(this.root.style.minHeight, measure(font, this.root, 10000, 10000)[1]);
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        this.lastOriginX = originX;
        this.lastOriginY = originY;
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        int[] size = measure(Minecraft.getInstance().font, this.root, this.preferredWidth(), this.preferredHeight());
        layout(this.root, originX, originY, size[0], size[1]);
        updateHover(mouseX, mouseY);
        draw(graphics, this.root, mouseX, mouseY, 1.0F);
    }

    @Override
    public void extractOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        Font font = Minecraft.getInstance().font;
        UiNode hovered = hitTest(mouseX, mouseY);
        if (this.overlay != null) {
            this.overlay.accept(graphics, new int[] {mouseX, mouseY});
        }
        if (hovered != null && hovered.tooltipSupplier != null) {
            String tooltip = hovered.tooltipSupplier.get();
            if (tooltip != null && !tooltip.isBlank()) {
                graphics.setTooltipForNextFrame(font, net.minecraft.network.chat.Component.literal(tooltip), mouseX,
                        mouseY);
            }
        }
    }

    @Override
    public void tick() {
        long now = System.nanoTime();
        float delta = Math.min(0.1F, (now - this.lastTickNanos) / 1_000_000_000.0F);
        this.lastTickNanos = now;
        this.root.tick(delta);
    }

    // --- Layout ------------------------------------------------------------------------------

    private static int[] measure(Font font, UiNode node, int availableWidth, int availableHeight) {
        UiStyle style = node.style;
        int width = resolve(style.width, style.widthPercent, availableWidth);
        int height = resolve(style.height, style.heightPercent, availableHeight);

        int innerWidth = width - style.paddingX * 2;
        int innerHeight = height - style.paddingY * 2;

        if (!node.children.isEmpty()) {
            int main = 0;
            int cross = 0;
            int autoCount = 0;
            boolean row = style.direction == UiStyle.Direction.ROW;
            for (UiNode child : node.children) {
                int[] childSize = measure(font, child, Math.max(1, innerWidth), Math.max(1, innerHeight));
                if (row) {
                    if (child.style.width < 0 && child.style.widthPercent < 0) {
                        autoCount++;
                    } else {
                        main += childSize[0];
                    }
                    cross = Math.max(cross, childSize[1]);
                } else {
                    if (child.style.height < 0 && child.style.heightPercent < 0) {
                        autoCount++;
                    } else {
                        main += childSize[1];
                    }
                    cross = Math.max(cross, childSize[0]);
                }
            }
            int gaps = style.gap * Math.max(0, node.children.size() - 1);
            if (row) {
                if (width < 0) {
                    width = main + gaps + style.paddingX * 2;
                    if (autoCount > 0) {
                        width += autoCount * 40;
                    }
                }
                if (height < 0) {
                    height = cross + style.paddingY * 2;
                }
            } else {
                if (height < 0) {
                    height = main + gaps + style.paddingY * 2;
                    if (autoCount > 0) {
                        height += autoCount * 16;
                    }
                }
                if (width < 0) {
                    width = cross + style.paddingX * 2;
                }
            }
        } else if (node.measure != null) {
            int[] measured = node.measure.apply(font);
            if (width < 0) {
                width = measured[0] + style.paddingX * 2;
            }
            if (height < 0) {
                height = measured[1] + style.paddingY * 2;
            }
        } else if (node.text != null) {
            String text = node.text.get();
            if (width < 0) {
                width = (text == null ? 0 : font.width(text)) + style.paddingX * 2;
            }
            if (height < 0) {
                height = font.lineHeight + style.paddingY * 2;
            }
        } else if (node.image != null) {
            if (width < 0) {
                width = node.imageWidth + style.paddingX * 2;
            }
            if (height < 0) {
                height = node.imageHeight + style.paddingY * 2;
            }
        } else {
            if (width < 0) {
                width = style.paddingX * 2;
            }
            if (height < 0) {
                height = style.paddingY * 2;
            }
        }
        width = Math.max(width, style.minWidth);
        height = Math.max(height, style.minHeight);
        return new int[] {width, height};
    }

    private static int resolve(int fixed, double percent, int available) {
        if (fixed >= 0) {
            return fixed;
        }
        if (percent >= 0) {
            return (int) Math.round(available * percent);
        }
        return -1;
    }

    private static void layout(UiNode node, int x, int y, int width, int height) {
        node.x = x;
        node.y = y;
        node.width = width;
        node.height = height;
        UiStyle style = node.style;
        int contentX = x + style.paddingX;
        int contentY = y + style.paddingY;
        int contentWidth = width - style.paddingX * 2;
        int contentHeight = height - style.paddingY * 2;
        boolean row = style.direction == UiStyle.Direction.ROW;
        int mainSize = row ? contentWidth : contentHeight;
        int crossSize = row ? contentHeight : contentWidth;

        List<int[]> childSizes = new ArrayList<>();
        int fixedMain = 0;
        int autoCount = 0;
        for (UiNode child : node.children) {
            int[] size = measure(Minecraft.getInstance().font, child, contentWidth, contentHeight);
            childSizes.add(size);
            int main = row ? size[0] : size[1];
            boolean auto = row ? child.style.width < 0 && child.style.widthPercent < 0
                    : child.style.height < 0 && child.style.heightPercent < 0;
            if (auto) {
                autoCount++;
            } else {
                fixedMain += main;
            }
        }
        int gaps = style.gap * Math.max(0, node.children.size() - 1);
        int autoSpace = autoCount > 0 ? Math.max(0, mainSize - fixedMain - gaps) / autoCount : 0;
        int autoRemainder = autoCount > 0 ? Math.max(0, mainSize - fixedMain - gaps) % autoCount : 0;

        int cursor = row ? contentX : contentY;
        for (int index = 0; index < node.children.size(); index++) {
            UiNode child = node.children.get(index);
            int[] size = childSizes.get(index);
            boolean auto = row ? child.style.width < 0 && child.style.widthPercent < 0
                    : child.style.height < 0 && child.style.heightPercent < 0;
            int childMain = auto ? autoSpace : (row ? size[0] : size[1]);
            if (auto && autoRemainder > 0) {
                childMain++;
                autoRemainder--;
            }
            int childCross;
            if (child.style.align == UiStyle.Align.STRETCH) {
                childCross = crossSize;
            } else if (child.style.widthPercent >= 0 && !row) {
                childCross = (int) Math.round(crossSize * child.style.widthPercent);
            } else if (child.style.heightPercent >= 0 && row) {
                childCross = (int) Math.round(crossSize * child.style.heightPercent);
            } else {
                childCross = row ? size[1] : size[0];
            }
            int crossCursor = switch (child.style.align) {
                case START, STRETCH -> 0;
                case CENTER -> (crossSize - childCross) / 2;
                case END -> crossSize - childCross;
            };
            int childX = row ? cursor : contentX + crossCursor;
            int childY = row ? contentY + crossCursor : cursor;
            layout(child, childX, childY, row ? childMain : childCross, row ? childCross : childMain);
            cursor += childMain + style.gap;
        }

        int contentBottom = 0;
        for (UiNode child : node.children) {
            contentBottom = Math.max(contentBottom, child.y + child.height);
        }
        node.contentHeight = Math.max(0, contentBottom - contentY);
        if (node.style.overflowClip) {
            node.scrollY = clamp(node.scrollY, 0, Math.max(0, node.contentHeight - contentHeight));
        } else {
            node.scrollY = 0;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // --- Rendering ---------------------------------------------------------------------------

    private void draw(GuiGraphicsExtractor graphics, UiNode node, int mouseX, int mouseY, float parentAlpha) {
        if (!node.visible()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        float alpha = parentAlpha;
        float translateX = 0.0F;
        float translateY = 0.0F;
        float scale = 1.0F;
        float rotate = 0.0F;
        for (UiAnimation animation : node.animations) {
            translateX += animation.dx;
            translateY += animation.dy;
            scale *= animation.scale;
            rotate += animation.rotate;
            alpha *= animation.alpha;
        }

        boolean transformed = translateX != 0.0F || translateY != 0.0F || scale != 1.0F || rotate != 0.0F;
        int drawX = node.x + (int) translateX;
        int drawY = node.y + (int) translateY;
        if (transformed) {
            graphics.pose().pushMatrix();
            float centerX = drawX + node.width / 2.0F;
            float centerY = drawY + node.height / 2.0F;
            graphics.pose().translate(centerX, centerY);
            if (rotate != 0.0F) {
                graphics.pose().rotate(rotate);
            }
            if (scale != 1.0F) {
                graphics.pose().scale(scale, scale);
            }
            graphics.pose().translate(-centerX, -centerY);
        }

        boolean clipped = false;
        if (node.style.overflowClip) {
            int clipX = node.x;
            int clipY = node.y;
            int clipRight = node.x + node.width;
            int clipBottom = node.y + node.height;
            if (clipRight > clipX && clipBottom > clipY) {
                graphics.enableScissor(clipX, clipY, clipRight, clipBottom);
                clipped = true;
            }
        }

        int color = applyAlpha(node.style.background, alpha);
        if (node.style.background != 0) {
            graphics.fill(node.x, node.y, node.x + node.width, node.y + node.height, color);
        }
        if (node.text != null) {
            String text = node.text.get();
            if (text != null && !text.isEmpty()) {
                int textY = node.y + (node.height - font.lineHeight) / 2;
                if (node.style.textCentered) {
                    graphics.centeredText(font, text, node.x + node.width / 2, textY,
                            applyAlpha(node.style.color, alpha));
                } else {
                    graphics.text(font, text, node.x + node.style.paddingX, textY,
                            applyAlpha(node.style.color, alpha));
                }
            }
        }
        if (node.image != null) {
            graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, node.image, node.x, node.y,
                    node.imageWidth, node.imageHeight);
        }
        if (node.style.borderWidth > 0 && node.style.borderColor != 0) {
            graphics.outline(node.x, node.y, node.width, node.height,
                    applyAlpha(node.style.borderColor, alpha));
        }
        if (node.draw != null) {
            node.draw.accept(node, graphics);
        }

        for (UiNode child : node.children) {
            int savedY = child.y;
            if (node.style.overflowClip) {
                child.y -= node.scrollY;
            }
            draw(graphics, child, mouseX, mouseY, alpha);
            child.y = savedY;
        }

        if (clipped) {
            graphics.disableScissor();
        }
        if (transformed) {
            graphics.pose().popMatrix();
        }
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = (argb >>> 24) & 0xFF;
        int newAlpha = Math.max(0, Math.min(255, (int) (a * alpha)));
        return (newAlpha << 24) | (argb & 0x00FFFFFF);
    }

    // --- Input -------------------------------------------------------------------------------

    private void updateHover(int mouseX, int mouseY) {
        UiNode hovered = hitTest(mouseX, mouseY);
        setHovered(this.root, hovered);
    }

    private void setHovered(UiNode node, UiNode hovered) {
        boolean shouldHover = node == hovered || isAncestor(hovered, node);
        if (shouldHover && !node.hovered) {
            node.hovered = true;
            if (node.onHoverEnter != null) {
                node.onHoverEnter.accept(node);
            }
        } else if (!shouldHover && node.hovered) {
            node.hovered = false;
            if (node.onHoverLeave != null) {
                node.onHoverLeave.accept(node);
            }
        }
        for (UiNode child : node.children) {
            setHovered(child, hovered);
        }
    }

    private static boolean isAncestor(UiNode node, UiNode ancestor) {
        UiNode cursor = node;
        while (cursor != null) {
            if (cursor == ancestor) {
                return true;
            }
            cursor = cursor.parent;
        }
        return false;
    }

    private @Nullable UiNode hitTest(double mouseX, double mouseY) {
        UiNode hit = hitTestNode(this.root, mouseX, mouseY);
        return hit;
    }

    private UiNode hitTestNode(UiNode node, double mouseX, double mouseY) {
        if (!node.visible()) {
            return null;
        }
        if (mouseX < node.x || mouseY < node.y || mouseX >= node.x + node.width || mouseY >= node.y + node.height) {
            return null;
        }
        for (int index = node.children.size() - 1; index >= 0; index--) {
            UiNode child = node.children.get(index);
            int savedY = child.y;
            if (node.style.overflowClip) {
                child.y -= node.scrollY;
            }
            UiNode hit = hitTestNode(child, mouseX, mouseY);
            child.y = savedY;
            if (hit != null) {
                return hit;
            }
        }
        return node;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, int originX, int originY, boolean doubleClick) {
        UiNode target = hitTest(event.x(), event.y());
        this.lastClickShift = event.hasShiftDown();
        this.lastClickButton = event.button();
        if (target == null) {
            return false;
        }
        boolean rightClick = event.button() == 1;
        UiNode cursor = target;
        while (cursor != null) {
            if (rightClick && cursor.onRightClick != null) {
                cursor.onRightClick.accept(cursor);
                return true;
            }
            if (!rightClick && cursor.onClick != null) {
                cursor.onClick.accept(cursor);
                return true;
            }
            cursor = cursor.parent;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, int originX,
            int originY) {
        UiNode target = hitTest(mouseX, mouseY);
        UiNode cursor = target;
        while (cursor != null) {
            if (cursor.style.overflowClip) {
                int max = Math.max(0, cursor.contentHeight - cursor.height);
                cursor.scrollY = clamp(cursor.scrollY - (int) Math.round(scrollY * 16), 0, max);
                if (cursor.onScroll != null) {
                    cursor.onScroll.accept(cursor);
                }
                return true;
            }
            cursor = cursor.parent;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        UiNode focus = findFocused(this.root);
        if (focus == null || focus.onKey == null) {
            return false;
        }
        focus.onKey.accept(event);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        UiNode focus = findFocused(this.root);
        if (focus == null || focus.onChar == null) {
            return false;
        }
        focus.onChar.accept(event);
        return true;
    }

    private static @Nullable UiNode findFocused(UiNode node) {
        if (node.focused) {
            return node;
        }
        for (UiNode child : node.children) {
            UiNode found = findFocused(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Sets keyboard focus to the first focusable node. */
    public void focusFirst() {
        focusFirst(this.root);
    }

    private boolean focusFirst(UiNode node) {
        if (node.style.focusable) {
            node.focused = true;
            return true;
        }
        for (UiNode child : node.children) {
            if (focusFirst(child)) {
                return true;
            }
        }
        return false;
    }
}
