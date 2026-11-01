package org.hp.jei_structures.debug;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class DebugLocateControl {

    private static final ThreadLocal<LocateToken> CURRENT = new ThreadLocal<>();
    private static final AtomicInteger RUNNING = new AtomicInteger();

    private DebugLocateControl() {
    }

    public static void begin(LocateToken token) {
        token.runStartMillis = System.currentTimeMillis();
        CURRENT.set(token);
        RUNNING.incrementAndGet();
    }

    public static void end() {
        LocateToken token = CURRENT.get();
        if (token != null) {
            token.runEndMillis = System.currentTimeMillis();
        }
        CURRENT.remove();
        RUNNING.decrementAndGet();
    }

    public static boolean isLocateThread() {
        return CURRENT.get() != null;
    }

    public static boolean isCancelled() {
        LocateToken token = CURRENT.get();
        return token != null && token.cancelled;
    }

    public static void checkCancelled() {
        if (isCancelled()) {
            throw new LocateCancelledException();
        }
    }

    public static void recordSimulatedStart(StructureStart start) {
        LocateToken token = CURRENT.get();
        if (token != null) {
            token.simulatedStarts.put(start.getChunkPos().pack(), start);
        }
    }

    public static void recordResolvedStart(long chunkKey, StructureStart start) {
        LocateToken token = CURRENT.get();
        if (token != null) {
            token.simulatedStarts.put(chunkKey, start);
        }
    }

    public static void recordCandidate() {
        LocateToken token = CURRENT.get();
        if (token != null) {
            token.candidates++;
        }
    }

    public static void recordChunkWait(long nanos) {
        LocateToken token = CURRENT.get();
        if (token != null) {
            token.chunkWaitNanos += nanos;
            token.chunkLoads++;
        }
    }

    public static int runningCount() {
        return RUNNING.get();
    }

    public static final class LocateToken {
        private volatile boolean cancelled;
        private volatile long runStartMillis;
        private volatile long runEndMillis;
        private volatile int candidates;
        private volatile int chunkLoads;
        private volatile long chunkWaitNanos;
        private final Map<Long, StructureStart> simulatedStarts = new ConcurrentHashMap<>();

        public void cancel() {
            cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public StructureStart simulatedStart(ChunkPos chunkPos) {
            return simulatedStarts.get(chunkPos.pack());
        }

        public long runStartMillis() {
            return runStartMillis;
        }

        public String debugSummary(long submitMillis) {
            long end = runEndMillis > 0L ? runEndMillis : System.currentTimeMillis();
            return "queueMs=" + (runStartMillis > 0L ? runStartMillis - submitMillis : -1L)
                    + " runMs=" + (runStartMillis > 0L ? end - runStartMillis : -1L)
                    + " candidates=" + candidates
                    + " chunkLoads=" + chunkLoads
                    + " chunkWaitMs=" + chunkWaitNanos / 1_000_000L;
        }
    }

    public static final class LocateCancelledException extends RuntimeException {
        private LocateCancelledException() {
            super("Structure debug locate cancelled", null, false, false);
        }
    }
}
