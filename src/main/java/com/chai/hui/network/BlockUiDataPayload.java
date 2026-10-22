package com.chai.hui.network;

import com.chai.hui.HuiMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: data for a HUI block UI (initial open or after an action). */
public record BlockUiDataPayload(BlockPos pos, Identifier typeId, CompoundTag data) implements CustomPacketPayload {
    public static final Type<BlockUiDataPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "block_ui_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlockUiDataPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BlockUiDataPayload::pos,
            Identifier.STREAM_CODEC, BlockUiDataPayload::typeId,
            ByteBufCodecs.COMPOUND_TAG, BlockUiDataPayload::data,
            BlockUiDataPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
