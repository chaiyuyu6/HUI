package com.chai.hui.network;

import com.chai.hui.HuiMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: close this HUI window's container menu on the server. */
public record CloseMenuPayload(int containerId) implements CustomPacketPayload {
    public static final Type<CloseMenuPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "close_menu"));

    public static final StreamCodec<FriendlyByteBuf, CloseMenuPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CloseMenuPayload::containerId, CloseMenuPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
