package com.chai.hui.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * HUI native recipe browser (plan B): reads recipes for one focus ingredient through the JEI API and draws them as HUI
 * cards with a category sidebar, bookmark and recipe transfer buttons. No JEI screen is involved.
 */
public final class RecipeBrowserContent implements com.chai.hui.client.windowing.WindowContent {
    private static final int PADDING = 6;
    private static final int SIDEBAR_WIDTH = 86;
    private static final int TAB_HEIGHT = 18;
    private static final int CARD_GAP = 4;
    private static final int MAX_PER_CATEGORY = 10;

    private record Card(Object recipe, IRecipeLayoutDrawable<?> drawable, int width, int height) {}

    private record Tab(Component title, IDrawable icon, IRecipeCategory<?> category, List<Card> cards) {}

    private record Action(int kind, int tab, int card, int x, int y, int width, int height) {}

    private static final int ACTION_BOOKMARK = 0;
    private static final int ACTION_TRANSFER = 1;

    private final IJeiRuntime runtime;
    private final IRecipeManager recipeManager;
    private final ItemStack focusStack;
    private final List<Tab> tabs = new ArrayList<>();
    private ITypedIngredient<ItemStack> typedFocus;
    private boolean built;
    private int selectedTab;
    private int tabScroll;
    private int cardScroll;
    private final List<Action> actions = new ArrayList<>();
    private final List<int[]> tabButtons = new ArrayList<>();

    public RecipeBrowserContent(IJeiRuntime runtime, ItemStack focusStack) {
        this.runtime = runtime;
        this.recipeManager = runtime.getRecipeManager();
        this.focusStack = focusStack.copy();
    }

    @Override
    public int preferredWidth() {
        return 320;
    }

    @Override
    public int preferredHeight() {
        return 240;
    }

    @Override
    public void init(int screenWidth, int screenHeight) {
        build();
    }

