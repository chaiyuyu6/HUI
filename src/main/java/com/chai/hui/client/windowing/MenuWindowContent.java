package com.chai.hui.client.windowing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/**
 * Window content backed by a container menu that also exists on the server.
 *
 * <p>HUI renders its own UI; the menu is used for data synchronisation (slot contents, carried stack) and for routing
 * clicks through {@code handleContainerInput}. The vanilla container screen is never rendered.
 */
public interface MenuWindowContent extends WindowContent {
    AbstractContainerMenu screenMenu();

    int menuContainerId();

    /** True when the server knows this menu (creative inventory is client only). */
    boolean serverBacked();

    /** Makes this window's menu the current server side menu so click packets are routed to it. */
    void activate();

    /** @return the slot index of this menu under the mouse, or -1. */
    default int slotIndexAt(int mouseX, int mouseY, int originX, int originY) {
        return -1;
    }

    /** @return the block position this window was opened from (for restoring pinned container windows). */
    default @Nullable BlockPos sourcePos() {
        return null;
    }
}
