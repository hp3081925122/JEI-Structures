package org.hp.jei_structures.mixin;

import net.minecraft.world.level.border.WorldBorder;
import org.hp.jei_structures.debug.DebugCaptureTickFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldBorder.class)
public abstract class WorldBorderFreezeMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void jei_structures$freezeWorldBorder(CallbackInfo callbackInfo) {
        if (DebugCaptureTickFreeze.isFrozenOnServerThread()) {
            callbackInfo.cancel();
        }
    }
}
