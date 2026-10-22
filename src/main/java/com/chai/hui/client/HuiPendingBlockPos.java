package com.chai.hui.client;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Remembers the block position of the last client side right click that opened a container, so HUI can recognise the
 * block entity window that is being opened (used to avoid opening the same block entity UI twice).
 */
public final class HuiPendingBlockPos {
    private static @Nullable BlockPos last;

    private HuiPendingBlockPos() {
    }

    public static void set(BlockPos pos) {
        last = pos;
    }

    public static @Nullable BlockPos poll() {
        BlockPos pos = last;
        last = null;
        return pos;
    }

    public static void clear() {
        last = null;
    }
}
