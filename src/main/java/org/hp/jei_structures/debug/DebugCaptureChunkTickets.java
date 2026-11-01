package org.hp.jei_structures.debug;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.hp.jei_structures.JeiStructures;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hp.jei_structures.mixin.ServerChunkCacheAccessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class DebugCaptureChunkTickets {

    public static final DeferredRegister<TicketType> TICKET_TYPES = DeferredRegister.create(Registries.TICKET_TYPE, JeiStructures.MODID);
    public static final DeferredHolder<TicketType, TicketType> CAPTURE_TICKET = TICKET_TYPES.register("capture", () -> new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));
    private static volatile long lastHasChunkDeniedLogMillis;
    public static final DeferredHolder<TicketType, TicketType> PLAYER_HOLD_TICKET = TICKET_TYPES.register("player_hold", () -> new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));
    public static final DeferredHolder<TicketType, TicketType> LOCATE_TICKET = TICKET_TYPES.register("locate", () -> new TicketType(1200L, TicketType.FLAG_LOADING));
    private static final Map<ResourceKey<Level>, Map<Long, Integer>> LOCATE_TICKET_COUNTS = new HashMap<>();
    private static final long LOCATE_CHUNK_POLL_MILLIS = 50L;
    private static final Map<ResourceKey<Level>, LongSet> CAPTURED_CHUNKS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long2IntOpenHashMap> CAPTURE_TICKET_COUNTS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<Long>> FEATURE_SKIPPED_CHUNKS = new ConcurrentHashMap<>();

    private DebugCaptureChunkTickets() {
    }

    public static void addCaptureTickets(ServerLevel level, List<ChunkPos> chunks) {
        ServerChunkCache chunkSource = level.getChunkSource();
        LongSet captured = CAPTURED_CHUNKS.computeIfAbsent(level.dimension(), ignored -> new LongOpenHashSet());
        Long2IntOpenHashMap counts = CAPTURE_TICKET_COUNTS.computeIfAbsent(level.dimension(), ignored -> new Long2IntOpenHashMap());
        for (ChunkPos chunkPos : chunks) {
            if (counts.addTo(chunkPos.pack(), 1) == 0) {
                chunkSource.addTicketWithRadius(CAPTURE_TICKET.get(), chunkPos, 0);
            }
            captured.add(chunkPos.pack());
        }
    }

    public static void addPlayerHoldTicket(ServerLevel level, ChunkPos chunkPos) {
        level.getChunkSource().addTicketWithRadius(PLAYER_HOLD_TICKET.get(), chunkPos, 0);
    }

    public static void removePlayerHoldTicket(ServerLevel level, ChunkPos chunkPos) {
        level.getChunkSource().removeTicketWithRadius(PLAYER_HOLD_TICKET.get(), chunkPos, 0);
    }

    public static void removeCaptureTickets(ServerLevel level, List<ChunkPos> chunks) {
        ServerChunkCache chunkSource = level.getChunkSource();
        Long2IntOpenHashMap counts = CAPTURE_TICKET_COUNTS.get(level.dimension());
        if (counts == null) {
            return;
        }
        for (ChunkPos chunkPos : chunks) {
            long key = chunkPos.pack();
            int count = counts.get(key);
            if (count <= 0) {
                continue;
            }
            if (count == 1) {
                counts.remove(key);
                chunkSource.removeTicketWithRadius(CAPTURE_TICKET.get(), chunkPos, 0);
            } else {
                counts.put(key, count - 1);
            }
        }
    }

    public static int countReadyChunks(ServerLevel level, List<ChunkPos> chunks) {
        ServerChunkCache chunkSource = level.getChunkSource();
        int ready = 0;
        for (ChunkPos chunkPos : chunks) {
            if (chunkSource.getChunkNow(chunkPos.x(), chunkPos.z()) != null && level.areEntitiesLoaded(chunkPos.pack())) {
                ready++;
            }
        }
        return ready;
    }

    public static void clearCapturedChunks() {
        CAPTURED_CHUNKS.clear();
        CAPTURE_TICKET_COUNTS.clear();
    }

    public static boolean isCapturedChunk(ServerLevel level, ChunkPos chunkPos) {
        LongSet captured = CAPTURED_CHUNKS.get(level.dimension());
        return captured != null && captured.contains(chunkPos.pack());
    }

    public static void markFeatureSkippedChunk(ServerLevel level, ChunkPos chunkPos) {
        FEATURE_SKIPPED_CHUNKS.computeIfAbsent(level.dimension(), ignored -> ConcurrentHashMap.newKeySet()).add(chunkPos.pack());
    }

    public static boolean isFeatureSkippedChunk(ServerLevel level, ChunkPos chunkPos) {
        Set<Long> skipped = FEATURE_SKIPPED_CHUNKS.get(level.dimension());
        return skipped != null && skipped.contains(chunkPos.pack());
    }

    public static void logOffThreadHasChunkDenied(int x, int z) {
        long now = System.currentTimeMillis();
        if (now - lastHasChunkDeniedLogMillis < 1000L) {
            return;
        }
        lastHasChunkDeniedLogMillis = now;
        JeiStructures.LOGGER.info("[JEI_DEBUG] off-thread hasChunk denied for unfinished chunk=[{}, {}] thread={}", x, z, Thread.currentThread().getName());
    }

    public static void logMainThreadSyncLoad(ServerChunkCache chunkSource, int x, int z, ChunkStatus status, boolean requireChunk) {
        if (!requireChunk || status != ChunkStatus.FULL || !DebugCaptureOptimizationGuard.isCaptureActive()) {
            return;
        }
        if (!(chunkSource.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread() || chunkSource.getChunkNow(x, z) != null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(DebugCaptureOptimizationGuard.activePlayerId());
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder builder = new StringBuilder();
        for (int i = 2; i < Math.min(stack.length, 22); i++) {
            builder.append("\n\tat ").append(stack[i]);
        }
        JeiStructures.LOGGER.info(
                "[JEI_DEBUG] main thread sync chunk load chunk=[{}, {}] blockTicking={} playerChunk={}{}",
                x,
                z,
                level.shouldTickBlocksAt(ChunkPos.pack(x, z)),
                player != null ? player.chunkPosition() : "none",
                builder
        );
    }

    public static void logUnreadyChunks(ServerLevel level, List<ChunkPos> chunks, String structureId) {
        ServerChunkCache chunkSource = level.getChunkSource();
        ServerChunkCacheAccessor accessor = (ServerChunkCacheAccessor) chunkSource;
        for (ChunkPos chunkPos : chunks) {
            boolean full = chunkSource.getChunkNow(chunkPos.x(), chunkPos.z()) != null;
            boolean entities = level.areEntitiesLoaded(chunkPos.pack());
            if (full && entities) {
                continue;
            }
            ChunkHolder holder = accessor.jei_structures$getVisibleChunkIfPresent(chunkPos.pack());
            StringBuilder statuses = new StringBuilder();
            String lastStatus = "none";
            int ticketLevel = -1;
            if (holder != null) {
                ticketLevel = holder.getTicketLevel();
                ChunkStatus last = holder.getLatestStatus();
                lastStatus = last != null ? last.toString() : "none";
                for (Pair<ChunkStatus, CompletableFuture<ChunkResult<ChunkAccess>>> pair : holder.getAllFutures()) {
                    CompletableFuture<ChunkResult<ChunkAccess>> future = pair.getSecond();
                    if (future == null) {
                        continue;
                    }
                    String state;
                    if (!future.isDone()) {
                        state = "pending";
                    } else if (future.isCompletedExceptionally()) {
                        state = "exception";
                    } else {
                        ChunkResult<ChunkAccess> result = future.getNow(null);
                        state = result != null && result.isSuccess() ? "ok" : "fail:" + (result != null ? result.getError() : "null");
                    }
                    statuses.append(' ').append(pair.getFirst()).append('=').append(state);
                }
            }
            JeiStructures.LOGGER.info(
                    "[JEI_DEBUG] unready chunk structure={} chunk=[{}, {}] full={} entitiesLoaded={} holder={} ticketLevel={} lastStatus={} futures:{}",
                    structureId,
                    chunkPos.x(),
                    chunkPos.z(),
                    full,
                    entities,
                    holder != null,
                    ticketLevel,
                    lastStatus,
                    statuses
            );
        }
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
                        addLocateTicket(level, chunkPos, ticketLevel);
                    }
                    return accessor.jei_structures$getChunkFutureMainThread(x, z, status, requireChunk);
                }, server)
                .thenCompose(Function.identity());
        if (requireChunk) {
            future.whenComplete((result, throwable) -> server.execute(() -> removeLocateTicket(level, chunkPos, ticketLevel)));
        }
        long jeiDebugWaitStart = System.nanoTime();
        ChunkResult<ChunkAccess> result = awaitLocateFuture(future);
        DebugLocateControl.recordChunkWait(System.nanoTime() - jeiDebugWaitStart);
        ChunkAccess chunk = result.orElse(null);
        if (chunk == null && requireChunk) {
            throw new IllegalStateException("Chunk not there when requested: " + result.getError());
        }
        return chunk;
    }

    private static long locateTicketKey(ChunkPos chunkPos, int ticketLevel) {
        return chunkPos.pack() * 64L + ticketLevel;
    }

    private static void addLocateTicket(ServerLevel level, ChunkPos chunkPos, int ticketLevel) {
        Map<Long, Integer> counts = LOCATE_TICKET_COUNTS.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        if (counts.merge(locateTicketKey(chunkPos, ticketLevel), 1, Integer::sum) == 1) {
            level.getChunkSource().addTicket(new Ticket(LOCATE_TICKET.get(), ticketLevel), chunkPos);
        }
    }

    private static void removeLocateTicket(ServerLevel level, ChunkPos chunkPos, int ticketLevel) {
        Map<Long, Integer> counts = LOCATE_TICKET_COUNTS.get(level.dimension());
        if (counts == null) {
            return;
        }
        long key = locateTicketKey(chunkPos, ticketLevel);
        Integer count = counts.get(key);
        if (count == null) {
            return;
        }
        if (count > 1) {
            counts.put(key, count - 1);
            return;
        }
        counts.remove(key);
        ((ServerChunkCacheAccessor) level.getChunkSource()).jei_structures$getTicketStorage().removeTicket(new Ticket(LOCATE_TICKET.get(), ticketLevel), chunkPos);
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
