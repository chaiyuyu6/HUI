package com.chai.hui.network;

import com.chai.hui.HuiMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: an action from a HUI block UI (button click, text submit, ...). */
public record BlockUiActionPayload(BlockPos pos, String action, CompoundTag payload)
        implements CustomPacketPayload {
    public static final Type<BlockUiActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "block_ui_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlockUiActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BlockUiActionPayload::pos,
            ByteBufCodecs.STRING_UTF8, BlockUiActionPayload::action,
            ByteBufCodecs.COMPOUND_TAG, BlockUiActionPayload::payload,
            BlockUiActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
