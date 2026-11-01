package org.hp.jei_structures.mixin;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.hp.jei_structures.debug.DebugLocateControl;
import org.hp.jei_structures.debug.DebugLocateStructureSimulator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorLocateCancelMixin {

    @Inject(method = "getStructureGeneratingAt", at = @At("HEAD"), cancellable = true)
    private static void jei_structures$checkLocateCancelled(Set<Holder<Structure>> structureHoldersSet, LevelReader level, StructureManager structureManager, boolean skipKnownStructures, StructurePlacement placement, ChunkPos chunkPos, CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> callbackInfo) {
        DebugLocateControl.checkCancelled();
        DebugLocateControl.recordCandidate();
        if (!DebugLocateControl.isLocateThread() || skipKnownStructures || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (Holder<Structure> holder : structureHoldersSet) {
            StructureCheckResult checkResult = structureManager.checkStructurePresence(chunkPos, holder.value(), false);
            if (checkResult == StructureCheckResult.START_NOT_PRESENT) {
                continue;
            }
            if (checkResult == StructureCheckResult.START_PRESENT) {
                callbackInfo.setReturnValue(Pair.of(placement.getLocatePos(chunkPos), holder));
                return;
            }
            StructureStart start = DebugLocateStructureSimulator.simulateStart(serverLevel, placement, chunkPos, holder);
            if (start == null) {
                return;
            }
            if (start.isValid()) {
                DebugLocateControl.recordSimulatedStart(start);
                callbackInfo.setReturnValue(Pair.of(placement.getLocatePos(start.getChunkPos()), holder));
                return;
            }
        }
        callbackInfo.setReturnValue(null);
    }
}
