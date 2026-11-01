package org.hp.jei_structures.mixin;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import org.hp.jei_structures.debug.DebugCaptureOptimizationGuard;
import org.hp.jei_structures.debug.DebugCaptureTickFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerCaptureMixin {

    @Inject(method = "spawnMobsForChunkGeneration", at = @At("HEAD"), cancellable = true)
    private static void jei_structures$skipCaptureChunkGenerationMobs(ServerLevelAccessor level, Holder<Biome> biome, ChunkPos chunkPos, RandomSource random, CallbackInfo callbackInfo) {
        if (DebugCaptureOptimizationGuard.shouldSkipChunkGenerationMobs()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void jei_structures$freezeNaturalSpawning(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState, boolean spawnFriendlies, boolean spawnMonsters, boolean spawnPersistent, CallbackInfo callbackInfo) {
        if (DebugCaptureTickFreeze.isFrozenOnServerThread()) {
            callbackInfo.cancel();
        }
    }
}
