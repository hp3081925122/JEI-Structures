package org.hp.jei_structures.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.hp.jei_structures.debug.DebugCaptureOptimizationGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class ChunkMapCaptureSyncMixin {

    @Inject(method = "move(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void jei_structures$suppressCapturePlayerSync(ServerPlayer player, CallbackInfo callbackInfo) {
        if (!DebugCaptureOptimizationGuard.isSuppressingPlayerSync(player)) {
            return;
        }
        callbackInfo.cancel();
    }
}
