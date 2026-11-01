package org.hp.jei_structures.mixin;

import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.hp.jei_structures.debug.DebugCaptureTickFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndDragonFight.class)
public abstract class EndDragonFightFreezeMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void jei_structures$freezeDragonFight(CallbackInfo callbackInfo) {
        if (DebugCaptureTickFreeze.isFrozenOnServerThread()) {
            callbackInfo.cancel();
        }
    }
}
