package com.chai.hui.client.windowing;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.chai.hui.HuiMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores {@link DesktopLayout} inside the save directory ({@code <world>/hui/desktop.json}), which matches the
 * "save scoped" decision in {@code docs/windowing-design.md}.
 *
 * <p>Singleplayer only: if there is no integrated server (e.g. title screen) the store is unavailable and the desktop
 * simply keeps its in-memory layout.
 */
public final class DesktopLayoutStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("HUI/DesktopLayoutStore");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String RELATIVE_PATH = "hui/desktop.json";

    private static @Nullable DesktopLayout cached;

    private DesktopLayoutStore() {}

    /** @return the layout file, or {@code null} when no integrated server is running. */
    public static @Nullable Path file() {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (minecraft.getSingleplayerServer() == null) {
                return null;
            }
            return minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).resolve(RELATIVE_PATH);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static DesktopLayout load() {
        if (cached != null) {
            return cached;
        }
        Path path = file();
        DesktopLayout layout = null;
        if (path != null && Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                layout = GSON.fromJson(reader, DesktopLayout.class);
            } catch (IOException | JsonParseException exception) {
                LOGGER.warn("Could not read the desktop layout from {}, using defaults", path, exception);
            }
        }
        if (layout == null) {
            layout = DesktopLayout.createDefault();
        }
        layout.ensurePages();
        cached = layout;
        return layout;
    }

    public static void save(DesktopLayout layout) {
        Path path = file();
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(layout, writer);
            }
            HuiMod.LOGGER.debug("Saved desktop layout to {}", path);
        } catch (IOException exception) {
            LOGGER.error("Could not save the desktop layout to {}", path, exception);
        }
    }

    /** Drops the cached layout, e.g. when leaving the world. */
    public static void invalidate() {
        cached = null;
    }

    /** @return whether the next inventory key press should open the HUI desktop instead of the vanilla inventory. */
    public static boolean isHuiInventoryEnabled() {
        return load().huiInventory;
    }
}
