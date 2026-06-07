package org.hp.jei_structures.debug;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class DebugStructureCaptureCommon {

    public static final int DEFAULT_SPEED_MULTIPLIER = 1;
    public static final int MAX_SPEED_MULTIPLIER = 32;

    private DebugStructureCaptureCommon() {
    }

    public static int normalizeSpeedMultiplier(int speedMultiplier) {
        return Math.max(DEFAULT_SPEED_MULTIPLIER, Math.min(MAX_SPEED_MULTIPLIER, speedMultiplier));
    }

    public static int scaleWaitTicks(int rawTicks, int speedMultiplier) {
        if (rawTicks <= 0) {
            return 0;
        }
        return Math.max((rawTicks + Math.max(speedMultiplier, 1) - 1) / Math.max(speedMultiplier, 1), 1);
    }

    public static int scaleWorkPerTick(int baseAmount, int speedMultiplier) {
        return Math.max(baseAmount * Math.max(speedMultiplier, 1), 1);
    }

    public static Component getStructureDisplayComponent(String structureId) {
        return DebugStructureCaptureSupport.getStructureDisplayComponent(structureId);
    }

    public static Component getStructureDisplayComponent(ResourceLocation structureId) {
        return DebugStructureCaptureSupport.getStructureDisplayComponent(structureId);
    }
}
