package com.chai.hui.client.network;

import com.chai.hui.client.windowing.HuiDesktopScreen;
import com.chai.hui.network.CloseMenuClientPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client side payload handlers (only loaded on the physical client). */
public final class HuiClientPayloads {
    private HuiClientPayloads() {}

    public static void onMenuClosed(CloseMenuClientPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() instanceof HuiDesktopScreen desktop) {
                desktop.closeBlockEntityWindow(payload.containerId());
            }
        });
    }
}
