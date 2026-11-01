package org.hp.jei_structures.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkType;
import org.hp.jei_structures.debug.DebugCaptureChunkTickets;
import org.hp.jei_structures.debug.DebugCaptureOptimizationGuard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class ChunkMapCaptureSaveMixin {

    @Shadow(remap = false)
    @Final
    ServerLevel level;

    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void jei_structures$skipCaptureChunkSave(ChunkAccess chunk, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (!DebugCaptureOptimizationGuard.shouldSkipChunkSaving()) {
            return;
        }
        if (chunk.getPersistedStatus().getChunkType() != ChunkType.LEVELCHUNK || DebugCaptureChunkTickets.isCapturedChunk(this.level, chunk.getPos())) {
            callbackInfo.setReturnValue(false);
        }
    }
}
