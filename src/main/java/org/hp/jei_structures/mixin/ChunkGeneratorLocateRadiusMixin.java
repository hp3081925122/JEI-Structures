package org.hp.jei_structures.mixin;

import net.minecraft.world.level.chunk.ChunkGenerator;
import org.hp.jei_structures.debug.DebugLocateRadiusLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorLocateRadiusMixin {

    @ModifyVariable(
            method = "findNearestMapStructure",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int jei_structures$clampLocateRadius(int radius) {
        return DebugLocateRadiusLimiter.clamp(radius);
    }
}
