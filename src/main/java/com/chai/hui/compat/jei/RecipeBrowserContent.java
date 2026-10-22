package com.chai.hui.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * HUI native recipe browser (plan B): reads recipes for one focus ingredient through the JEI API and draws them as HUI
 * styled cards in a window. No JEI screen is involved, so all interaction stays inside the HUI window system.
 */
public final class RecipeBrowserContent implements com.chai.hui.client.windowing.WindowContent {
    private static final int PADDING = 6;
    private static final int ROW_GAP = 6;
    private static final int MAX_CARDS = 40;

    private record Card(Component category, IRecipeLayoutDrawable<?> drawable, int width, int height) {}

    private final IRecipeManager recipeManager;
    private final ItemStack focusStack;
    private final List<Card> cards = new ArrayList<>();
    private boolean built;
    private int scroll;

    public RecipeBrowserContent(IJeiRuntime runtime, ItemStack focusStack) {
        this.recipeManager = runtime.getRecipeManager();
        this.focusStack = focusStack.copy();
        this.runtime = runtime;
    }

    private final IJeiRuntime runtime;

    @Override
    public int preferredWidth() {
        return 300;
    }

    @Override
    public int preferredHeight() {
        return 240;
    }

    @Override
    public void init(int screenWidth, int screenHeight) {
        buildCards();
    }

    private void buildCards() {
        if (this.built) {
            return;
        }
        this.built = true;
        var helpers = this.runtime.getJeiHelpers();
        var focusFactory = helpers.getFocusFactory();
        var typed = this.runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, this.focusStack);
        if (typed.isEmpty()) {
            return;
        }
        List<IFocus<?>> focuses = List.of(
                focusFactory.createFocus(RecipeIngredientRole.OUTPUT, typed.get()),
                focusFactory.createFocus(RecipeIngredientRole.INPUT, typed.get()));
        IFocusGroup focusGroup = focusFactory.createFocusGroup(focuses);
        try (Stream<IRecipeCategory<?>> categories = this.recipeManager.createRecipeCategoryLookup()
                .limitFocus(focuses).get()) {
            categories.forEach(category -> addCategory(category, focuses, focusGroup));
        } catch (RuntimeException ignored) {
        }
        this.cards.sort((left, right) -> left.category().getString().compareToIgnoreCase(right.category().getString()));
    }

    private <T> void addCategory(IRecipeCategory<T> category, List<IFocus<?>> focuses, IFocusGroup focusGroup) {
        if (this.cards.size() >= MAX_CARDS) {
            return;
        }
        IRecipeType<T> recipeType = category.getRecipeType();
        try (Stream<T> recipes = this.recipeManager.createRecipeLookup(recipeType).limitFocus(focuses).get()) {
            recipes.limit(8).forEach(recipe -> {
                if (this.cards.size() >= MAX_CARDS) {
                    return;
                }
                this.recipeManager.createRecipeLayoutDrawable(category, recipe, focusGroup).ifPresent(drawable -> {
                    this.cards.add(new Card(category.getTitle(), drawable, category.getWidth(),
                            category.getHeight()));
                });
            });
        } catch (RuntimeException ignored) {
        }
    }

    private int cardRowHeight(Card card) {
        return 12 + card.height() + ROW_GAP;
    }

    private int totalHeight() {
        int total = PADDING;
        for (Card card : this.cards) {
            total += cardRowHeight(card);
        }
        return total;
    }

    @Override
    public boolean paintsBodyBackground() {
        return true;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        int localX = mouseX - originX;
        int localY = mouseY - originY;
        graphics.enableScissor(originX, originY, originX + preferredWidth(), originY + preferredHeight());
        graphics.text(net.minecraft.client.Minecraft.getInstance().font,
                Component.translatable("hui.recipe_browser.focused", this.focusStack.getHoverName()),
                originX + PADDING, originY + PADDING, 0xFFE0E0E0, false);
        if (this.cards.isEmpty()) {
            graphics.text(net.minecraft.client.Minecraft.getInstance().font,
                    Component.translatable("hui.recipe_browser.empty"), originX + PADDING, originY + 24,
                    0xFF909090, false);
            graphics.disableScissor();
            return;
        }
        int y = originY + 22 - this.scroll;
        for (Card card : this.cards) {
            int rowHeight = cardRowHeight(card);
            if (y + rowHeight >= originY && y <= originY + preferredHeight()) {
                graphics.text(net.minecraft.client.Minecraft.getInstance().font, card.category(), originX + PADDING,
                        y, 0xFF9CD2FF, false);
                card.drawable().setPosition(originX + PADDING, y + 11);
                card.drawable().drawRecipe(graphics, localX, localY);
                card.drawable().drawOverlays(graphics, localX, localY);
            }
            y += rowHeight;
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, int originX,
            int originY) {
        int maxScroll = Math.max(0, totalHeight() - preferredHeight());
        this.scroll = Math.max(0, Math.min(maxScroll, this.scroll - (int) (scrollY * 18)));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, int originX, int originY, boolean doubleClick) {
        return false;
    }

    @Override
    public void tick() {
        for (Card card : this.cards) {
            card.drawable().tick();
        }
    }
}
