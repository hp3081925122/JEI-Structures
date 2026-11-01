package org.hp.jei_structures.mixin;

import net.minecraft.world.entity.raid.Raids;
import org.hp.jei_structures.debug.DebugCaptureTickFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Raids.class)
public abstract class RaidsFreezeMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void jei_structures$freezeRaids(CallbackInfo callbackInfo) {
        if (DebugCaptureTickFreeze.isFrozenOnServerThread()) {
            callbackInfo.cancel();
        }
    }
}
