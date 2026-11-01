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

    private static volatile UUID activePlayerId;
    private static volatile boolean skipLighting;
    private static volatile boolean skipChunkSaving;
    private static volatile boolean skipFeatures;
    private static volatile boolean skipChunkGenerationMobs;

    private DebugCaptureOptimizationGuard() {
    }

    public static void enable(UUID playerId) {
        skipLighting = JeiStructuresConfig.captureSkipLighting();
        skipChunkSaving = JeiStructuresConfig.captureSkipChunkSaving();
        skipFeatures = JeiStructuresConfig.captureSkipFeatures();
        skipChunkGenerationMobs = JeiStructuresConfig.captureSkipChunkGenerationMobs();
        activePlayerId = playerId;
    }

    public static void disable() {
        activePlayerId = null;
        skipLighting = false;
        skipChunkSaving = false;
        skipFeatures = false;
        skipChunkGenerationMobs = false;
    }

    public static boolean shouldSkipFeatures() {
        return skipFeatures;
    }

    public static boolean shouldSkipChunkGenerationMobs() {
        return skipChunkGenerationMobs;
    }

    public static boolean shouldSkipLighting() {
        return skipLighting;
    }

    public static boolean shouldSkipChunkSaving() {
        return skipChunkSaving;
    }

    public static boolean isSuppressingPlayerSync(ServerPlayer player) {
        return JeiStructuresConfig.silentCaptureTeleport() && isActiveForPlayer(player);
    }

    public static boolean isActiveForPlayer(ServerPlayer player) {
        UUID playerId = activePlayerId;
        return playerId != null && player != null && playerId.equals(player.getUUID());
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
