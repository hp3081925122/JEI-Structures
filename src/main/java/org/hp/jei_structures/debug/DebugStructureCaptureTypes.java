package org.hp.jei_structures.debug;

import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;

public final class DebugStructureCaptureTypes {

    private DebugStructureCaptureTypes() {
    }

    public record StartResult(State state, Path outputRoot, int structureCount, int speedMultiplier, ResourceLocation missingId) {

        public static StartResult started(Path outputRoot, int structureCount, int speedMultiplier) {
            return new StartResult(State.STARTED, outputRoot, structureCount, speedMultiplier, null);
        }

        public static StartResult busy() {
            return new StartResult(State.BUSY, null, 0, 0, null);
        }

        public static StartResult empty() {
            return new StartResult(State.EMPTY, null, 0, 0, null);
        }

        public static StartResult missing(ResourceLocation missingId) {
            return new StartResult(State.MISSING, null, 0, 0, missingId);
        }

        public enum State {
            STARTED,
            BUSY,
            EMPTY,
            MISSING
        }
    }

    public record StopResult(boolean active, Path outputRoot, TimingSnapshot timingSnapshot) {

        public static StopResult idle() {
            return new StopResult(false, null, null);
        }

        public static StopResult active(Path outputRoot) {
            return new StopResult(true, outputRoot, null);
        }

        public static StopResult active(Path outputRoot, TimingSnapshot timingSnapshot) {
            return new StopResult(true, outputRoot, timingSnapshot);
        }
    }

    public record TimingSnapshot(String totalDuration, String cooldownDuration, String locateDuration, String prepareDuration, String placeDuration, String lootDuration, String mobWaitDuration, String mobScanDuration, String clearDuration, String writeDuration, String otherDuration, int slowLocateStructureCount) {
    }

    public record StatusSnapshot(boolean active, String structureId, int structureIndex, int structureCount, int capturedCount, int captureTotalCount, String phase, int attempt, int attemptCount, int speedMultiplier, int loadedChunkCount, int totalChunkCount, String dimensionId, int dimensionIndex, int dimensionCount, int dimensionLocateCompleted, int dimensionLocateTotal, int dimensionLocateSucceeded, int activeLocateCount, int maxLocateCount, String waitingLocateStructureId, long waitingLocateSeconds, int dimensionCaptureCompleted, int dimensionCaptureTotal, Path outputRoot, boolean stopRequested, long elapsedSeconds, long structureElapsedSeconds) {

        public static StatusSnapshot idle() {
            return new StatusSnapshot(false, "", 0, 0, 0, 0, "", 0, 0, 0, 0, 0, "", 0, 0, 0, 0, 0, 0, 0, "", 0L, 0, 0, null, false, 0L, 0L);
        }
    }
}
