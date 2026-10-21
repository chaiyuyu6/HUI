package com.chai.hui.client.windowing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.chai.hui.network.OpenBlockEntityPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Client side helper for opening block entity containers in HUI windows.
 *
 * <p>The client scans nearby {@link MenuProvider} block entities for the "open" list and asks the server to open one.
 * The pending request tells the {@code ScreenEvent.Opening} handler that the next container screen should become a
 * window instead of a full screen.
 */
public final class HuiRemoteContainers {
    private static final int REQUEST_TIMEOUT_TICKS = 100;

    private static @Nullable BlockPos pending;
    private static int pendingTicks;

    private HuiRemoteContainers() {}

    /** One selectable block entity in the "open container" list. */
    public record ContainerEntry(BlockPos pos, Component name, double distance) {}

    public static void request(BlockPos pos) {
        pending = pos.immutable();
        pendingTicks = REQUEST_TIMEOUT_TICKS;
        ClientPacketDistributor.sendToServer(new OpenBlockEntityPayload(pending));
    }

    public static boolean hasPending() {
        return pending != null;
    }

    public static void clearPending() {
        pending = null;
        pendingTicks = 0;
    }

    /** Expires stale requests (e.g. the server refused to open the container). */
    public static void tick() {
        if (pending != null && --pendingTicks <= 0) {
            clearPending();
        }
    }

    /** @return nearby block entities that can provide a menu, sorted by distance. */
    public static List<ContainerEntry> findNearby(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return List.of();
        }
        BlockPos center = minecraft.player.blockPosition();
        int radius = 8;
        List<ContainerEntry> entries = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            BlockEntity blockEntity = minecraft.level.getBlockEntity(cursor);
            if (!(blockEntity instanceof MenuProvider)) {
                continue;
            }
            BlockPos pos = cursor.immutable();
            double distance = Math.sqrt(minecraft.player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D));
            entries.add(new ContainerEntry(pos, minecraft.level.getBlockState(pos).getBlock().getName(), distance));
        }
        entries.sort(Comparator.comparingDouble(ContainerEntry::distance));
        return entries;
    }
}
