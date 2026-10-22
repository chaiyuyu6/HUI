package com.chai.hui.client.network;

import com.chai.hui.client.screen.HuiScreens;
import com.chai.hui.client.ui.BlockUiContext;
import com.chai.hui.client.ui.HuiUiRegistry;
import com.chai.hui.client.ui.framework.UiNode;
import com.chai.hui.client.ui.framework.UiWindowContent;
import com.chai.hui.client.windowing.HuiDesktopScreen;
import com.chai.hui.network.BlockUiActionPayload;
import com.chai.hui.network.BlockUiDataPayload;
import com.chai.hui.network.OpenBlockUiPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Client side of the HUI block UI channel: right clicking a registered block entity opens a HUI window even when the
 * block has no vanilla GUI, and the server sends the data for it.
 */
public final class HuiBlockUiClient {
    private HuiBlockUiClient() {
    }

    public static void registerHooks() {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (!event.getLevel().isClientSide()) {
                return;
            }
            BlockPos pos = event.getPos();
            BlockEntity blockEntity = event.getLevel().getBlockEntity(pos);
            if (blockEntity == null || !HuiUiRegistry.isBlockRegistered(blockEntity.getType())) {
                return;
            }
            // Registered block entity: HUI takes over, vanilla use/interaction is suppressed on the client.
            ClientPacketDistributor.sendToServer(new OpenBlockUiPayload(pos));
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
        });
    }

    public static void handleData(BlockUiDataPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> handleData(payload));
    }

    private static void handleData(BlockUiDataPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        HuiUiRegistry.BlockUiFactory factory = HuiUiRegistry.blockUi(payload.typeId());
        if (factory == null || minecraft.level == null) {
            return;
        }
        BlockEntity blockEntity = minecraft.level.getBlockEntity(payload.pos());
        Component title = blockEntity == null
                ? Component.literal(payload.typeId().toString())
                : blockEntity.getBlockState().getBlock().getName();
        BlockUiContext context = new BlockUiContext(payload.pos(), payload.typeId(), payload.data(),
                (action, data) -> ClientPacketDistributor
                        .sendToServer(new BlockUiActionPayload(payload.pos(), action,
                                data == null ? new CompoundTag() : data)),
                blockEntity);
        UiNode root = factory.build(context);
        UiWindowContent content = new UiWindowContent(root);
        if (minecraft.gui.screen() instanceof HuiDesktopScreen desktop) {
            desktop.addBlockUiWindow(payload.pos(), title, content);
        } else {
            HuiScreens.open(minecraft, HuiDesktopScreen.openWithBlockUi(payload.pos(), title, content));
        }
    }
}
