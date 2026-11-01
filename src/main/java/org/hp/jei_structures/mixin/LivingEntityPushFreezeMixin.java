package org.hp.jei_structures.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.hp.jei_structures.debug.DebugCaptureTickFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityPushFreezeMixin {

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void jei_structures$freezePlayerPush(CallbackInfo callbackInfo) {
        if ((Object) this instanceof ServerPlayer && DebugCaptureTickFreeze.isFrozenOnServerThread()) {
            callbackInfo.cancel();
        }
    }
}
