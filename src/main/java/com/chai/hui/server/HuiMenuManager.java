package com.chai.hui.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chai.hui.network.CloseMenuClientPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks the container menus that belong to HUI windows.
 *
 * <p>Vanilla only keeps one {@code containerMenu} per player. HUI keeps the other menus registered here and hot-swaps
 * {@code player.containerMenu} when a window is used (see {@link com.chai.hui.network.ActivateMenuPayload}), which lets
 * several container windows stay open while clicks still go through the vanilla click handling.
 */
public final class HuiMenuManager {
    private static final Map<UUID, Map<Integer, AbstractContainerMenu>> MENUS = new LinkedHashMap<>();

    private HuiMenuManager() {}

    public static void register(ServerPlayer player, int containerId, AbstractContainerMenu menu) {
        MENUS.computeIfAbsent(player.getUUID(), uuid -> new LinkedHashMap<>()).put(containerId, menu);
    }

    public static @Nullable AbstractContainerMenu get(ServerPlayer player, int containerId) {
        Map<Integer, AbstractContainerMenu> menus = MENUS.get(player.getUUID());
        return menus == null ? null : menus.get(containerId);
    }

    /** Closes one tracked menu (client already closed its window). */
    public static void closeMenu(ServerPlayer player, int containerId) {
        Map<Integer, AbstractContainerMenu> menus = MENUS.get(player.getUUID());
        if (menus == null) {
            return;
        }
        AbstractContainerMenu menu = menus.remove(containerId);
        if (menu == null) {
            return;
        }
        if (player.containerMenu == menu) {
            player.containerMenu = player.inventoryMenu;
        }
        menu.removed(player);
    }

    /** Called by the server tick: closes tracked menus whose block entity/player is no longer valid. */
    public static void validateAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Map<Integer, AbstractContainerMenu> menus = MENUS.get(player.getUUID());
            if (menus == null || menus.isEmpty()) {
                continue;
            }
            List<Integer> invalid = new ArrayList<>();
            for (Map.Entry<Integer, AbstractContainerMenu> entry : menus.entrySet()) {
                if (!entry.getValue().stillValid(player)) {
                    invalid.add(entry.getKey());
                }
            }
            for (int containerId : invalid) {
                closeMenu(player, containerId);
                PacketDistributor.sendToPlayer(player, new CloseMenuClientPayload(containerId));
            }
        }
    }

    /** Drops all tracked menus for a player (logout / disconnect). */
    public static void clear(ServerPlayer player) {
        Map<Integer, AbstractContainerMenu> menus = MENUS.remove(player.getUUID());
        if (menus == null) {
            return;
        }
        for (AbstractContainerMenu menu : menus.values()) {
            if (player.containerMenu == menu) {
                player.containerMenu = player.inventoryMenu;
            }
            menu.removed(player);
        }
    }
}
