package org.hp.jei_structures.debug;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public final class DebugStructureCaptureWorld {

    private DebugStructureCaptureWorld() {
    }

    public static ChunkCoverage collectStructureChunkCoverage(StructureStart structureStart, BoundingBox fallbackBox) {
        LinkedHashSet<ChunkPos> chunkSet = new LinkedHashSet<>();
        ChunkBounds bounds = new ChunkBounds();
        if (structureStart != null) {
            for (StructurePiece piece : structureStart.getPieces()) {
                if (piece == null || piece.getBoundingBox() == null) {
                    continue;
                }
                addBoundingBoxChunks(piece.getBoundingBox(), chunkSet, bounds);
            }
        }
        if (chunkSet.isEmpty() && fallbackBox != null) {
            addBoundingBoxChunks(fallbackBox, chunkSet, bounds);
        }
        List<ChunkPos> chunks = new ArrayList<>(chunkSet);
        chunks.sort(Comparator.comparingInt((ChunkPos chunkPos) -> chunkPos.x).thenComparingInt(chunkPos -> chunkPos.z));
        if (!bounds.valid()) {
            ChunkPos zero = new ChunkPos(0, 0);
            return new ChunkCoverage(List.of(), zero, 2);
        }
        ChunkPos ticketCenter = new ChunkPos((bounds.minChunkX + bounds.maxChunkX) >> 1, (bounds.minChunkZ + bounds.maxChunkZ) >> 1);
        int ticketRadius = Math.max(Math.max(bounds.maxChunkX - bounds.minChunkX, bounds.maxChunkZ - bounds.minChunkZ) / 2 + 2, 2);
        return new ChunkCoverage(List.copyOf(chunks), ticketCenter, ticketRadius);
    }

    public static ChunkCoverage buildChunkCoverageFromChunks(List<ChunkPos> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            ChunkPos zero = new ChunkPos(0, 0);
            return new ChunkCoverage(List.of(), zero, 2);
        }
        LinkedHashSet<ChunkPos> chunkSet = new LinkedHashSet<>();
        for (ChunkPos chunkPos : chunks) {
            if (chunkPos != null) {
                chunkSet.add(chunkPos);
            }
        }
        List<ChunkPos> orderedChunks = new ArrayList<>(chunkSet);
        orderedChunks.sort(Comparator.comparingInt((ChunkPos chunkPos) -> chunkPos.x).thenComparingInt(chunkPos -> chunkPos.z));
        ChunkBounds bounds = new ChunkBounds();
        for (ChunkPos chunkPos : orderedChunks) {
            bounds.include(chunkPos.x, chunkPos.z);
        }
        if (!bounds.valid()) {
            ChunkPos zero = new ChunkPos(0, 0);
            return new ChunkCoverage(List.of(), zero, 2);
        }
        ChunkPos ticketCenter = new ChunkPos((bounds.minChunkX + bounds.maxChunkX) >> 1, (bounds.minChunkZ + bounds.maxChunkZ) >> 1);
        int ticketRadius = Math.max(Math.max(bounds.maxChunkX - bounds.minChunkX, bounds.maxChunkZ - bounds.minChunkZ) / 2 + 2, 2);
        return new ChunkCoverage(orderedChunks, ticketCenter, ticketRadius);
    }

    private static void addBoundingBoxChunks(BoundingBox box, LinkedHashSet<ChunkPos> chunkSet, ChunkBounds bounds) {
        int minChunkX = box.minX() >> 4;
        int maxChunkX = box.maxX() >> 4;
        int minChunkZ = box.minZ() >> 4;
        int maxChunkZ = box.maxZ() >> 4;
        bounds.include(minChunkX, maxChunkX, minChunkZ, maxChunkZ);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunkSet.add(new ChunkPos(chunkX, chunkZ));
            }
        }
    }

    public record ChunkCoverage(List<ChunkPos> chunks, ChunkPos ticketCenter, int ticketRadius) {
    }

    private static final class ChunkBounds {
        private int minChunkX = Integer.MAX_VALUE;
        private int maxChunkX = Integer.MIN_VALUE;
        private int minChunkZ = Integer.MAX_VALUE;
        private int maxChunkZ = Integer.MIN_VALUE;

        private void include(int chunkX, int chunkZ) {
            minChunkX = Math.min(minChunkX, chunkX);
            maxChunkX = Math.max(maxChunkX, chunkX);
            minChunkZ = Math.min(minChunkZ, chunkZ);
            maxChunkZ = Math.max(maxChunkZ, chunkZ);
        }

        private void include(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
            this.minChunkX = Math.min(this.minChunkX, minChunkX);
            this.maxChunkX = Math.max(this.maxChunkX, maxChunkX);
            this.minChunkZ = Math.min(this.minChunkZ, minChunkZ);
            this.maxChunkZ = Math.max(this.maxChunkZ, maxChunkZ);
        }

        private boolean valid() {
            return minChunkX <= maxChunkX && minChunkZ <= maxChunkZ;
        }
    }
}
