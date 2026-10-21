package com.chai.hui;

import com.chai.hui.network.HuiNetwork;
import com.chai.hui.server.HuiMenuManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
        // Tracked container menus: periodically close the ones that became invalid (block removed / out of range) and
        // clean up on logout so no menu outlives its player.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> HuiMenuManager.validateAll(event.getServer()));
        // Menus opened directly by vanilla (right clicking a chest/brewing stand/...) must be tracked too, otherwise
        // activating another window cannot move the carried stack to them.
        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Open event) -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getContainer().containerId != 0) {
                HuiMenuManager.register(player, event.getContainer().containerId, event.getContainer());
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Close event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                HuiMenuManager.untrack(player, event.getContainer().containerId);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                HuiMenuManager.clear(player);
            }
        });
        LOGGER.info("HUI (by chai) is loading - GUI/HUD tweaks for Minecraft 26.3");
    }
}
