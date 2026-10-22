package com.chai.hui.network;

import com.chai.hui.HuiMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client asks the server to open HUI's server side creative inventory menu. */
public record OpenCreativePayload() implements CustomPacketPayload {
    public static final Type<OpenCreativePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "open_creative"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCreativePayload> STREAM_CODEC =
            StreamCodec.unit(new OpenCreativePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
