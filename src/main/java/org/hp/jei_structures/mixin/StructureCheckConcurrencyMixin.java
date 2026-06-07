package org.hp.jei_structures.mixin;

import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.hp.jei_structures.debug.DebugStructureCheckConcurrency;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(StructureCheck.class)
public abstract class StructureCheckConcurrencyMixin implements DebugStructureCheckConcurrency.StructureCheckAccess {

    @Mutable
    @Shadow
    @Final
    private Long2ObjectMap<Object2IntMap<Structure>> loadedChunks;

    @Mutable
    @Shadow
    @Final
    private Map<Structure, Long2BooleanMap> featureChecks;

    @Unique
    private Long2ObjectMap<Object2IntMap<Structure>> jei_structures$originalLoadedChunks;

    @Unique
    private Map<Structure, Long2BooleanMap> jei_structures$originalFeatureChecks;

    @Unique
    private boolean jei_structures$concurrencyEnabled;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void jei_structures$registerStructureCheck(
            ChunkScanAccess storageAccess,
            RegistryAccess registryAccess,
            StructureTemplateManager structureTemplateManager,
            ResourceKey<Level> dimension,
            ChunkGenerator chunkGenerator,
            RandomState randomState,
            LevelHeightAccessor heightAccessor,
            BiomeSource biomeSource,
            long seed,
            DataFixer fixerUpper,
            CallbackInfo callbackInfo
    ) {
        DebugStructureCheckConcurrency.register(this);
    }

    @Override
    public void jei_structures$setStructureCheckConcurrency(boolean enabled) {
        if (enabled == this.jei_structures$concurrencyEnabled) {
            return;
        }
        if (enabled) {
            this.jei_structures$originalLoadedChunks = this.loadedChunks;
            this.jei_structures$originalFeatureChecks = this.featureChecks;
            this.loadedChunks = DebugStructureCheckConcurrency.copyLoadedChunks(this.loadedChunks);
            this.featureChecks = DebugStructureCheckConcurrency.copyFeatureChecks(this.featureChecks);
            this.jei_structures$concurrencyEnabled = true;
            return;
        }
        if (this.jei_structures$originalLoadedChunks != null) {
            this.jei_structures$originalLoadedChunks.clear();
            this.jei_structures$originalLoadedChunks.putAll(this.loadedChunks);
            this.loadedChunks = this.jei_structures$originalLoadedChunks;
        }
        if (this.jei_structures$originalFeatureChecks != null) {
            this.jei_structures$originalFeatureChecks.clear();
            this.jei_structures$originalFeatureChecks.putAll(this.featureChecks);
            this.featureChecks = this.jei_structures$originalFeatureChecks;
        }
        this.jei_structures$originalLoadedChunks = null;
        this.jei_structures$originalFeatureChecks = null;
        this.jei_structures$concurrencyEnabled = false;
    }
}
