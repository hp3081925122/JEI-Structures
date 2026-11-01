package org.hp.jei_structures.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.hp.jei_structures.debug.DebugCaptureOptimizationGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlacedFeature.class)
public abstract class PlacedFeatureCaptureSkipMixin {

    @Inject(method = "placeWithBiomeCheck", at = @At("HEAD"), cancellable = true)
    private void jei_structures$skipCaptureFeature(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (DebugCaptureOptimizationGuard.shouldSkipDecorationFeature()) {
            callbackInfo.setReturnValue(false);
        }
    }
}
