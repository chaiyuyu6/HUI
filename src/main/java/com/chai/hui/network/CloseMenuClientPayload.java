package com.chai.hui.network;

import com.chai.hui.HuiMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: this container menu is no longer valid, close just its HUI window. */
public record CloseMenuClientPayload(int containerId) implements CustomPacketPayload {
    public static final Type<CloseMenuClientPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "close_menu_client"));

    public static final StreamCodec<FriendlyByteBuf, CloseMenuClientPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CloseMenuClientPayload::containerId, CloseMenuClientPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
