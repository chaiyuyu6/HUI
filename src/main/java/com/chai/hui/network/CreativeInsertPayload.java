package com.chai.hui.network;

import com.chai.hui.HuiMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Client to server: insert a creative-mode item stack into a container window slot.
 *
 * <p>The creative inventory is a client only menu, so a stack taken from it exists nowhere on the server. This payload
 * lets creative players place it into a tracked container menu slot (the server validates creative mode and the slot's
 * {@code mayPlace}).
 */
public record CreativeInsertPayload(int containerId, int slotIndex, ItemStack stack) implements CustomPacketPayload {
    public static final Type<CreativeInsertPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(HuiMod.MOD_ID, "creative_insert"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreativeInsertPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CreativeInsertPayload::containerId,
            ByteBufCodecs.VAR_INT, CreativeInsertPayload::slotIndex,
            ItemStack.STREAM_CODEC, CreativeInsertPayload::stack,
            CreativeInsertPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
