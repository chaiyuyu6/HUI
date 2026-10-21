package com.chai.hui.network;

import com.chai.hui.HuiMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * HUI's network channel (singleplayer oriented, but the server side is a normal payload handler).
 *
 * <p>P3 scope: the client asks the server to open a block entity container; the server re-validates it (the client scan
 * is only a convenience) and opens the menu. Vanilla's {@code openMenu} closes any previous menu, so only one block
 * entity window can be live at a time for now.
 */
public final class HuiNetwork {
    private HuiNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(OpenBlockEntityPayload.TYPE, OpenBlockEntityPayload.STREAM_CODEC,
                HuiNetwork::handleOpenBlockEntity);
    }

    private static void handleOpenBlockEntity(OpenBlockEntityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel level = player.level();
            BlockPos pos = payload.pos();
            if (!level.isLoaded(pos) || !player.isWithinBlockInteractionRange(pos, 1.0D)) {
                player.sendSystemMessage(Component.translatable("hui.container.unreachable"));
                return;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof MenuProvider provider)) {
                player.sendSystemMessage(Component.translatable("hui.container.unreachable"));
                return;
            }
            HuiMod.LOGGER.debug("Opening container {} for {}", pos, player.getName().getString());
            player.openMenu(provider);
        });
    }
}
