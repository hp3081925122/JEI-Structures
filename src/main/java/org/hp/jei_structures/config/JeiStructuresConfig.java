package org.hp.jei_structures.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class JeiStructuresConfig {

    public static final ModConfigSpec COMMON_SPEC;
    private static final ModConfigSpec.BooleanValue DEBUG_RENDER_TIMING;
    private static final ModConfigSpec.BooleanValue SILENT_CAPTURE_TELEPORT;
    private static final ModConfigSpec.IntValue CAPTURE_CHUNK_LOAD_TIMEOUT_SECONDS;
    private static final ModConfigSpec.IntValue CAPTURE_MOB_WAIT_TICKS;
    private static final ModConfigSpec.IntValue CAPTURE_PREFETCH_STRUCTURES;
    private static final ModConfigSpec.IntValue CAPTURE_LOCATE_THREADS;
    private static final ModConfigSpec.IntValue CAPTURE_LOCATE_TIMEOUT_SECONDS;
    private static final ModConfigSpec.BooleanValue CAPTURE_SKIP_LIGHTING;
    private static final ModConfigSpec.BooleanValue CAPTURE_SKIP_CHUNK_SAVING;
    private static final ModConfigSpec.BooleanValue CAPTURE_SKIP_FEATURES;
    private static final ModConfigSpec.BooleanValue CAPTURE_SKIP_CHUNK_GENERATION_MOBS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("debugCapture");
        SILENT_CAPTURE_TELEPORT = builder
                .comment("Suppress teleport, chunk, and entity sync packets for the capture player during debug structure capture.")
                .define("silentCaptureTeleport", true);
        CAPTURE_CHUNK_LOAD_TIMEOUT_SECONDS = builder
                .comment(
                        "Seconds to wait for structure chunks. On the first timeout the structure is moved to the end of the queue while its chunks keep loading; on the second timeout it is skipped and recorded as failed.",
                        "等待结构区块加载的最长秒数。第一次超时会把该结构移到队尾，区块继续在后台加载；第二次超时则跳过并记为失败。"
                )
                .defineInRange("chunkLoadTimeoutSeconds", 20, 5, 3600);
        CAPTURE_MOB_WAIT_TICKS = builder
                .comment(
                        "Ticks to wait before sampling mobs after each structure is prepared.",
                        "每个结构准备完成后，采样生物前等待的 tick 数。"
                )
                .defineInRange("mobWaitTicks", 2, 0, 200);
        CAPTURE_PREFETCH_STRUCTURES = builder
                .comment(
                        "Number of located structures whose chunks are loaded in advance while the current structure is captured.",
                        "采集当前结构时，提前加载后续多少个已定位结构的区块。"
                )
                .defineInRange("prefetchStructures", 8, 0, 32);
        CAPTURE_LOCATE_THREADS = builder
                .comment(
                        "Number of structures located concurrently.",
                        "同时定位的结构数量。"
                )
                .defineInRange("locateThreads", Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors() / 2)), 1, 32);
        CAPTURE_LOCATE_TIMEOUT_SECONDS = builder
                .comment(
                        "Seconds a single structure locate may run before it is cancelled.",
                        "单个结构定位最长运行秒数，超时后取消。"
                )
                .defineInRange("locateTimeoutSeconds", 30, 5, 600);
        CAPTURE_SKIP_LIGHTING = builder
                .comment(
                        "Skip light propagation for chunks generated during capture. Chunks are saved as unlit and relit when loaded again.",
                        "采集期间跳过新生成区块的光照计算。区块会以未计算光照的状态保存，下次加载时自动重新计算。"
                )
                .define("skipLighting", true);
        CAPTURE_SKIP_CHUNK_SAVING = builder
                .comment(
                        "Do not save proto chunks or captured structure chunks during capture. They will be regenerated later. Only use this in a throwaway capture world.",
                        "采集期间不保存未完成生成的区块和被采集的结构区块，之后会重新生成。仅用于一次性采集存档。"
                )
                .define("skipChunkSaving", true);
        CAPTURE_SKIP_FEATURES = builder
                .comment(
                        "Skip biome features such as trees, ores and lakes for chunks generated during capture. Structure pieces are still placed. These chunks are never saved until the server restarts.",
                        "采集期间跳过新生成区块的普通地物（树、矿石、湖等），结构本身照常放置。这些区块在服务端重启前都不会保存。"
                )
                .define("skipFeatures", true);
        CAPTURE_SKIP_CHUNK_GENERATION_MOBS = builder
                .comment(
                        "Skip biome animals spawned during chunk generation while capturing.",
                        "采集期间跳过区块生成时按群系刷出的动物。"
                )
                .define("skipChunkGenerationMobs", true);
        builder.pop();
        builder.push("debugRender");
        DEBUG_RENDER_TIMING = builder
                .comment(
                        "Log render timing of the structure index page to the game log.",
                        "在游戏日志中记录结构索引页面的渲染耗时。"
                )
                .define("debugRenderTiming", false);
        builder.pop();
        COMMON_SPEC = builder.build();
    }

    private JeiStructuresConfig() {
    }

    public static boolean silentCaptureTeleport() {
        return SILENT_CAPTURE_TELEPORT.get();
    }

    public static int captureChunkLoadTimeoutSeconds() {
        return CAPTURE_CHUNK_LOAD_TIMEOUT_SECONDS.get();
    }

    public static int captureMobWaitTicks() {
        return CAPTURE_MOB_WAIT_TICKS.get();
    }

    public static int capturePrefetchStructures() {
        return CAPTURE_PREFETCH_STRUCTURES.get();
    }

    public static int captureLocateThreads() {
        return CAPTURE_LOCATE_THREADS.get();
    }

    public static int captureLocateTimeoutSeconds() {
        return CAPTURE_LOCATE_TIMEOUT_SECONDS.get();
    }

    public static boolean captureSkipLighting() {
        return CAPTURE_SKIP_LIGHTING.get();
    }

    public static boolean captureSkipChunkSaving() {
        return CAPTURE_SKIP_CHUNK_SAVING.get();
    }

    public static boolean captureSkipFeatures() {
        return CAPTURE_SKIP_FEATURES.get();
    }

    public static boolean captureSkipChunkGenerationMobs() {
        return CAPTURE_SKIP_CHUNK_GENERATION_MOBS.get();
    }

    public static boolean debugRenderTiming() {
        return COMMON_SPEC.isLoaded() && DEBUG_RENDER_TIMING.get();
    }
}
