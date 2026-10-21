package com.chai.hui.network;

import com.chai.hui.HuiMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: the result of an open request (the client uses it to drop stale restore placeholders). */
public record OpenBlockEntityResultPayload(BlockPos pos, boolean success) implements CustomPacketPayload {
    public static final Type<OpenBlockEntityResultPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "open_block_entity_result"));

    public static final StreamCodec<FriendlyByteBuf, OpenBlockEntityResultPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, OpenBlockEntityResultPayload::pos,
                    net.minecraft.network.codec.ByteBufCodecs.BOOL, OpenBlockEntityResultPayload::success,
                    OpenBlockEntityResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
