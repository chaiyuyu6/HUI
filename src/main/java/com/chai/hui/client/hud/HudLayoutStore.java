package com.chai.hui.client.hud;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.chai.hui.HuiMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Reads and writes {@link HudLayout} as a small JSON document in the config directory.
 *
 * <p>The file is intentionally independent from the NeoForge config system: the set of blocks and their order is
 * dynamic, and the layout is global for the whole game installation (not per world).
 */
public final class HudLayoutStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "hui-hud-layout.json";
    private static final int FORMAT_VERSION = 1;

    private HudLayoutStore() {}

    /** @return the layout file location, {@code config/hui-hud-layout.json}. */
    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    /**
     * Loads the persisted layout.
     *
     * <p>Unknown or missing blocks get their default entries, and a missing/corrupt file silently falls back to the
     * default layout so a broken config never prevents the game or the editor from working.
     */
    public static HudLayout load(Collection<Identifier> knownIds) {
        HudLayout fallback = defaultLayout(knownIds);
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return fallback;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            FileData data = GSON.fromJson(reader, FileData.class);
            if (data == null || data.elements == null) {
                return fallback;
            }
            HudLayout layout = new HudLayout();
            int defaultOrder = 0;
            for (Identifier id : knownIds) {
                HudLayout.Entry entry = layout.ensure(id, defaultOrder++);
                EntryData stored = data.elements.get(id.toString());
                if (stored != null) {
                    entry.offsetX = stored.offsetX;
                    entry.offsetY = stored.offsetY;
                    entry.hidden = stored.hidden;
                    entry.order = stored.order;
                }
            }
            return layout;
        } catch (IOException | JsonParseException exception) {
            HuiMod.LOGGER.warn("Could not read HUD layout from {}, using defaults", path, exception);
            return fallback;
        }
    }

    /** Writes the layout, creating the config directory if necessary. */
    public static void save(HudLayout layout) {
        FileData data = new FileData();
        data.version = FORMAT_VERSION;
        layout.ordered(layoutEntries(layout)).forEach(id -> {
            HudLayout.Entry entry = layout.entry(id);
            EntryData stored = new EntryData();
            stored.offsetX = entry.offsetX();
            stored.offsetY = entry.offsetY();
            stored.hidden = entry.hidden();
            stored.order = entry.order();
            data.elements.put(id.toString(), stored);
        });

        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException exception) {
            HuiMod.LOGGER.error("Could not save HUD layout to {}", path, exception);
        }
    }

    private static Collection<Identifier> layoutEntries(HudLayout layout) {
        // The layout itself is the source of truth for which blocks exist.
        return layout.ids();
    }

    private static HudLayout defaultLayout(Collection<Identifier> knownIds) {
        HudLayout layout = new HudLayout();
        int order = 0;
        for (Identifier id : knownIds) {
            layout.ensure(id, order++);
        }
        return layout;
    }

    /** JSON root of the layout file. */
    private static final class FileData {
        int version = FORMAT_VERSION;
        Map<String, EntryData> elements = new LinkedHashMap<>();
    }

    /** JSON representation of a single block entry. */
    private static final class EntryData {
        int offsetX;
        int offsetY;
        boolean hidden;
        int order;
    }
}
