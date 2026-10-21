package com.chai.hui.network;

import java.util.OptionalInt;

import com.chai.hui.HuiMod;
import com.chai.hui.server.HuiMenuManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * HUI's network channel.
 *
 * <p>P3 scope: the client asks the server to open block entity containers, to activate one of the open menus (clicks are
 * routed to {@code player.containerMenu}), and to close a window's menu. The server re-validates everything; the client
 * scan is only a convenience.
 */
public final class HuiNetwork {
    private HuiNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(OpenBlockEntityPayload.TYPE, OpenBlockEntityPayload.STREAM_CODEC,
                HuiNetwork::handleOpenBlockEntity);
        registrar.playToServer(ActivateMenuPayload.TYPE, ActivateMenuPayload.STREAM_CODEC,
                HuiNetwork::handleActivateMenu);
        registrar.playToServer(CloseMenuPayload.TYPE, CloseMenuPayload.STREAM_CODEC, HuiNetwork::handleCloseMenu);
        registrar.playToClient(CloseMenuClientPayload.TYPE, CloseMenuClientPayload.STREAM_CODEC,
                com.chai.hui.client.network.HuiClientPayloads::onMenuClosed);
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
            // Park the current menu without closing it, so vanilla openMenu does not send a close for it. HUI keeps the
            // parked menus tracked and hot-swaps containerMenu on demand instead.
            if (player.containerMenu != player.inventoryMenu) {
                HuiMenuManager.register(player, player.containerMenu.containerId, player.containerMenu);
                player.containerMenu = player.inventoryMenu;
            }
            OptionalInt opened = player.openMenu(provider);
            if (opened.isPresent()) {
                HuiMenuManager.register(player, opened.getAsInt(), player.containerMenu);
                HuiMod.LOGGER.debug("Opened container {} ({}) at {}", opened.getAsInt(),
                        player.containerMenu.getType(), pos);
            } else {
                player.sendSystemMessage(Component.translatable("hui.container.unreachable"));
            }
        });
    }

    private static void handleActivateMenu(ActivateMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            AbstractContainerMenu menu = HuiMenuManager.get(player, payload.containerId());
            if (menu == null) {
                return;
            }
            if (!menu.stillValid(player)) {
                HuiMenuManager.closeMenu(player, payload.containerId());
                PacketDistributor.sendToPlayer(player, new CloseMenuClientPayload(payload.containerId()));
                return;
            }
            if (player.containerMenu != menu) {
                // Move the carried stack to the menu that becomes active, so two-click cross-window transfers work
                // (pick up in one window, click a slot in another window).
                net.minecraft.world.item.ItemStack carried = player.containerMenu.getCarried();
                if (!carried.isEmpty()) {
                    player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                }
                menu.setCarried(carried);
                player.containerMenu = menu;
                menu.broadcastFullState();
            }
        });
    }

    private static void handleCloseMenu(CloseMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                HuiMenuManager.closeMenu(player, payload.containerId());
            }
        });
    }
}
