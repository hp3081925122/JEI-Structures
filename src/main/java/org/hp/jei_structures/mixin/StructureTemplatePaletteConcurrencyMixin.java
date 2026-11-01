package org.hp.jei_structures.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Mixin(StructureTemplate.Palette.class)
public abstract class StructureTemplatePaletteConcurrencyMixin {

    @Shadow(remap = false)
    @Final
    @Mutable
    private Map<Block, List<StructureTemplate.StructureBlockInfo>> cache;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void jei_structures$useConcurrentCache(List<StructureTemplate.StructureBlockInfo> blocks, CallbackInfo callbackInfo) {
        if (!(this.cache instanceof ConcurrentMap)) {
            this.cache = new ConcurrentHashMap<>(this.cache);
        }
    }
}
