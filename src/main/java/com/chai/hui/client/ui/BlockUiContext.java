package com.chai.hui.client.ui;

import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Context handed to {@link HuiUiRegistry.BlockUiFactory}: the block position, the raw data the server sent, and a
 * channel to send actions back to the server.
 */
public final class BlockUiContext {
    private final BlockPos pos;
    private final Identifier typeId;
    private final CompoundTag data;
    private final BiConsumer<String, CompoundTag> actionSender;
    private final @Nullable BlockEntity blockEntity;

    public BlockUiContext(BlockPos pos, Identifier typeId, CompoundTag data,
            BiConsumer<String, CompoundTag> actionSender, @Nullable BlockEntity blockEntity) {
        this.pos = pos;
        this.typeId = typeId;
        this.data = data;
        this.actionSender = actionSender;
        this.blockEntity = blockEntity;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public Identifier typeId() {
        return this.typeId;
    }

    public CompoundTag data() {
        return this.data;
    }

    public @Nullable BlockEntity blockEntity() {
        return this.blockEntity;
    }

    /** Sends a UI action (button, input, ...) to the server side block UI provider. */
    public void sendAction(String action, CompoundTag payload) {
        this.actionSender.accept(action, payload);
    }

    public void sendAction(String action) {
        sendAction(action, new CompoundTag());
    }
}
