package org.hp.jei_structures.mixin;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.hp.jei_structures.debug.DebugCaptureOptimizationGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksSpawnSkipMixin {

    @Inject(method = "generateSpawn", at = @At("HEAD"), cancellable = true, remap = false)
    private static void jei_structures$skipCaptureChunkGenerationMobs(WorldGenContext worldGenContext, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callbackInfo) {
        if (DebugCaptureOptimizationGuard.shouldSkipChunkGenerationMobs()) {
            callbackInfo.setReturnValue(CompletableFuture.completedFuture(chunk));
        }
    }
}
