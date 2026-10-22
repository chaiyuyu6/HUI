package com.chai.hui.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

/**
 * Server side half of the HUI block UI channel: the data provider and action handler for block entities that have a HUI
 * UI but no vanilla GUI.
 */
public final class HuiBlockUiServer {
    public interface Provider {
        /** NBT sent to the client UI (initial open and after every action). */
        CompoundTag writeData(ServerPlayer player, BlockEntity blockEntity);

        /** Called when the client UI sends an action; return normally and the (rebuilt) data is sent back. */
        default void onAction(ServerPlayer player, BlockEntity blockEntity, String action, CompoundTag payload) {}

        /** Optional block entity type id used by the client to pick its UI factory. */
        default @Nullable Identifier typeId() {
            return null;
        }
    }

    private static final Map<Identifier, Provider> PROVIDERS = new ConcurrentHashMap<>();

    private HuiBlockUiServer() {
    }

    public static void register(BlockEntityType<?> type, Provider provider) {
        Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        if (id != null) {
            PROVIDERS.put(id, provider);
        }
    }

    public static @Nullable Provider provider(Identifier typeId) {
        return PROVIDERS.get(typeId);
    }

    public static @Nullable Identifier typeId(BlockEntity blockEntity) {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
    }
}
