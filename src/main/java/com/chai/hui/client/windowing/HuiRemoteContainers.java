package com.chai.hui.client.windowing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import com.chai.hui.network.OpenBlockEntityPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Client side helper for opening block entity containers in HUI windows.
 *
 * <p>The client scans nearby blocks that provide a menu for the "open" list and asks the server to open one. Requests are
 * queued in order: the server answers in the same order, so when a container screen arrives the client knows which
 * position (and which restored window placeholder) it belongs to.
 */
public final class HuiRemoteContainers {
    private static final int REQUEST_TIMEOUT_TICKS = 200;

    private static final Deque<Request> OPEN_QUEUE = new ArrayDeque<>();

    private HuiRemoteContainers() {}

    private static final class Request {
        private final BlockPos pos;
        private int age;

        private Request(BlockPos pos) {
            this.pos = pos;
        }
    }

    /** One selectable block/block entity in the "open container" list. */
    public record ContainerEntry(BlockPos pos, Component name, double distance) {}

    public static void request(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        OPEN_QUEUE.addLast(new Request(immutable));
        ClientPacketDistributor.sendToServer(new OpenBlockEntityPayload(immutable));
    }

    /** @return the position of the next container screen that the server is about to open. */
    public static @Nullable BlockPos pollOpened() {
        Request request = OPEN_QUEUE.pollFirst();
        return request == null ? null : request.pos;
    }

    public static void remove(BlockPos pos) {
        for (Iterator<Request> iterator = OPEN_QUEUE.iterator(); iterator.hasNext();) {
            if (iterator.next().pos.equals(pos)) {
                iterator.remove();
                return;
            }
        }
    }

    public static boolean hasPending() {
        return !OPEN_QUEUE.isEmpty();
    }

    /** Expires stale requests (e.g. the server refused to open a container). */
    public static void tick() {
        for (Iterator<Request> iterator = OPEN_QUEUE.iterator(); iterator.hasNext();) {
            Request request = iterator.next();
            if (++request.age > REQUEST_TIMEOUT_TICKS) {
                iterator.remove();
            }
        }
    }

    /** @return nearby blocks that can provide a menu, sorted by distance. */
    public static List<ContainerEntry> findNearby(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return List.of();
        }
        BlockPos center = minecraft.player.blockPosition();
        int radius = 8;
        List<ContainerEntry> entries = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            net.minecraft.world.level.block.state.BlockState state = minecraft.level.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            MenuProvider provider = state.getMenuProvider(minecraft.level, cursor);
            if (provider == null) {
                continue;
            }
            BlockPos pos = cursor.immutable();
            double distance = Math.sqrt(minecraft.player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D));
            entries.add(new ContainerEntry(pos, state.getBlock().getName(), distance));
        }
        entries.sort(Comparator.comparingDouble(ContainerEntry::distance));
        return entries;
    }
}
