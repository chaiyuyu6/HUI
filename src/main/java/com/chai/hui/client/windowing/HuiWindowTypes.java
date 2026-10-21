package com.chai.hui.client.windowing;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chai.hui.HuiMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Registry of the built-in window types.
 *
 * <p>Every type has a factory so a window can be recreated from the persisted layout. The public registration API for
 * other mods is planned for P4; for now this registry is client internal.
 */
public final class HuiWindowTypes {
    public static final Identifier INVENTORY = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "inventory");
    public static final Identifier DEMO = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "demo");
    public static final Identifier PROPERTIES = Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "properties");

    private static final Map<Identifier, WindowType> TYPES = new LinkedHashMap<>();

    static {
        register(new WindowType(INVENTORY, "hui.window.inventory", () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return new TextWindowContent(List.of(Component.translatable("hui.window.unavailable")), 176, 24);
            }
            return new EmbeddedScreenContent(minecraft.player);
        }));
        register(new WindowType(DEMO, "hui.window.demo", TextWindowContent::demo));
        register(new WindowType(PROPERTIES, "hui.window.properties",
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
}