    private void build() {
        if (this.built) {
            return;
        }
        this.built = true;
        var helpers = this.runtime.getJeiHelpers();
        var focusFactory = helpers.getFocusFactory();
        this.runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, this.focusStack)
                .ifPresent(typed -> this.typedFocus = typed);
        if (this.typedFocus == null) {
            return;
        }
        List<IFocus<?>> focuses = List.of(
                focusFactory.createFocus(RecipeIngredientRole.OUTPUT, this.typedFocus),
                focusFactory.createFocus(RecipeIngredientRole.INPUT, this.typedFocus));
        IFocusGroup focusGroup = focusFactory.createFocusGroup(focuses);
        try (Stream<IRecipeCategory<?>> categories = this.recipeManager.createRecipeCategoryLookup()
                .limitFocus(focuses).get()) {
            categories.forEach(category -> addCategory(category, focuses, focusGroup));
        } catch (RuntimeException ignored) {
        }
    }

    private <T> void addCategory(IRecipeCategory<T> category, List<IFocus<?>> focuses, IFocusGroup focusGroup) {
        List<Card> cards = new ArrayList<>();
        IRecipeType<T> recipeType = category.getRecipeType();
        try (Stream<T> recipes = this.recipeManager.createRecipeLookup(recipeType).limitFocus(focuses).get()) {
            recipes.limit(MAX_PER_CATEGORY).forEach(recipe -> this.recipeManager
                    .createRecipeLayoutDrawable(category, recipe, focusGroup)
                    .ifPresent(drawable -> cards.add(
                            new Card(recipe, drawable, category.getWidth(), category.getHeight()))));
        } catch (RuntimeException ignored) {
        }
        if (!cards.isEmpty()) {
            this.tabs.add(new Tab(category.getTitle(), category.getIcon(), category, cards));
        }
    }

    private Tab selected() {
        if (this.tabs.isEmpty()) {
            return null;
        }
        return this.tabs.get(Math.max(0, Math.min(this.tabs.size() - 1, this.selectedTab)));
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        var font = Minecraft.getInstance().font;
        this.actions.clear();
        this.tabButtons.clear();
        graphics.enableScissor(originX, originY, originX + preferredWidth(), originY + preferredHeight());
        graphics.fill(originX + 1, originY + 1, originX + SIDEBAR_WIDTH, originY + preferredHeight() - 1,
                0xB0101418);
        graphics.text(font, Component.translatable("hui.recipe_browser.focused", this.focusStack.getHoverName()),
                originX + SIDEBAR_WIDTH + PADDING, originY + PADDING, 0xFFE0E0E0, false);
        if (this.tabs.isEmpty()) {
            graphics.text(font, Component.translatable("hui.recipe_browser.empty"),
                    originX + SIDEBAR_WIDTH + PADDING, originY + 24, 0xFF909090, false);
            graphics.disableScissor();
            return;
        }
        renderSidebar(graphics, mouseX, mouseY, originX, originY);
        renderCards(graphics, mouseX, mouseY, partialTick, originX, originY);
        graphics.disableScissor();
    }

    private void renderSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int originX, int originY) {
        var font = Minecraft.getInstance().font;
        int y = originY + 2 - this.tabScroll;
        for (int index = 0; index < this.tabs.size(); index++) {
            Tab tab = this.tabs.get(index);
            boolean selected = index == this.selectedTab;
            if (y + TAB_HEIGHT < originY + 2 || y > originY + preferredHeight() - 4) {
                y += TAB_HEIGHT + 2;
                continue;
            }
            graphics.fill(originX + 2, y, originX + SIDEBAR_WIDTH - 3, y + TAB_HEIGHT,
                    selected ? 0xFF2A4A66 : 0xFF182430);
            if (tab.icon() != null) {
                tab.icon().draw(graphics, originX + 3, y + 1);
            }
            String label = font.plainSubstrByWidth(tab.title().getString(), SIDEBAR_WIDTH - 26);
            graphics.text(font, Component.literal(label), originX + 22, y + 5, selected ? 0xFFFFFFFF : 0xFFB0C0D0,
                    false);
            this.tabButtons.add(new int[] {index, originX + 2, y, SIDEBAR_WIDTH - 5, TAB_HEIGHT});
            y += TAB_HEIGHT + 2;
        }
    }

    private void renderCards(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int originX,
            int originY) {
        var font = Minecraft.getInstance().font;
        Tab tab = selected();
        int listX = originX + SIDEBAR_WIDTH + PADDING;
        int listW = preferredWidth() - SIDEBAR_WIDTH - PADDING * 2;
        int y = originY + 18 - this.cardScroll;
        int tabIndex = this.tabs.indexOf(tab);
        for (int cardIndex = 0; cardIndex < tab.cards().size(); cardIndex++) {
            Card card = tab.cards().get(cardIndex);
            int rowHeight = Math.max(card.height(), 24) + CARD_GAP;
            if (y + rowHeight < originY + 18 || y > originY + preferredHeight() - 4) {
                y += rowHeight;
                continue;
            }
            int drawX = listX + 2;
            int drawY = y + 2;
            card.drawable().setPosition(drawX, drawY);
            int localX = mouseX - originX;
            int localY = mouseY - originY;
            card.drawable().drawRecipe(graphics, localX, localY);
            card.drawable().drawOverlays(graphics, localX, localY);
            if (this.typedFocus != null) {
                int bookmarkX = listX + listW - 38;
                int transferX = listX + listW - 18;
                boolean bookmarked = this.runtime.getBookmarkManager().contains(this.typedFocus);
                graphics.fill(bookmarkX, y, bookmarkX + 16, y + 16, bookmarked ? 0xFF6B5A1E : 0xFF22303C);
                graphics.text(font, Component.literal("★"), bookmarkX + 3, y + 4,
                        bookmarked ? 0xFFFFD54A : 0xFF8090A0, false);
                boolean canTransfer = transferAvailable(tab);
                graphics.fill(transferX, y, transferX + 16, y + 16, canTransfer ? 0xFF1E4A6B : 0xFF22303C);
                graphics.text(font, Component.literal("→"), transferX + 3, y + 4,
                        canTransfer ? 0xFF7FD4FF : 0xFF607080, false);
                this.actions.add(new Action(ACTION_BOOKMARK, tabIndex, cardIndex, bookmarkX, y, 17, 17));
                this.actions.add(new Action(ACTION_TRANSFER, tabIndex, cardIndex, transferX, y, 17, 17));
            }
            y += rowHeight;
        }
    }

    private boolean transferAvailable(Tab tab) {
        if (!(Minecraft.getInstance().gui.screen() instanceof com.chai.hui.client.windowing.HuiDesktopScreen desktop)) {
            return false;
        }
        AbstractContainerMenu menu = desktop.activeServerMenu();
        if (menu == null) {
            return false;
        }
        try {
            return this.runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, tab.category()).isPresent();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void transfer(Tab tab, Card card) {
        if (!(Minecraft.getInstance().gui.screen() instanceof com.chai.hui.client.windowing.HuiDesktopScreen desktop)
                || Minecraft.getInstance().player == null) {
            return;
        }
        AbstractContainerMenu menu = desktop.activeServerMenu();
        if (menu == null) {
            return;
        }
        desktop.activateActiveContent();
        Optional<IRecipeTransferHandler<AbstractContainerMenu, Object>> handler = this.runtime
                .getRecipeTransferManager()
                .getRecipeTransferHandler(menu, (IRecipeCategory) tab.category());
        if (handler.isEmpty()) {
            return;
        }
        IRecipeSlotsView slots = card.drawable().getRecipeSlotsView();
        handler.get().transferRecipe(menu, card.recipe(), slots, Minecraft.getInstance().player, false, true);
    }

    private void toggleBookmark() {
        if (this.typedFocus == null) {
            return;
        }
        var bookmarks = this.runtime.getBookmarkManager();
        if (bookmarks.contains(this.typedFocus)) {
            bookmarks.remove(this.typedFocus);
        } else {
            bookmarks.add(this.typedFocus);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, int originX, int originY, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        for (int[] button : this.tabButtons) {
            if (mouseX >= button[1] && mouseX < button[1] + button[3] && mouseY >= button[2]
                    && mouseY < button[2] + button[4]) {
                this.selectedTab = button[0];
                this.cardScroll = 0;
                return true;
            }
        }
        Tab tab = selected();
        for (Action action : this.actions) {
            if (tab == null || action.tab() != this.tabs.indexOf(tab)
                    || mouseX < action.x() || mouseX >= action.x() + action.width()
                    || mouseY < action.y() || mouseY >= action.y() + action.height()) {
                continue;
            }
            Card card = tab.cards().get(action.card());
            if (action.kind() == ACTION_BOOKMARK) {
                toggleBookmark();
            } else {
                transfer(tab, card);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, int originX,
            int originY) {
        if (mouseX < originX + SIDEBAR_WIDTH) {
            int max = Math.max(0, this.tabs.size() * (TAB_HEIGHT + 2) - preferredHeight());
            this.tabScroll = Math.max(0, Math.min(max, this.tabScroll - (int) (scrollY * 18)));
        } else {
            Tab tab = selected();
            int content = tab == null ? 0
                    : tab.cards().stream().mapToInt(card -> Math.max(card.height(), 24) + CARD_GAP).sum();
            int max = Math.max(0, content - (preferredHeight() - 18));
            this.cardScroll = Math.max(0, Math.min(max, this.cardScroll - (int) (scrollY * 18)));
        }
        return true;
    }

    @Override
    public void tick() {
        for (Tab tab : this.tabs) {
            for (Card card : tab.cards()) {
                card.drawable().tick();
            }
        }
    }
}
