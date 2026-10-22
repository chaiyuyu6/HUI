package com.chai.hui.network;

import com.chai.hui.HuiMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client (and client to server) request: clear the carried stack of a menu.
 *
 * <p>Used when the player hands a server backed carried stack over to a client only window (creative inventory):
 * the server side menu must drop its carried copy, otherwise the stack would be duplicated when the client only UI
 * writes the same stack into the player inventory.
 */
public record ClearCarriedPayload(int containerId) implements CustomPacketPayload {
    public static final Type<ClearCarriedPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "clear_carried"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearCarriedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ClearCarriedPayload::containerId,
            ClearCarriedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
