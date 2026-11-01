package org.hp.jei_structures.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.hp.jei_structures.debug.DebugCaptureChunkTickets;
import org.hp.jei_structures.debug.DebugCaptureOptimizationGuard;
import org.hp.jei_structures.debug.DebugLocateControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheLocateMixin {

    @Shadow(remap = false)
    @Final
    Thread mainThread;

    @Shadow(remap = false)
    private ChunkHolder getVisibleChunkIfPresent(long chunkPos) {
        throw new AssertionError();
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"), cancellable = true, remap = false)
    private void jei_structures$getChunkForLocate(int x, int z, ChunkStatus status, boolean requireChunk, CallbackInfoReturnable<ChunkAccess> callbackInfo) {
        if (!DebugLocateControl.isLocateThread()) {
            DebugCaptureChunkTickets.logMainThreadSyncLoad((ServerChunkCache) (Object) this, x, z, status, requireChunk);
            return;
        }
        callbackInfo.setReturnValue(DebugCaptureChunkTickets.getChunkForLocate((ServerChunkCache) (Object) this, x, z, status, requireChunk));
    }

    @Inject(method = "hasChunk", at = @At("HEAD"), cancellable = true, remap = false)
    private void jei_structures$hasFullChunkOffThread(int x, int z, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (!DebugCaptureOptimizationGuard.isCaptureActive() || Thread.currentThread() == this.mainThread) {
            return;
        }
        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
        boolean present = holder != null && holder.getChunkIfPresent(ChunkStatus.FULL) != null;
        if (!present) {
            DebugCaptureChunkTickets.logOffThreadHasChunkDenied(x, z);
        }
        callbackInfo.setReturnValue(present);
    }
}
