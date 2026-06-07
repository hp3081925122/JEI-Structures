package org.hp.jei_structures.debug;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class DebugCaptureOptimizationGuard {

    private static UUID activePlayerId;

    private DebugCaptureOptimizationGuard() {
    }

    public static synchronized void enable(UUID playerId) {
        activePlayerId = playerId;
    }

    public static synchronized void disable() {
        activePlayerId = null;
    }

    public static synchronized boolean isSuppressingPlayerSync(ServerPlayer player) {
        return activePlayerId != null && player != null && activePlayerId.equals(player.getUUID());
    }
}
