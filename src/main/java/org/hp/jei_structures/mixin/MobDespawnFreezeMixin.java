package org.hp.jei_structures.mixin;

import net.minecraft.world.entity.Mob;
import org.hp.jei_structures.debug.DebugCaptureTickFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobDespawnFreezeMixin {

    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void jei_structures$freezeDespawn(CallbackInfo callbackInfo) {
        if (DebugCaptureTickFreeze.isEntityFrozen((Mob) (Object) this)) {
            callbackInfo.cancel();
        }
    }
}
