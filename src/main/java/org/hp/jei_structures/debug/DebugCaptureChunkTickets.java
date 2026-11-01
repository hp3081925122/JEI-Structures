package org.hp.jei_structures.debug;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.hp.jei_structures.mixin.ServerChunkCacheAccessor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class DebugCaptureChunkTickets {

    public static final TicketType<ChunkPos> CAPTURE_TICKET = TicketType.create("jei_structures_capture", Comparator.comparingLong(ChunkPos::toLong));
    public static final TicketType<ChunkPos> LOCATE_TICKET = TicketType.create("jei_structures_locate", Comparator.comparingLong(ChunkPos::toLong), 1200);
    private static final long LOCATE_CHUNK_POLL_MILLIS = 50L;
    private static final Map<ResourceKey<Level>, LongSet> CAPTURED_CHUNKS = new HashMap<>();

    private DebugCaptureChunkTickets() {
    }

    public static void addCaptureTickets(ServerLevel level, List<ChunkPos> chunks) {
        ServerChunkCache chunkSource = level.getChunkSource();
        LongSet captured = CAPTURED_CHUNKS.computeIfAbsent(level.dimension(), ignored -> new LongOpenHashSet());
        for (ChunkPos chunkPos : chunks) {
            chunkSource.addRegionTicket(CAPTURE_TICKET, chunkPos, 0, chunkPos);
            captured.add(chunkPos.toLong());
        }
    }

    public static void removeCaptureTickets(ServerLevel level, List<ChunkPos> chunks) {
        ServerChunkCache chunkSource = level.getChunkSource();
        for (ChunkPos chunkPos : chunks) {
            chunkSource.removeRegionTicket(CAPTURE_TICKET, chunkPos, 0, chunkPos);
        }
    }

    public static int countReadyChunks(ServerLevel level, List<ChunkPos> chunks) {
        ServerChunkCache chunkSource = level.getChunkSource();
        int ready = 0;
        for (ChunkPos chunkPos : chunks) {
            if (chunkSource.getChunkNow(chunkPos.x, chunkPos.z) != null && level.areEntitiesLoaded(chunkPos.toLong())) {
                ready++;
            }
        }
        return ready;
    }

    public static void clearCapturedChunks() {
        CAPTURED_CHUNKS.clear();
    }

    public static boolean isCapturedChunk(ServerLevel level, ChunkPos chunkPos) {
        LongSet captured = CAPTURED_CHUNKS.get(level.dimension());
        return captured != null && captured.contains(chunkPos.toLong());
    }

    public static ChunkAccess getChunkForLocate(ServerChunkCache chunkSource, int x, int z, ChunkStatus status, boolean requireChunk) {
        ServerLevel level = chunkSource.level;
        MinecraftServer server = level.getServer();
        ChunkPos chunkPos = new ChunkPos(x, z);
        int ticketLevel = ChunkLevel.byStatus(status);
        ServerChunkCacheAccessor accessor = (ServerChunkCacheAccessor) chunkSource;
        CompletableFuture<ChunkResult<ChunkAccess>> future = CompletableFuture
                .supplyAsync(() -> {
                    if (requireChunk) {
                        accessor.jei_structures$getDistanceManager().addTicket(LOCATE_TICKET, chunkPos, ticketLevel, chunkPos);
                    }
                    return accessor.jei_structures$getChunkFutureMainThread(x, z, status, requireChunk);
                }, server)
                .thenCompose(Function.identity());
        if (requireChunk) {
            future.whenComplete((result, throwable) -> server.execute(() -> accessor.jei_structures$getDistanceManager().removeTicket(LOCATE_TICKET, chunkPos, ticketLevel, chunkPos)));
        }
        ChunkResult<ChunkAccess> result = awaitLocateFuture(future);
        ChunkAccess chunk = result.orElse(null);
        if (chunk == null && requireChunk) {
            throw new IllegalStateException("Chunk not there when requested: " + result.getError());
        }
        return chunk;
    }

    private static <T> T awaitLocateFuture(CompletableFuture<T> future) {
        while (true) {
            DebugLocateControl.checkCancelled();
            try {
                return future.get(LOCATE_CHUNK_POLL_MILLIS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | InterruptedException ignored) {
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException(cause);
            }
        }
    }
}
