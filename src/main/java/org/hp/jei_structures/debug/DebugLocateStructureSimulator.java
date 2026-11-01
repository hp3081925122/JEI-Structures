package org.hp.jei_structures.debug;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.List;

public final class DebugLocateStructureSimulator {

    private DebugLocateStructureSimulator() {
    }

    public static StructureStart simulateStart(ServerLevel level, StructurePlacement placement, ChunkPos chunkPos, Holder<Structure> target) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        ChunkGeneratorStructureState structureState = level.getChunkSource().getGeneratorState();
        for (Holder<StructureSet> setHolder : structureState.possibleStructureSets()) {
            StructureSet structureSet = setHolder.value();
            if (structureSet.placement() != placement || !containsStructure(structureSet, target.value())) {
                continue;
            }
            if (!placement.isStructureChunk(structureState, chunkPos.x, chunkPos.z)) {
                return StructureStart.INVALID_START;
            }
            List<StructureSet.StructureSelectionEntry> entries = structureSet.structures();
            if (entries.size() == 1) {
                return generate(level, generator, structureState, entries.get(0), chunkPos);
            }
            ArrayList<StructureSet.StructureSelectionEntry> remaining = new ArrayList<>(entries);
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(structureState.getLevelSeed(), chunkPos.x, chunkPos.z);
            int totalWeight = 0;
            for (StructureSet.StructureSelectionEntry entry : remaining) {
                totalWeight += entry.weight();
            }
            while (!remaining.isEmpty()) {
                int roll = random.nextInt(totalWeight);
                int index = 0;
                for (StructureSet.StructureSelectionEntry entry : remaining) {
                    roll -= entry.weight();
                    if (roll < 0) {
                        break;
                    }
                    index++;
                }
                StructureSet.StructureSelectionEntry picked = remaining.get(index);
                StructureStart start = generate(level, generator, structureState, picked, chunkPos);
                if (start.isValid()) {
                    return picked.structure().value() == target.value() ? start : StructureStart.INVALID_START;
                }
                remaining.remove(index);
                totalWeight -= picked.weight();
            }
            return StructureStart.INVALID_START;
        }
        return null;
    }

    private static boolean containsStructure(StructureSet structureSet, Structure structure) {
        for (StructureSet.StructureSelectionEntry entry : structureSet.structures()) {
            if (entry.structure().value() == structure) {
                return true;
            }
        }
        return false;
    }

    private static StructureStart generate(ServerLevel level, ChunkGenerator generator, ChunkGeneratorStructureState structureState, StructureSet.StructureSelectionEntry entry, ChunkPos chunkPos) {
        Structure structure = entry.structure().value();
        return structure.generate(
                level.registryAccess(),
                generator,
                generator.getBiomeSource(),
                structureState.randomState(),
                level.getStructureManager(),
                structureState.getLevelSeed(),
                chunkPos,
                0,
                level,
                structure.biomes()::contains
        );
    }
}
