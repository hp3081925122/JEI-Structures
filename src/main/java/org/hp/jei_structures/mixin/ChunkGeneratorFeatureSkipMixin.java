package org.hp.jei_structures.mixin;

import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.hp.jei_structures.debug.DebugCaptureChunkTickets;
import org.hp.jei_structures.debug.DebugCaptureOptimizationGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorFeatureSkipMixin {

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void jei_structures$markFeatureSkippedChunk(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, CallbackInfo callbackInfo) {
        if (DebugCaptureOptimizationGuard.shouldSkipFeatures()) {
            DebugCaptureChunkTickets.markFeatureSkippedChunk(level.getLevel(), chunk.getPos());
            DebugCaptureOptimizationGuard.beginFeatureDecoration();
        }
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void jei_structures$endFeatureDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, CallbackInfo callbackInfo) {
        DebugCaptureOptimizationGuard.endFeatureDecoration();
    }
}
