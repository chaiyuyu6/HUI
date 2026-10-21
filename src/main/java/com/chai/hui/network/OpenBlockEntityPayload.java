package com.chai.hui.network;

import com.chai.hui.HuiMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client to server: "open the container at this position as a HUI window".
 *
 * <p>The server validates the position (loaded, block entity still there, within the player's block interaction range)
 * and then opens the block entity's menu, which the client turns into a window instead of a full screen.
 */
public record OpenBlockEntityPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenBlockEntityPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "open_block_entity"));

    public static final StreamCodec<FriendlyByteBuf, OpenBlockEntityPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenBlockEntityPayload::pos, OpenBlockEntityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
