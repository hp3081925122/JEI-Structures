package org.hp.jei_structures.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.LevelTicks;
import org.hp.jei_structures.debug.DebugCaptureTickFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

@Mixin(LevelTicks.class)
public abstract class LevelTicksFreezeMixin<T> {

    @Inject(method = "tick(JILjava/util/function/BiConsumer;)V", at = @At("HEAD"), cancellable = true)
    private void jei_structures$freezeScheduledTicks(long gameTime, int maxTicks, BiConsumer<BlockPos, T> ticker, CallbackInfo callbackInfo) {
        if (DebugCaptureTickFreeze.isFrozenOnServerThread()) {
            callbackInfo.cancel();
        }
    }
}
