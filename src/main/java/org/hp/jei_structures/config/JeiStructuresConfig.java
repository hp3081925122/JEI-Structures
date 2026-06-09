package org.hp.jei_structures.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class JeiStructuresConfig {

    public static final ModConfigSpec COMMON_SPEC;
    private static final ModConfigSpec.BooleanValue SILENT_CAPTURE_TELEPORT;
    private static final ModConfigSpec.IntValue CAPTURE_CHUNK_LOADS_PER_TICK;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("debugCapture");
        SILENT_CAPTURE_TELEPORT = builder
                .comment("Suppress teleport, chunk, and entity sync packets for the capture player during debug structure capture.")
                .define("silentCaptureTeleport", true);
        CAPTURE_CHUNK_LOADS_PER_TICK = builder
                .comment("Number of structure chunks to load per tick during debug structure capture.")
                .defineInRange("chunkLoadsPerTick", 8, 1, 64);
        builder.pop();
        COMMON_SPEC = builder.build();
    }

    private JeiStructuresConfig() {
    }

    public static boolean silentCaptureTeleport() {
        return SILENT_CAPTURE_TELEPORT.get();
    }

    public static int captureChunkLoadsPerTick() {
        return CAPTURE_CHUNK_LOADS_PER_TICK.get();
    }
}
