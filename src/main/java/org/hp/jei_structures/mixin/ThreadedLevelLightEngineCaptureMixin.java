package org.hp.jei_structures.mixin;

import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.hp.jei_structures.debug.DebugCaptureOptimizationGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ThreadedLevelLightEngine.class)
public abstract class ThreadedLevelLightEngineCaptureMixin {

    @Inject(method = "lightChunk(Lnet/minecraft/world/level/chunk/ChunkAccess;Z)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), cancellable = true, remap = false)
    private void jei_structures$skipCaptureLighting(ChunkAccess chunk, boolean isLighted, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callbackInfo) {
        if (isLighted || !DebugCaptureOptimizationGuard.shouldSkipLighting()) {
            return;
        }
        chunk.setLightCorrect(false);
        callbackInfo.setReturnValue(CompletableFuture.completedFuture(chunk));
    }
}
