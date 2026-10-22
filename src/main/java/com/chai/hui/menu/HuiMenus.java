package com.chai.hui.menu;

import com.chai.hui.HuiMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Menus added by HUI. */
public final class HuiMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HuiMod.MOD_ID);

    /** The server side creative inventory used by the HUI creative window. */
    public static final DeferredHolder<MenuType<?>, MenuType<HuiCreativeMenu>> CREATIVE =
            MENUS.register("creative", () -> new MenuType<>(
                    (containerId, inventory) -> new HuiCreativeMenu(containerId, inventory), FeatureFlags.VANILLA_SET));

    private HuiMenus() {
    }
}
