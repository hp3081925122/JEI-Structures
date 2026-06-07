package org.hp.jei_structures.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class JeiStructuresConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    private static final ForgeConfigSpec.BooleanValue SILENT_CAPTURE_TELEPORT;
    private static final ForgeConfigSpec.IntValue CAPTURE_CHUNK_LOADS_PER_TICK;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("debugCapture");
        SILENT_CAPTURE_TELEPORT = builder
                .comment("调试采集结构时屏蔽采集玩家的传送、区块和实体客户端同步包，只保留服务端采集逻辑。")
                .define("silentCaptureTeleport", true);
        CAPTURE_CHUNK_LOADS_PER_TICK = builder
                .comment("调试采集结构时每 tick 主动加载的结构区块数量。")
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
