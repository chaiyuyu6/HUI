package com.chai.hui;

import com.chai.hui.network.HuiNetwork;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * HUI - a small NeoForge mod about GUI and HUD tweaks for Minecraft 26.3.
 *
 * <p>Author: chai.
 *
 * <p>This class is the common entry point: it only registers the client configuration. Everything that touches
 * rendering, key bindings or screens lives in {@link com.chai.hui.client.HuiClient} and is only loaded on the
 * physical client.
 */
@Mod(HuiMod.MOD_ID)
public final class HuiMod {
    /** The mod id, must match the {@code modId} entry in {@code META-INF/neoforge.mods.toml}. */
    public static final String MOD_ID = "hui";

    /** Shared logger. */
    public static final Logger LOGGER = LogUtils.getLogger();

    public HuiMod(IEventBus modEventBus, ModContainer modContainer) {
        // HUI only changes the client, so its settings live in a CLIENT config (config/hui-client.toml).
        modContainer.registerConfig(ModConfig.Type.CLIENT, HuiConfig.SPEC);
        modEventBus.addListener(HuiNetwork::register);
        LOGGER.info("HUI (by chai) is loading - GUI/HUD tweaks for Minecraft 26.3");
    }
}
