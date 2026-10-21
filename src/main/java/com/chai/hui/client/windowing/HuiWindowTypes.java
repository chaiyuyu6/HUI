package com.chai.hui.client.windowing;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.chai.hui.HuiMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Registry of the built-in window types.
 *
 * <p>Every type has a factory so a window can be recreated from the persisted layout. The public registration API for
 * other mods is planned for P4; for now this registry is client internal.
 */
public final class HuiWindowTypes {
    /** Survival player inventory (full vanilla inventory screen). */
    public static final Identifier INVENTORY = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "inventory");
    /** Creative inventory (vanilla creative screen with its tab rows). */
    public static final Identifier CREATIVE = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "creative_inventory");
    /** The cropped default inventory: only the 27 storage slots and the hotbar row (the HUI inventory window). */
    public static final Identifier DEFAULT_INVENTORY =
            Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "default_inventory");
    /** Block entity containers opened through the "open" list; ephemeral and not persisted for now. */
    public static final Identifier BLOCK_ENTITY = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "block_entity");
    public static final Identifier DEMO = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "demo");
    public static final Identifier PROPERTIES = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "properties");

    /** All types that count as "the inventory window" of a page; only one of them may be open per page. */
    private static final Set<Identifier> INVENTORY_TYPES = Set.of(INVENTORY, CREATIVE, DEFAULT_INVENTORY);

    private static final Map<Identifier, WindowType> TYPES = new LinkedHashMap<>();

    static {
        register(WindowType.translatable(INVENTORY, "hui.window.inventory", () -> {
            Player player = Minecraft.getInstance().player;
            if (player == null) {
                return unavailable();
            }
            if (player.hasInfiniteMaterials()) {
                return notAvailableHere();
            }
            return EmbeddedScreenContent.survival(player);
        }));
        register(WindowType.translatable(CREATIVE, "hui.window.creative_inventory", () -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return unavailable();
            }
            if (!player.hasInfiniteMaterials()) {
                return notAvailableHere();
            }
            return EmbeddedScreenContent.creative(player);
        }));
        register(WindowType.translatable(DEFAULT_INVENTORY, "hui.window.default_inventory", () -> {
            Player player = Minecraft.getInstance().player;
            if (player == null) {
                return unavailable();
            }
            if (player.hasInfiniteMaterials()) {
                return notAvailableHere();
            }
            return EmbeddedScreenContent.croppedInventory(player);
        }));
        register(WindowType.translatable(DEMO, "hui.window.demo", () -> {
            com.chai.hui.client.windowing.ui.PanelWidget root = com.chai.hui.client.windowing.ui.Ui.column()
                    .padding(6).gap(4)
                    .child(com.chai.hui.client.windowing.ui.Ui.label(() -> Component.translatable("hui.demo.line1")))
                    .child(com.chai.hui.client.windowing.ui.Ui.mutedLabel(
                            () -> Component.translatable("hui.demo.line2")))
                    .child(com.chai.hui.client.windowing.ui.Ui.label(
                            () -> Component.translatable("hui.demo.input_label")))
                    .child(com.chai.hui.client.windowing.ui.Ui.textInput("", 24, value -> {}, () -> {}));
            return new com.chai.hui.client.windowing.ui.UiPanelContent(root);
        }));
        register(WindowType.translatable(PROPERTIES, "hui.window.properties",
                () -> new TextWindowContent(List.of(Component.translatable("hui.window.properties_lost")), 200, 24)));
    }

    private HuiWindowTypes() {}

    public static void register(WindowType type) {
        TYPES.put(type.id(), type);
    }

    public static @Nullable WindowType get(Identifier id) {
        return TYPES.get(id);
    }

    public static Collection<WindowType> all() {
        return List.copyOf(TYPES.values());
    }

    /** @return true when the type is one of the inventory window kinds (survival, creative, default). */
    public static boolean isInventoryType(Identifier id) {
        return INVENTORY_TYPES.contains(id);
    }

    /** The inventory window type that matches the current game mode. */
    public static Identifier inventoryTypeForCurrentGameMode() {
        Player player = Minecraft.getInstance().player;
        return player != null && player.hasInfiniteMaterials() ? CREATIVE : INVENTORY;
    }

    private static WindowContent unavailable() {
        return new TextWindowContent(List.of(Component.translatable("hui.window.unavailable")), 176, 24);
    }

    private static WindowContent notAvailableHere() {
        return new TextWindowContent(List.of(Component.translatable("hui.window.inventory_unavailable")), 220, 24);
    }
}
