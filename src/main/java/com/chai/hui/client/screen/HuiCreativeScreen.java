package com.chai.hui.client.screen;

import com.chai.hui.menu.HuiCreativeMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Vanilla screen stub for {@link HuiCreativeMenu}.
 *
 * <p>HUI intercepts the screen opening event and turns this into a HUI window; the stub only exists so the menu gets a
 * client side screen object (menu carrier) and never renders by itself.
 */
public final class HuiCreativeScreen extends AbstractContainerScreen<HuiCreativeMenu> {
    public HuiCreativeScreen(HuiCreativeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }
}
