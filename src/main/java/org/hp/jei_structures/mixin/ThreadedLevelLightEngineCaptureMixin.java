package org.hp.jei_structures.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.hp.jei_structures.debug.DebugCaptureOptimizationGuard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ThreadedLevelLightEngine.class)
public abstract class ThreadedLevelLightEngineCaptureMixin {

    @Shadow
    @Final
    private ChunkMap chunkMap;

    @Inject(method = "lightChunk", at = @At("HEAD"), cancellable = true)
    private void jei_structures$skipCaptureLighting(ChunkAccess chunk, boolean isLighted, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callbackInfo) {
        if (isLighted || !DebugCaptureOptimizationGuard.shouldSkipLighting()) {
            return;
        }
        chunk.setLightCorrect(false);
        ((ChunkMapAccessor) this.chunkMap).jei_structures$releaseLightTicket(chunk.getPos());
        callbackInfo.setReturnValue(CompletableFuture.completedFuture(chunk));
    }
}
