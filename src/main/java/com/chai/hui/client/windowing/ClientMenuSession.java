package com.chai.hui.client.windowing;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only menu state isolation.
 *
 * <p>Client-only screens (the creative item picker is the prime example) have no server side menu: their carried stack
 * lives only in the client and any server driven full state sync (which carries an empty carried stack) would wipe it.
 * A session shadows the carried stack and restores it when an external sync clears it, so a carried creative item can
 * never be "lost" by HUI activating another menu window.
 *
 * <p>Server interactions of a client-only window must not go through {@code player.containerMenu}; HUI writes back via
 * the vanilla creative slot packets ({@code handleCreativeModeItemAdd}) and the server validated
 * {@code CreativeInsertPayload} bridge when dropping into a server backed container window.
 */
public final class ClientMenuSession {
    private ItemStack carried = ItemStack.EMPTY;

    /** Reads the live carried stack and keeps the shadow copy up to date. */
    public void capture(ItemStack live) {
        if (!live.isEmpty()) {
            this.carried = live.copy();
        }
    }

    /** Restores the shadow copy when a server sync cleared the live carried stack. */
    public void reconcile(AbstractContainerMenu menu) {
        capture(menu.getCarried());
        if (menu.getCarried().isEmpty() && !this.carried.isEmpty()) {
            menu.setCarried(this.carried.copy());
        }
    }

    /** Explicitly clears the carried stack (window closed). */
    public void clear(AbstractContainerMenu menu) {
        this.carried = ItemStack.EMPTY;
        if (!menu.getCarried().isEmpty()) {
            menu.setCarried(ItemStack.EMPTY);
        }
    }

    /** Updates the shadow copy after a HUI driven click changed the carried stack. */
    public void set(ItemStack stack) {
        this.carried = stack.copy();
    }

    public ItemStack carried() {
        return this.carried;
    }

    public boolean isEmpty() {
        return this.carried.isEmpty();
    }

    public ItemStack get() {
        return this.carried.copy();
    }
}
