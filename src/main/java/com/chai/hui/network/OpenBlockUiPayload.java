package com.chai.hui.network;

import com.chai.hui.HuiMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client asks the server to open the HUI UI of a registered block entity (used when the block has no vanilla GUI). */
public record OpenBlockUiPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenBlockUiPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "open_block_ui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBlockUiPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenBlockUiPayload::pos,
            OpenBlockUiPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
