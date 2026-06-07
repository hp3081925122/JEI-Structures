package org.hp.jei_structures.mixin;

import brightspark.asynclocator.AsyncLocatorConfigForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = AsyncLocatorConfigForge.class, remap = false)
public abstract class AsyncLocatorConfigDefaultMixin {

    @ModifyArg(
            method = "lambda$static$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/common/ForgeConfigSpec$Builder;defineInRange(Ljava/lang/String;III)Lnet/minecraftforge/common/ForgeConfigSpec$IntValue;"
            ),
            index = 1,
            require = 1
    )
    private static int jei_structures$defaultLocatorThreads(int defaultValue) {
        return Math.max(defaultValue, 3);
    }
}
