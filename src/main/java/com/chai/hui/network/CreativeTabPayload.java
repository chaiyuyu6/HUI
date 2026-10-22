package com.chai.hui.network;

import com.chai.hui.HuiMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client asks the server to refill the creative display slots (tab change / search). */
public record CreativeTabPayload(int tabIndex, String search) implements CustomPacketPayload {
    public static final Type<CreativeTabPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "creative_tab"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreativeTabPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CreativeTabPayload::tabIndex,
            ByteBufCodecs.STRING_UTF8, CreativeTabPayload::search,
            CreativeTabPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
