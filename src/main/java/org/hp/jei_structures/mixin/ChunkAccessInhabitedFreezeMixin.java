package org.hp.jei_structures.mixin;

import net.minecraft.world.level.chunk.ChunkAccess;
import org.hp.jei_structures.debug.DebugCaptureTickFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkAccess.class)
public abstract class ChunkAccessInhabitedFreezeMixin {

    @Inject(method = "incrementInhabitedTime", at = @At("HEAD"), cancellable = true)
    private void jei_structures$freezeInhabitedTime(long amount, CallbackInfo callbackInfo) {
        if (DebugCaptureTickFreeze.isFrozenOnServerThread()) {
            callbackInfo.cancel();
        }
    }
}
