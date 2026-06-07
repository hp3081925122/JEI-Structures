package org.hp.jei_structures.debug;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.hp.jei_structures.config.JeiStructuresConfig;

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
        return JeiStructuresConfig.silentCaptureTeleport() && isActiveForPlayer(player);
    }

    public static synchronized boolean isActiveForPlayer(ServerPlayer player) {
        return activePlayerId != null && player != null && activePlayerId.equals(player.getUUID());
    }

    public static boolean shouldSuppressClientboundPacket(ServerPlayer player, Packet<?> packet) {
        if (!isSuppressingPlayerSync(player) || packet == null) {
            return false;
        }
        return packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundRespawnPacket
                || packet instanceof ClientboundLevelChunkWithLightPacket
                || packet instanceof ClientboundForgetLevelChunkPacket
                || packet instanceof ClientboundSetChunkCacheCenterPacket
                || packet instanceof ClientboundSetChunkCacheRadiusPacket
                || packet instanceof ClientboundAddEntityPacket
                || packet instanceof ClientboundRemoveEntitiesPacket
                || packet instanceof ClientboundMoveEntityPacket
                || packet instanceof ClientboundTeleportEntityPacket
                || packet instanceof ClientboundRotateHeadPacket
                || packet instanceof ClientboundSetEntityDataPacket
                || packet instanceof ClientboundSetEntityMotionPacket
                || packet instanceof ClientboundBlockUpdatePacket
                || packet instanceof ClientboundSectionBlocksUpdatePacket
                || packet instanceof ClientboundBlockEntityDataPacket
                || packet instanceof ClientboundBundlePacket;
    }
}
