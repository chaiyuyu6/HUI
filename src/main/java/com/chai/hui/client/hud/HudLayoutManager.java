package com.chai.hui.client.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.chai.hui.HuiMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Takes ownership of the HUD layers that HUI can re-arrange, applies the saved layout every frame, and exposes the same
 * data to the HUD editor.
 *
 * <p>Why the vanilla layers are replaced: NeoForge lets mods wrap a HUD layer, but not reorder layers. To support
 * "bring to front" HUI captures the original layers, replaces them with no-op layers and draws the captured layers
 * itself from a single root layer whose order is driven by {@link HudLayout}.
 *
 * <p>The layers next to the editable blocks (armour, food, vehicle health, air, selected item name) are captured as
 * well, because vanilla stacks them using {@code Hud#leftHeight} / {@code Hud#rightHeight}, which the health layer
 * updates while it renders. Rendering them in the same order as vanilla keeps their positions identical.
 *
 * <p>Built-in movable blocks: health, experience, potion effects, hotbar, chat and the scoreboard sidebar. Everything
 * else that was registered when HUI receives the event (other vanilla layers and other mods' HUD layers) is discovered
 * automatically and wrapped so it can be moved and hidden too; see {@link AutoHudBlock}.
 */
public final class HudLayoutManager {
    public static final Identifier HEALTH = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "health");
    public static final Identifier EXPERIENCE = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "experience");
    public static final Identifier EFFECTS = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "effects");
    public static final Identifier HOTBAR = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "hotbar");
    public static final Identifier CHAT = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "chat");
    public static final Identifier SCOREBOARD = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "scoreboard");
    public static final Identifier FOOD = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "food");

    /** Id of the root layer HUI registers above everything else. */
    public static final Identifier ROOT_LAYER = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "hud_layout");

    private static final GuiLayer NOOP_LAYER = (graphics, deltaTracker) -> {};
    /** Draws the health layer far outside the screen, used to let vanilla update its layout counters. */
    private static final float OFFSCREEN = -10000.0F;

    private static final HudLayoutManager INSTANCE = new HudLayoutManager();

    private final Map<Identifier, GuiLayer> capturedLayers = new LinkedHashMap<>();
    private final Map<Identifier, HudBlock> blocks = new LinkedHashMap<>();
    /** Layers rendered in place (with the offset applied through RenderGuiLayerEvent), keyed by layer id. */
    private final Map<Identifier, GuiLayer> inPlaceLayers = new LinkedHashMap<>();
    /** Maps an in-place layer id to the layout block it belongs to (e.g. minecraft:food_level -> hui:food). */
    private final Map<Identifier, Identifier> inPlaceTargets = new LinkedHashMap<>();
    /** Auto-detected layers that should follow a block instead of being shown separately (e.g. AppleSkin overlays). */
    private final Map<Identifier, Identifier> followerTargets = new LinkedHashMap<>();
    /** Layers whose matrix push has not been popped yet, per frame. */
    private final Set<Identifier> pushedLayers = new HashSet<>();

    private HudLayout active = new HudLayout();
    private HudLayout working;

    private HudLayoutManager() {}

    public static HudLayoutManager get() {
        return INSTANCE;
    }

    /** Called from the client entry point on the mod event bus. Captures layers and registers the root layer. */
    public void initialize(RegisterGuiLayersEvent event) {
        if (!this.blocks.isEmpty()) {
            return;
        }

        // --- Vanilla layers HUI knows by name and renders itself (so they can be reordered) -------------------
        capture(event, VanillaGuiLayers.PLAYER_HEALTH);
        capture(event, VanillaGuiLayers.ARMOR_LEVEL);
        captureInPlace(event, VanillaGuiLayers.FOOD_LEVEL, FOOD);
        capture(event, VanillaGuiLayers.VEHICLE_HEALTH);
        capture(event, VanillaGuiLayers.AIR_LEVEL);
        capture(event, VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND);
        capture(event, VanillaGuiLayers.EXPERIENCE_LEVEL);
        capture(event, VanillaGuiLayers.CONTEXTUAL_INFO_BAR);
        capture(event, VanillaGuiLayers.SELECTED_ITEM_NAME);
        capture(event, VanillaGuiLayers.EFFECTS);
        capture(event, VanillaGuiLayers.HOTBAR);
        capture(event, VanillaGuiLayers.CHAT);
        capture(event, VanillaGuiLayers.SCOREBOARD_SIDEBAR);

        this.blocks.put(HEALTH, new HealthHudBlock(HEALTH, "hui.block.health",
                layers(VanillaGuiLayers.PLAYER_HEALTH)));
        this.blocks.put(EXPERIENCE, new SimpleHudBlock(EXPERIENCE, "hui.block.experience",
                layers(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND, VanillaGuiLayers.EXPERIENCE_LEVEL,
                        VanillaGuiLayers.CONTEXTUAL_INFO_BAR),
                HudLayoutManager::experienceBounds));
        this.blocks.put(EFFECTS, new SimpleHudBlock(EFFECTS, "hui.block.effects",
                layers(VanillaGuiLayers.EFFECTS),
                HudLayoutManager::effectsBounds));
        this.blocks.put(HOTBAR, new SimpleHudBlock(HOTBAR, "hui.block.hotbar",
                layers(VanillaGuiLayers.HOTBAR),
                HudLayoutManager::hotbarBounds));
        this.blocks.put(CHAT, new SimpleHudBlock(CHAT, "hui.block.chat",
                layers(VanillaGuiLayers.CHAT),
                HudLayoutManager::chatBounds));
        this.blocks.put(FOOD, new InPlaceHudBlock(FOOD, "hui.block.food", this.inPlaceLayers.get(VanillaGuiLayers.FOOD_LEVEL),
                HudLayoutManager::foodBounds));
        this.blocks.put(SCOREBOARD, new SimpleHudBlock(SCOREBOARD, "hui.block.scoreboard",
                layers(VanillaGuiLayers.SCOREBOARD_SIDEBAR),
                HudLayoutManager::scoreboardBounds));

        // --- Everything else that is registered at this point (vanilla leftovers and other mods' HUD layers) --
        for (Identifier id : HudLayerDiscovery.discover(event)) {
            if (this.capturedLayers.containsKey(id) || this.inPlaceLayers.containsKey(id) || ROOT_LAYER.equals(id)) {
                continue;
            }
            captureAutoBlock(event, id);
        }

        this.active = HudLayoutStore.load(this.blocks.keySet());
        event.registerAboveAll(ROOT_LAYER, this::renderHud);
        HuiMod.LOGGER.info("HUI HUD layout manager initialised with {} blocks ({} auto-detected)",
                this.blocks.size(), this.blocks.size() - BUILTIN_BLOCK_COUNT);
    }

    private static final int BUILTIN_BLOCK_COUNT = 7;

    private void capture(RegisterGuiLayersEvent event, Identifier id) {
        event.wrapLayer(id, original -> {
            this.capturedLayers.put(id, original);
            return NOOP_LAYER;
        });
    }

    /** Captures a layer but keeps rendering it in NeoForge's pipeline; offsets are applied via the render events. */
    private void captureInPlace(RegisterGuiLayersEvent event, Identifier layerId, Identifier blockId) {
        event.wrapLayer(layerId, original -> {
            this.inPlaceLayers.put(layerId, original);
            this.inPlaceTargets.put(layerId, blockId);
            return (graphics, deltaTracker) -> original.render(graphics, deltaTracker);
        });
    }

    /**
     * Pushes the configured translation for in-place layers. Registered on the game bus with the highest priority, so
     * the push happens before the layer and before other mods' own {@code RenderGuiLayerEvent} decorations.
     */
    public void onLayerPre(RenderGuiLayerEvent.Pre event) {
        Identifier layerId = event.getName();
        Identifier id = this.inPlaceTargets.get(layerId);
        if (id == null) {
            return;
        }

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        float offsetX;
        float offsetY;
        if (this.working != null) {
            // The editor draws its own preview; move the real layer off screen so it is not drawn twice.
            offsetX = OFFSCREEN;
            offsetY = OFFSCREEN;
        } else {
            HudLayout layout = this.active;
            if (layout.isHidden(id)) {
                offsetX = OFFSCREEN;
                offsetY = OFFSCREEN;
            } else {
                offsetX = layout.offsetX(id);
                offsetY = layout.offsetY(id);
            }
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX, offsetY);
        this.pushedLayers.add(layerId);
    }

    /**
     * Safety net: if another mod cancels {@code RenderGuiLayerEvent.Pre} after our push, no Post event follows and the
     * matrix push would leak into the remaining HUD. Clean up any leftover push at the end of the GUI frame.
     */
    public void onGuiPost(RenderGuiEvent.Post event) {
        while (!this.pushedLayers.isEmpty()) {
            event.getGuiGraphics().pose().popMatrix();
            this.pushedLayers.clear();
        }
    }
    /**
     * Pops the translation pushed in {@link #onLayerPre}. Registered with the lowest priority, so it runs after other
     * mods' {@code RenderGuiLayerEvent.Post} decorations and those move together with the layer.
     */
    public void onLayerPost(RenderGuiLayerEvent.Post event) {
        if (this.pushedLayers.remove(event.getName())) {
            event.getGuiGraphics().pose().popMatrix();
        }
    }

    /**
     * Wraps an auto-detected layer in a pass-through layer that applies the configured offset and visibility while
     * keeping the layer in NeoForge's own rendering order.
     */
    private void captureAutoBlock(RegisterGuiLayersEvent event, Identifier id) {
        AutoHudBlock[] holder = new AutoHudBlock[1];
        event.wrapLayer(id, original -> {
            AutoHudBlock block = new AutoHudBlock(id, original);
            if ("appleskin".equals(id.getNamespace())) {
                // AppleSkin draws hunger/saturation overlays on top of the hunger bar; keep them attached to it.
                this.followerTargets.put(id, FOOD);
            }
            holder[0] = block;
            return (graphics, deltaTracker) -> renderAutoLayer(id, original, graphics, deltaTracker);
        });
        if (holder[0] != null) {
            this.blocks.put(id, holder[0]);
        }
    }

    private void renderAutoLayer(Identifier id, GuiLayer layer, GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker) {
        if (this.working != null) {
            // The editor renders its own preview of every block.
            return;
        }
        Identifier target = this.followerTargets.getOrDefault(id, id);
        HudLayout layout = this.active;
        if (layout.isHidden(target)) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(layout.offsetX(target), layout.offsetY(target));
        try {
            layer.render(graphics, deltaTracker);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private List<GuiLayer> layers(Identifier... ids) {
        List<GuiLayer> layers = new ArrayList<>(ids.length);
        for (Identifier id : ids) {
            GuiLayer layer = this.capturedLayers.get(id);
            if (layer != null) {
                layers.add(layer);
            } else {
                HuiMod.LOGGER.warn("HUD layer {} was not captured, block will be incomplete", id);
            }
        }
        return layers;
    }

    // --- Rendering -------------------------------------------------------------------------

    private void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (this.working != null) {
            // The editor renders its own preview of the blocks, so the in-game HUD stays clean.
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
            return;
        }

        HudLayout layout = this.active;
        boolean survivalHud = minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer();

        if (survivalHud) {
            // Vanilla draws the health layer first and adds 10 to leftHeight while doing so. Run it off screen once
            // so the armour/food/air layers below keep their vanilla positions, then paint it at its real position
            // with the other editable blocks.
            renderLayer(graphics, deltaTracker, VanillaGuiLayers.PLAYER_HEALTH, OFFSCREEN, OFFSCREEN);
            renderLayer(graphics, deltaTracker, VanillaGuiLayers.ARMOR_LEVEL, 0, 0);
        }
        renderLayer(graphics, deltaTracker, VanillaGuiLayers.VEHICLE_HEALTH, 0, 0);
        if (survivalHud) {
            renderLayer(graphics, deltaTracker, VanillaGuiLayers.AIR_LEVEL, 0, 0);
        }
        renderLayer(graphics, deltaTracker, VanillaGuiLayers.SELECTED_ITEM_NAME, 0, 0);

        for (Identifier id : layout.ordered(this.blocks.keySet())) {
            if (layout.isHidden(id)) {
                continue;
            }
            HudBlock block = this.blocks.get(id);
            // Auto-detected and in-place layers render themselves from NeoForge's pipeline; only captured blocks are
            // painted here.
            if (block != null && !(block instanceof AutoHudBlock) && !(block instanceof InPlaceHudBlock)) {
                block.render(graphics, deltaTracker, layout.offsetX(id), layout.offsetY(id));
            }
        }
    }

    private void renderLayer(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Identifier id, float dx, float dy) {
        GuiLayer layer = this.capturedLayers.get(id);
        if (layer == null) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(dx, dy);
        try {
            layer.render(graphics, deltaTracker);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /** Renders a single block with its configured offsets; used by the editor preview. */
    public void renderBlock(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, HudBlock block, HudLayout layout) {
        block.render(graphics, deltaTracker, layout.offsetX(block.id()), layout.offsetY(block.id()));
    }

    // --- Layout state ----------------------------------------------------------------------

    public void beginEditing() {
        if (this.working == null) {
            this.working = this.active.copy();
        }
    }

    /** Persists the working layout and makes it the active one. */
    public void commit() {
        if (this.working == null) {
            return;
        }
        this.active = this.working;
        this.working = null;
        HudLayoutStore.save(this.active);
    }

    /** Throws the working layout away. */
    public void cancel() {
        this.working = null;
    }
    /** Resets every block to its default offset, visibility and order (working copy only while editing). */
    public void resetAll(HudLayout layout) {
        layout.resetAll(this.blocks.keySet());
    }

    /** Resets one block to its default offset and visibility (working copy only while editing). */
    public void resetBlock(HudLayout layout, Identifier id) {
        layout.reset(id);
    }

    public boolean isEditing() {
        return this.working != null;
    }

    /** @return the layout that should currently be used (working copy while editing, else the saved one). */
    public HudLayout layout() {
        return this.working != null ? this.working : this.active;
    }

    // --- Block queries used by the editor ---------------------------------------------------

    public List<HudBlock> blocksInDrawOrder(HudLayout layout, int screenWidth, int screenHeight) {
        List<HudBlock> ordered = new ArrayList<>(this.blocks.size());
        for (Identifier id : layout.ordered(this.blocks.keySet())) {
            HudBlock block = this.blocks.get(id);
            if (block == null) {
                continue;
            }
            // Follower layers are moved together with their target block, so they are not listed separately.
            if (this.followerTargets.containsKey(id)) {
                continue;
            }
            // Auto-detected layers that currently draw nothing, or that are full screen overlays, stay out of the way.
            if (block instanceof AutoHudBlock auto && !auto.isEditable(screenWidth, screenHeight)) {
                continue;
            }
            ordered.add(block);
        }
        return ordered;
    }

    public HudRect boundsOf(HudBlock block, HudLayout layout, int screenWidth, int screenHeight) {
        return block.bounds(screenWidth, screenHeight).offset(layout.offsetX(block.id()), layout.offsetY(block.id()));
    }

    /** @return the topmost block under the given point, or {@code null} if the point is empty. */
    public HudBlock blockAt(double mouseX, double mouseY, HudLayout layout, int screenWidth, int screenHeight) {
        List<HudBlock> ordered = blocksInDrawOrder(layout, screenWidth, screenHeight);
        for (int index = ordered.size() - 1; index >= 0; index--) {
            HudBlock block = ordered.get(index);
            if (boundsOf(block, layout, screenWidth, screenHeight).contains(mouseX, mouseY)) {
                return block;
            }
        }
        return null;
    }

    // --- Default bounds --------------------------------------------------------------------

    /** Experience bar: {@code ContextualBar} draws at {@code ((width - 182) / 2, height - 29)}, the level text above it. */
    private static HudRect experienceBounds(int screenWidth, int screenHeight) {
        return new HudRect((screenWidth - 182) / 2, screenHeight - 36, 182, 13);
    }

    /** Potion effects: 24x24 icons in 25px columns starting at the top right corner. */
    private static HudRect effectsBounds(int screenWidth, int screenHeight) {
        int beneficial = 0;
        int harmful = 0;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            for (MobEffectInstance effect : player.getActiveEffects()) {
                if (!effect.showIcon()) {
                    continue;
                }
                if (effect.getEffect().value().isBeneficial()) {
                    beneficial++;
                } else {
                    harmful++;
                }
            }
        }

        int columns = Math.max(beneficial, harmful);
        int rows = (beneficial > 0 ? 1 : 0) + (harmful > 0 ? 1 : 0);
        if (columns == 0) {
            // Placeholder so the block stays selectable in the editor when no effect is active.
            return new HudRect(screenWidth - 25, 1, 24, 24);
        }
        return new HudRect(screenWidth - 25 * columns, 1, 25 * columns, 26 * rows - 2);
    }

    /** Hotbar: vanilla draws {@code 182x22} at {@code (width / 2 - 91, height - 22)}; the offhand slot is not included. */
    private static HudRect hotbarBounds(int screenWidth, int screenHeight) {
        return new HudRect(screenWidth / 2 - 91, screenHeight - 22, 182, 22);
    }

    /** Hunger: vanilla draws ten 9px food icons from {@code width / 2 + 10} at {@code height - 39}. */
    private static HudRect foodBounds(int screenWidth, int screenHeight) {
        return new HudRect(screenWidth / 2 + 10, screenHeight - 39, 81, 9);
    }

    /**
     * Chat: {@link ChatComponent} draws its messages in the bottom left corner with a 40px bottom margin, inset by 4px
     * and scaled by the chat scale. The bounds cover the whole configured (unfocused) chat area so an empty chat can be
     * dragged as well.
     */
    private static HudRect chatBounds(int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        double scale = minecraft.options.chatScale().get();
        int width = (int) Math.ceil(ChatComponent.getWidth(minecraft.options.chatWidth().get()) + 12.0 * scale);
        int height = (int) Math.ceil(ChatComponent.getHeight(minecraft.options.chatHeightUnfocused().get()) * scale);
        return new HudRect(0, screenHeight - 40 - height, width, height);
    }

    /**
     * Scoreboard sidebar: mirrors the box vanilla draws in {@code Hud#displayScoreboardSidebar}, i.e. a 9px line per
     * entry (at most 15), a 9px header line plus 1px padding, anchored to the right edge around the vertical centre.
     */
    private static HudRect scoreboardBounds(int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        Objective objective = sidebarObjective(minecraft);
        if (objective == null) {
            // Placeholder so the block stays selectable in the editor when no sidebar is displayed.
            return new HudRect(screenWidth - 63, screenHeight / 2 - 10, 60, 20);
        }

        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        int biggestWidth = minecraft.font.width(objective.getDisplayName());
        int entryCount = 0;
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
            if (entry.isHidden() || entryCount >= 15) {
                continue;
            }
            entryCount++;
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            Component name = PlayerTeam.formatNameForTeam(team, entry.ownerName());
            int scoreWidth = minecraft.font.width(entry.formatValue(numberFormat));
            int entryWidth = minecraft.font.width(name)
                    + (scoreWidth > 0 ? minecraft.font.width(": ") + scoreWidth : 0);
            biggestWidth = Math.max(biggestWidth, entryWidth);
        }

        int entriesHeight = entryCount * 9;
        int bottom = screenHeight / 2 + entriesHeight / 3;
        int top = bottom - entriesHeight - 10;
        return new HudRect(screenWidth - biggestWidth - 5, top, biggestWidth + 4, bottom - top);
    }

    /** @return the objective currently shown in the sidebar, or {@code null}. */
    private static Objective sidebarObjective(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        return minecraft.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
    }
}
