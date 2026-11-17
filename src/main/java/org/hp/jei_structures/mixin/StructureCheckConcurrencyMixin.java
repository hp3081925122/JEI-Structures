package org.hp.jei_structures.mixin;

import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(StructureCheck.class)
public abstract class StructureCheckConcurrencyMixin implements DebugStructureCheckConcurrency.StructureCheckAccess {

    @Shadow
    @Final
    private RegistryAccess registryAccess;

    @Shadow
    @Final
    private ChunkGenerator chunkGenerator;

    @Shadow
    @Final
    private BiomeSource biomeSource;

    @Shadow
    @Final
    private RandomState randomState;

    @Shadow
    @Final
    private StructureTemplateManager structureTemplateManager;

    @Shadow
    @Final
    private LevelHeightAccessor heightAccessor;

    @Shadow
    @Final
    private long seed;

    @Mutable
    @Shadow
    @Final
    private Long2ObjectMap<Object2IntMap<Structure>> loadedChunks;

    @Mutable
    @Shadow
    @Final
    private Map<Structure, Long2BooleanMap> featureChecks;

    @Mutable
    @Shadow
    @Final
    private LongSet chunksWithoutStartsInStorage;

    @Unique
    private Long2ObjectMap<Object2IntMap<Structure>> jei_structures$originalLoadedChunks;

    @Unique
    private Map<Structure, Long2BooleanMap> jei_structures$originalFeatureChecks;

    @Unique
    private LongSet jei_structures$originalChunksWithoutStarts;

    @Unique
    private ThreadLocal<Climate.Sampler> jei_structures$threadLocalClimateSampler;

    @Unique
    private volatile boolean jei_structures$concurrencyEnabled;

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

    @Inject(method = "canCreateStructure", at = @At("HEAD"), cancellable = true)
    private void jei_structures$canCreateStructureConcurrent(ChunkPos pos, Structure structure, CallbackInfoReturnable<Boolean> cir) {
        ThreadLocal<Climate.Sampler> samplerHolder = this.jei_structures$threadLocalClimateSampler;
        if (!this.jei_structures$concurrencyEnabled || samplerHolder == null) {
            return;
        }
        Climate.Sampler threadSampler = samplerHolder.get();
        boolean result = structure.findValidGenerationPoint(
                new Structure.GenerationContext(
                        this.registryAccess,
                        this.chunkGenerator,
                        this.biomeSource,
                        threadSampler,
                        this.randomState,
                        this.structureTemplateManager,
                        this.seed,
                        pos,
                        this.heightAccessor,
                        structure.biomes()::contains
                )
        ).isPresent();
        cir.setReturnValue(result);
    }

    @Override
    public void jei_structures$setStructureCheckConcurrency(boolean enabled) {
        if (enabled == this.jei_structures$concurrencyEnabled) {
            return;
        }
        if (enabled) {
            this.jei_structures$originalLoadedChunks = this.loadedChunks;
            this.jei_structures$originalFeatureChecks = this.featureChecks;
            this.jei_structures$originalChunksWithoutStarts = this.chunksWithoutStartsInStorage;
            this.loadedChunks = DebugStructureCheckConcurrency.copyLoadedChunks(this.loadedChunks);
            this.featureChecks = DebugStructureCheckConcurrency.copyFeatureChecks(this.featureChecks);
            this.chunksWithoutStartsInStorage = DebugStructureCheckConcurrency.copyChunksWithoutStarts(this.chunksWithoutStartsInStorage);
            this.jei_structures$threadLocalClimateSampler = ThreadLocal.withInitial(
                    () -> this.randomState.createClimateSampler(SamplerContext.builder().enableCaches().build())
            );
            this.jei_structures$concurrencyEnabled = true;
            return;
        }
        this.jei_structures$concurrencyEnabled = false;
        this.jei_structures$threadLocalClimateSampler = null;
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
        if (this.jei_structures$originalChunksWithoutStarts != null) {
            this.jei_structures$originalChunksWithoutStarts.clear();
            this.jei_structures$originalChunksWithoutStarts.addAll(this.chunksWithoutStartsInStorage);
            this.chunksWithoutStartsInStorage = this.jei_structures$originalChunksWithoutStarts;
        }
        this.jei_structures$originalLoadedChunks = null;
        this.jei_structures$originalFeatureChecks = null;
        this.jei_structures$originalChunksWithoutStarts = null;
    }
}
