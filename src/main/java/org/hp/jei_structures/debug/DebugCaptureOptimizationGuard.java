package org.hp.jei_structures.debug;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class DebugCaptureOptimizationGuard {

    private static UUID activePlayerId;
    private static final AtomicLong SUPPRESSED_PLAYER_SYNC_COUNT = new AtomicLong();

    private DebugCaptureOptimizationGuard() {
    }

    public static synchronized void enable(UUID playerId) {
        activePlayerId = playerId;
        SUPPRESSED_PLAYER_SYNC_COUNT.set(0L);
    }

    public static synchronized void disable() {
        activePlayerId = null;
    }

    public static synchronized boolean isSuppressingPlayerSync(ServerPlayer player) {
        return activePlayerId != null && player != null && activePlayerId.equals(player.getUUID());
    }

    public static void recordSuppressedPlayerSync() {
        SUPPRESSED_PLAYER_SYNC_COUNT.incrementAndGet();
    }

    public static long getSuppressedPlayerSyncCount() {
        return SUPPRESSED_PLAYER_SYNC_COUNT.get();
    }
}
