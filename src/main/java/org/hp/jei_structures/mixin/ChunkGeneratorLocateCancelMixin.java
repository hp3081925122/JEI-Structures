package org.hp.jei_structures.mixin;

import net.minecraft.world.level.chunk.ChunkGenerator;
import org.hp.jei_structures.debug.DebugLocateControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorLocateCancelMixin {

    @Inject(method = "getStructureGeneratingAt", at = @At("HEAD"), remap = false)
    private static void jei_structures$checkLocateCancelled(CallbackInfoReturnable<?> callbackInfo) {
        DebugLocateControl.checkCancelled();
    }
}
