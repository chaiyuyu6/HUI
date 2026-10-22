package com.chai.hui.client.ui;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.chai.hui.client.ui.framework.UiNode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

/**
 * The registration channel other mods use to plug GUIs into HUI.
 *
 * <ul>
 *   <li>{@link #registerMenu(MenuType, MenuUiFactory)}: build a HUI (web-like) UI for a menu. The menu data still syncs
 *       through the vanilla packets.</li>
 *   <li>{@link #registerBlockEntity(BlockEntityType, BlockUiFactory)}: give a block entity a HUI UI. If the block entity
 *       also opens a vanilla menu the UI receives that menu; if it has no vanilla GUI HUI forces its own window and
 *       syncs through the HUI block UI payloads.</li>
 * </ul>
 *
 * <p>Fallback rules: covered vanilla menus are rendered with HUI's own UI; unregistered modded menus and unregistered
 * block entities fall back to their vanilla screens.
 */
public final class HuiUiRegistry {
    @FunctionalInterface
    public interface MenuUiFactory {
        UiNode build(MenuUiContext context);
    }

    @FunctionalInterface
    public interface BlockUiFactory {
        UiNode build(BlockUiContext context);
    }

    private static final Map<Identifier, MenuUiFactory> MENU_UI = new ConcurrentHashMap<>();
    private static final Map<Identifier, BlockUiFactory> BLOCK_UI = new ConcurrentHashMap<>();
    private static final Set<Identifier> COVERED_MENUS = ConcurrentHashMap.newKeySet();
    private static volatile boolean coverageInitialized;

    private HuiUiRegistry() {
    }

    public static void registerMenu(MenuType<?> type, MenuUiFactory factory) {
        Identifier id = BuiltInRegistries.MENU.getKey(type);
        if (id != null) {
            MENU_UI.put(id, factory);
            COVERED_MENUS.add(id);
        }
    }

    public static void registerBlockEntity(BlockEntityType<?> type, BlockUiFactory factory) {
        Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        if (id != null) {
            BLOCK_UI.put(id, factory);
        }
    }

    public static @Nullable MenuUiFactory menuUi(MenuType<?> type) {
        Identifier id = BuiltInRegistries.MENU.getKey(type);
        return id == null ? null : MENU_UI.get(id);
    }

    public static @Nullable BlockUiFactory blockUi(Identifier typeId) {
        return BLOCK_UI.get(typeId);
    }

    public static @Nullable BlockUiFactory blockUi(BlockEntityType<?> type) {
        Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        return id == null ? null : BLOCK_UI.get(id);
    }

    public static boolean isBlockRegistered(BlockEntityType<?> type) {
        return blockUi(type) != null;
    }

    /**
     * Marks every vanilla menu as covered by HUI: those screens are replaced by HUI windows automatically. Modded menus
     * stay vanilla unless they are registered explicitly.
     */
    public static void coverVanillaMenus() {
        for (Identifier id : BuiltInRegistries.MENU.keySet()) {
            if ("minecraft".equals(id.getNamespace())) {
                COVERED_MENUS.add(id);
            }
        }
        coverageInitialized = true;
    }

    public static boolean isMenuCovered(MenuType<?> type) {
        if (!coverageInitialized) {
            coverVanillaMenus();
        }
        Identifier id = BuiltInRegistries.MENU.getKey(type);
        return id != null && COVERED_MENUS.contains(id);
    }
}
