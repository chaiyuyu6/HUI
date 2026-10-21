package com.chai.hui.network;

import com.chai.hui.HuiMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: make this container the active menu (so clicks in that window are routed to it). */
public record ActivateMenuPayload(int containerId) implements CustomPacketPayload {
    public static final Type<ActivateMenuPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "activate_menu"));

    public static final StreamCodec<FriendlyByteBuf, ActivateMenuPayload> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, ActivateMenuPayload::containerId, ActivateMenuPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
