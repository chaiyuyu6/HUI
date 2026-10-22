package com.chai.hui.client.ui;

import com.chai.hui.network.ActivateMenuPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Context handed to {@link HuiUiRegistry.MenuUiFactory} implementations: the menu plus helpers to activate it and route
 * clicks exactly like a vanilla container screen would.
 */
public final class MenuUiContext {
    private final AbstractContainerMenu menu;
    private final Component title;
    private final Runnable activator;

    public MenuUiContext(AbstractContainerMenu menu, Component title, Runnable activator) {
        this.menu = menu;
        this.title = title;
        this.activator = activator;
    }

    public AbstractContainerMenu menu() {
        return this.menu;
    }

    public Component title() {
        return this.title;
    }

    public int containerId() {
        return this.menu.containerId;
    }

    /** Makes this menu the server side active menu, so the click below is routed to it. */
    public void activate() {
        this.activator.run();
    }

    /** Routes one container input to the server (same path as {@code AbstractContainerScreen#slotClicked}). */
    public void click(int slotIndex, int button, ContainerInput input) {
        activate();
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player != null) {
            minecraft.gameMode.handleContainerInput(this.menu.containerId, slotIndex, button, input, player);
        }
    }

    /** Convenience helper for mods that only want to use the HUI UI as a renderer for menu slots. */
    public static void activateMenu(AbstractContainerMenu menu) {
        ClientPacketDistributor.sendToServer(new ActivateMenuPayload(menu.containerId));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.containerMenu = menu;
        }
    }
}
