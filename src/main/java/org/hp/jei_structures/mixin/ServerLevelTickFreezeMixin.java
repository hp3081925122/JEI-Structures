package org.hp.jei_structures.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.hp.jei_structures.debug.DebugCaptureTickFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelTickFreezeMixin {

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void jei_structures$freezeWeather(CallbackInfo callbackInfo) {
        if (DebugCaptureTickFreeze.isFrozenOnServerThread()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void jei_structures$freezeTime(CallbackInfo callbackInfo) {
        if (DebugCaptureTickFreeze.isFrozenOnServerThread()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "runBlockEvents", at = @At("HEAD"), cancellable = true)
    private void jei_structures$freezeBlockEvents(CallbackInfo callbackInfo) {
        if (DebugCaptureTickFreeze.isFrozenOnServerThread()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void jei_structures$freezeEntity(Entity entity, CallbackInfo callbackInfo) {
        if (DebugCaptureTickFreeze.isEntityFrozen(entity)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void jei_structures$freezeRandomTicks(LevelChunk chunk, int randomTickSpeed, CallbackInfo callbackInfo) {
        if (DebugCaptureTickFreeze.isFrozenOnServerThread()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "tickCustomSpawners", at = @At("HEAD"), cancellable = true)
    private void jei_structures$freezeCustomSpawners(boolean spawnEnemies, boolean spawnFriendlies, CallbackInfo callbackInfo) {
        if (DebugCaptureTickFreeze.isFrozenOnServerThread()) {
            callbackInfo.cancel();
        }
    }
}
