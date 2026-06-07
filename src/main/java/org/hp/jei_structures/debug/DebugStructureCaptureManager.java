package org.hp.jei_structures.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import brightspark.asynclocator.AsyncLocator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.registries.ForgeRegistries;
import org.hp.jei_structures.JeiStructures;
import org.hp.jei_structures.data.LootTableItemResolver;
import org.hp.jei_structures.data.StoredItemNbtReader;
import org.hp.jei_structures.data.StructureBindingPaths;
import org.hp.jei_structures.data.StructureIndexCache;
import org.hp.jei_structures.config.JeiStructuresConfig;
import org.hp.jei_structures.debug.DebugStructureCaptureTargets.StructureTarget;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DebugStructureCaptureManager {

    private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final int DEFAULT_LOCATE_RADIUS = 128;
    private static final int END_OUTER_ISLAND_OFFSET_X = 1200;
    private static final int BASE_COOLDOWN_TICKS = 5;
    private static final int BASE_MOB_WAIT_TICKS = 20;
    private static final int MAX_CONCURRENT_LOCATE_REQUESTS = 3;
    private static final long LOCATE_REQUEST_TIMEOUT_MILLIS = 20_000L;
    private static final long CHUNK_LOAD_SLOW_WARN_MILLIS = 500L;
    private static final long CHUNK_LOAD_BATCH_SLOW_WARN_MILLIS = 1_500L;

    private static Session activeSession;

    private DebugStructureCaptureManager() {
    }

    public static synchronized DebugStructureCaptureTypes.StartResult startAll(ServerPlayer player) throws Exception {
        return startAll(player, DebugStructureCaptureCommon.DEFAULT_SPEED_MULTIPLIER, null);
    }

    public static synchronized DebugStructureCaptureTypes.StartResult startAll(ServerPlayer player, int speedMultiplier) throws Exception {
        return startAll(player, speedMultiplier, null);
    }

    public static synchronized DebugStructureCaptureTypes.StartResult startAll(ServerPlayer player, int speedMultiplier, ResourceLocation dimensionId) throws Exception {
        return startAll(player, null, speedMultiplier, dimensionId, false, null, null);
    }

    public static synchronized DebugStructureCaptureTypes.StartResult startAll(ServerPlayer player, int speedMultiplier, ResourceLocation dimensionId, String excludedNamespace, ResourceLocation excludedDimensionId) throws Exception {
        return startAll(player, null, speedMultiplier, dimensionId, false, excludedNamespace, excludedDimensionId);
    }

    public static synchronized DebugStructureCaptureTypes.StartResult startByNamespace(ServerPlayer player, String namespace, int speedMultiplier, ResourceLocation dimensionId) throws Exception {
        return startAll(player, namespace, speedMultiplier, dimensionId, false, null, null);
    }

    public static synchronized DebugStructureCaptureTypes.StartResult startRemainingAll(ServerPlayer player, int speedMultiplier, ResourceLocation dimensionId) throws Exception {
        return startAll(player, null, speedMultiplier, dimensionId, true, null, null);
    }

    public static synchronized DebugStructureCaptureTypes.StartResult startRemainingAll(ServerPlayer player, int speedMultiplier, ResourceLocation dimensionId, String excludedNamespace, ResourceLocation excludedDimensionId) throws Exception {
        return startAll(player, null, speedMultiplier, dimensionId, true, excludedNamespace, excludedDimensionId);
    }

    public static synchronized DebugStructureCaptureTypes.StartResult startRemainingByNamespace(ServerPlayer player, String namespace, int speedMultiplier, ResourceLocation dimensionId) throws Exception {
        return startAll(player, namespace, speedMultiplier, dimensionId, true, null, null);
    }

    public static synchronized DebugStructureCaptureTypes.StartResult startSingle(ServerPlayer player, ResourceLocation structureId) throws Exception {
        return startSingle(player, structureId, DebugStructureCaptureCommon.DEFAULT_SPEED_MULTIPLIER, null);
    }

    public static synchronized DebugStructureCaptureTypes.StartResult startSingle(ServerPlayer player, ResourceLocation structureId, int speedMultiplier) throws Exception {
        return startSingle(player, structureId, speedMultiplier, null);
    }

    public static synchronized DebugStructureCaptureTypes.StartResult startSingle(ServerPlayer player, ResourceLocation structureId, int speedMultiplier, ResourceLocation dimensionId) throws Exception {
        if (activeSession != null || !DebugStructureCaptureCoordinator.canStartQuick()) {
            return DebugStructureCaptureTypes.StartResult.busy();
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return DebugStructureCaptureTypes.StartResult.empty();
        }
        List<StructureTarget> targets = DebugStructureCaptureTargets.buildTargets(server, structureId, null, dimensionId, false, null, null);
        if (targets.isEmpty()) {
            return DebugStructureCaptureTypes.StartResult.missing(structureId);
        }
        activeSession = new Session(player, targets, DebugStructureCaptureCommon.normalizeSpeedMultiplier(speedMultiplier));
        DebugStructureCaptureCoordinator.markQuickStarted();
        return DebugStructureCaptureTypes.StartResult.started(activeSession.outputRoot, targets.size(), activeSession.speedMultiplier);
    }

    private static synchronized DebugStructureCaptureTypes.StartResult startAll(ServerPlayer player, String namespace, int speedMultiplier, ResourceLocation dimensionId, boolean skipReported, String excludedNamespace, ResourceLocation excludedDimensionId) throws Exception {
        if (activeSession != null || !DebugStructureCaptureCoordinator.canStartQuick()) {
            return DebugStructureCaptureTypes.StartResult.busy();
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return DebugStructureCaptureTypes.StartResult.empty();
        }
        List<StructureTarget> targets = DebugStructureCaptureTargets.buildTargets(server, null, namespace, dimensionId, skipReported, excludedNamespace, excludedDimensionId);
        if (targets.isEmpty()) {
            return DebugStructureCaptureTypes.StartResult.empty();
        }
        activeSession = new Session(player, targets, DebugStructureCaptureCommon.normalizeSpeedMultiplier(speedMultiplier));
        DebugStructureCaptureCoordinator.markQuickStarted();
        return DebugStructureCaptureTypes.StartResult.started(activeSession.outputRoot, targets.size(), activeSession.speedMultiplier);
    }

    public static synchronized DebugStructureCaptureTypes.StatusSnapshot getStatus() {
        if (activeSession == null) {
            return DebugStructureCaptureTypes.StatusSnapshot.idle();
        }
        return activeSession.createStatusSnapshot();
    }

    public static synchronized DebugStructureCaptureTypes.StopResult requestStop() {
        if (activeSession == null) {
            return DebugStructureCaptureTypes.StopResult.idle();
        }
        activeSession.stopRequested = true;
        activeSession.refreshCurrentPhaseTiming();
        return DebugStructureCaptureTypes.StopResult.active(activeSession.outputRoot, activeSession.createTimingSnapshot());
    }

    public static synchronized void tick(MinecraftServer server) {
        if (activeSession == null) {
            return;
        }
        try {
            if (activeSession.tick(server)) {
                activeSession = null;
                DebugStructureCaptureCoordinator.markQuickStopped();
            }
        } catch (Exception exception) {
            JeiStructures.LOGGER.error("结构调试采集执行失败", exception);
            try {
                activeSession.fail(exception);
            } catch (Exception nested) {
                JeiStructures.LOGGER.error("结构调试采集收尾失败", nested);
            } finally {
                activeSession = null;
                DebugStructureCaptureCoordinator.markQuickStopped();
            }
        }
    }

    private enum Phase {
        COOLDOWN("结构间冷却"),
        LOCATE_DIMENSION("按维度定位结构"),
        PREPARE_CAPTURE("准备采集结构"),
        LOAD_CHUNKS("加载结构区块"),
        SCAN_LOOT("扫描战利品"),
        WAIT_MOBS("等待生物出现"),
        SCAN_MOBS("采样生物"),
        CLEAR("清理残余生物"),
        WRITE("写出结果");

        private final String displayName;

        Phase(String displayName) {
            this.displayName = displayName;
        }
    }

    private static final class Session {
        private final MinecraftServer server;
        private final UUID playerId;
        private final ResourceKey<Level> playerLevelKey;
        private final BlockPos baseOrigin;
        private final float baseYaw;
        private final float basePitch;
        private final long sessionStartMillis;
        private final int speedMultiplier;
        private final Path outputRoot;
        private final Path structureToMobsRoot;
        private final Path structureLootBindingsRoot;
        private final List<StructureTarget> targets;
        private final List<ResourceKey<Level>> orderedDimensions;
        private final List<DebugStructureCaptureExport.FailureEntryData> failures = new ArrayList<>();
        private final LinkedHashSet<String> completedStructureIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> failedStructureIds = new LinkedHashSet<>();
        private final Map<String, LinkedHashSet<ResourceKey<Level>>> failedLocateByStructureId = new LinkedHashMap<>();
        private final Map<UUID, AsyncLocateRequest> activeLocateRequests = new LinkedHashMap<>();
        private final Deque<PrelocatedTarget> locatedCaptureQueue = new ArrayDeque<>();
        private final TimingStats timingStats = new TimingStats();
        private final LootTableItemResolver lootResolver;

        private StructureTarget currentTarget;
        private AttemptContext currentAttempt;
        private Phase currentPhase = Phase.COOLDOWN;
        private ResourceKey<Level> currentDimensionKey;
        private List<StructureTarget> currentDimensionTargets = List.of();
        private int currentDimensionIndex = -1;
        private int currentDimensionNextLocateIndex;
        private int currentDimensionLocateSubmitted;
        private int currentDimensionLocateCompleted;
        private int currentDimensionLocateSucceeded;
        private int currentDimensionCaptureTotal;
        private int currentDimensionCaptureCompleted;
        private int cooldownTicks;
        private boolean stopRequested;
        private boolean stoppedEarly;
        private boolean cleanedUp;
        private long currentStructureStartMillis;
        private long phaseStartMillis;

        private Session(ServerPlayer player, List<StructureTarget> targets, int speedMultiplier) throws Exception {
            this.server = player.getServer();
            this.playerId = player.getUUID();
            this.playerLevelKey = player.serverLevel().dimension();
            this.baseOrigin = player.blockPosition().immutable();
            this.baseYaw = player.getYRot();
            this.basePitch = player.getXRot();
            this.sessionStartMillis = System.currentTimeMillis();
            this.speedMultiplier = speedMultiplier;
            this.targets = List.copyOf(targets);
            this.orderedDimensions = collectOrderedDimensions(this.server, this.targets);
            this.outputRoot = StructureBindingPaths.getReportRunRoot(createRunId());
            this.structureToMobsRoot = outputRoot.resolve("structure_to_mobs");
            this.structureLootBindingsRoot = outputRoot.resolve("structure_loot_bindings");
            Files.createDirectories(this.structureToMobsRoot);
            Files.createDirectories(this.structureLootBindingsRoot);
            Files.createDirectories(this.outputRoot);
            this.lootResolver = new LootTableItemResolver(server.getResourceManager(), server.registryAccess().registryOrThrow(Registries.ITEM));
            this.cooldownTicks = 0;
            DebugCaptureOptimizationGuard.enable(this.playerId);
            DebugStructureCheckConcurrency.enable();
        }

        private DebugStructureCaptureTypes.StatusSnapshot createStatusSnapshot() {
            String structureId = currentTarget != null ? currentTarget.structureId().toString() : "";
            int structureIndex = Math.min(completedStructureIds.size() + (currentTarget != null ? 1 : 0), targets.size());
            int loadedChunkCount = currentAttempt != null ? currentAttempt.loadedChunkCount : 0;
            int totalChunkCount = currentAttempt != null ? currentAttempt.placeChunks.size() : 0;
            long structureElapsedSeconds = currentStructureStartMillis > 0L ? toSeconds(System.currentTimeMillis() - currentStructureStartMillis) : 0L;
            AsyncLocateRequest longestLocateRequest = getLongestActiveLocateRequest();
            return new DebugStructureCaptureTypes.StatusSnapshot(
                    true,
                    structureId,
                    structureIndex,
                    targets.size(),
                    completedStructureIds.size(),
                    targets.size(),
                    currentPhase.displayName,
                    1,
                    1,
                    speedMultiplier,
                    loadedChunkCount,
                    totalChunkCount,
                    currentDimensionKey != null ? currentDimensionKey.location().toString() : "",
                    currentDimensionIndex >= 0 ? currentDimensionIndex + 1 : 0,
                    orderedDimensions.size(),
                    currentDimensionLocateCompleted,
                    currentDimensionTargets.size(),
                    currentDimensionLocateSucceeded,
                    activeLocateRequests.size(),
                    MAX_CONCURRENT_LOCATE_REQUESTS,
                    longestLocateRequest != null ? longestLocateRequest.target().structureId().toString() : "",
                    longestLocateRequest != null ? toSeconds(System.currentTimeMillis() - longestLocateRequest.startMillis()) : 0L,
                    currentDimensionCaptureCompleted,
                    currentDimensionCaptureTotal,
                    outputRoot,
                    stopRequested,
                    toSeconds(System.currentTimeMillis() - sessionStartMillis),
                    structureElapsedSeconds
            );
        }

        private DebugStructureCaptureTypes.TimingSnapshot createTimingSnapshot() {
            long totalMillis = System.currentTimeMillis() - sessionStartMillis;
            return new DebugStructureCaptureTypes.TimingSnapshot(
                    formatDuration(toSeconds(totalMillis)),
                    formatDuration(toSeconds(timingStats.cooldownMillis)),
                    formatDuration(toSeconds(timingStats.locateMillis)),
                    formatDuration(toSeconds(timingStats.prepareMillis)),
                    formatDuration(toSeconds(timingStats.loadChunksMillis)),
                    formatDuration(toSeconds(timingStats.lootMillis)),
                    formatDuration(toSeconds(timingStats.mobWaitMillis)),
                    formatDuration(toSeconds(timingStats.mobScanMillis)),
                    formatDuration(toSeconds(timingStats.clearMillis)),
                    formatDuration(toSeconds(timingStats.writeMillis)),
                    formatDuration(toSeconds(timingStats.otherMillis(totalMillis))),
                    0
            );
        }

        private boolean tick(MinecraftServer server) throws Exception {
            if (server == null) {
                return true;
            }
            if (stopRequested) {
                stoppedEarly = completedStructureIds.size() < targets.size();
                finish();
                return true;
            }
            return switch (currentPhase) {
                case COOLDOWN -> tickCooldown(server);
                case LOCATE_DIMENSION -> tickLocateDimension(server);
                case PREPARE_CAPTURE -> tickPrepareCapture(server);
                case LOAD_CHUNKS -> tickLoadChunks();
                case SCAN_LOOT -> tickScanLoot();
                case WAIT_MOBS -> tickWaitMobs();
                case SCAN_MOBS -> tickScanMobs();
                case CLEAR -> tickClear();
                case WRITE -> tickWrite();
            };
        }

        private boolean tickCooldown(MinecraftServer server) throws Exception {
            if (cooldownTicks > 0) {
                cooldownTicks--;
                return false;
            }
            if (!locatedCaptureQueue.isEmpty()) {
                enterPhase(Phase.PREPARE_CAPTURE);
                return false;
            }
            return startNextDimensionOrFinish(server);
        }

        private boolean tickLocateDimension(MinecraftServer server) throws Exception {
            boolean timeoutChanged = handleLocateTimeouts(server);
            submitLocateRequests(server);
            if (timeoutChanged) {
                sendDimensionLocateProgress(server);
            }
            if (currentDimensionLocateCompleted < currentDimensionTargets.size() || !activeLocateRequests.isEmpty()) {
                return false;
            }
            sendPlayerMessage(
                    server,
                    "jei_structures.command.debug_capture.progress.dimension_locate_finish",
                    currentDimensionKey.location(),
                    currentDimensionLocateCompleted,
                    currentDimensionTargets.size(),
                    currentDimensionLocateSucceeded,
                    locatedCaptureQueue.size()
            );
            currentDimensionCaptureTotal = locatedCaptureQueue.size();
            currentDimensionCaptureCompleted = 0;
            if (locatedCaptureQueue.isEmpty()) {
                sendPlayerMessage(
                        server,
                        "jei_structures.command.debug_capture.progress.dimension_capture_skip_empty",
                        currentDimensionKey.location(),
                        completedStructureIds.size(),
                        targets.size()
                );
                return startNextDimensionOrFinish(server);
            }
            enterPhase(Phase.PREPARE_CAPTURE);
            return false;
        }

        private boolean tickPrepareCapture(MinecraftServer server) throws Exception {
            PrelocatedTarget prelocatedTarget;
            while ((prelocatedTarget = locatedCaptureQueue.pollFirst()) != null) {
                StructureTarget target = prelocatedTarget.target();
                String structureId = target.structureId().toString();
                if (completedStructureIds.contains(structureId) || failedStructureIds.contains(structureId)) {
                    continue;
                }
                currentTarget = target;
                currentAttempt = new AttemptContext(target.structureId().toString(), target.entry().structureType);
                currentAttempt.finishLocate(prelocatedTarget.locatedStructure());
                currentAttempt.teleportPos = teleportPlayerToCapturePosition(server, prelocatedTarget.locatedStructure());
                currentStructureStartMillis = System.currentTimeMillis();
                sendPlayerMessage(
                        server,
                        "jei_structures.command.debug_capture.progress.dimension_capture_start",
                        DebugStructureCaptureSupport.getStructureDisplayComponent(target.structureId()),
                        currentDimensionKey.location(),
                        currentDimensionCaptureCompleted + 1,
                        currentDimensionCaptureTotal,
                        completedStructureIds.size(),
                        targets.size()
                );
                sendAttemptPrepareMessage(server, prelocatedTarget.locatedStructure());
                enterPhase(Phase.LOAD_CHUNKS);
                return false;
            }
            currentDimensionCaptureTotal = currentDimensionCaptureCompleted;
            return startNextDimensionOrFinish(server);
        }

        private void sendAttemptPrepareMessage(MinecraftServer server, LocatedStructure locatedStructure) {
            sendPlayerMessage(
                    server,
                    "jei_structures.command.debug_capture.progress.attempt_prepare",
                    DebugStructureCaptureSupport.getStructureDisplayComponent(currentTarget.structureId()),
                    1,
                    1,
                    locatedStructure.locatePos().getX(),
                    locatedStructure.locatePos().getY(),
                    locatedStructure.locatePos().getZ(),
                    currentAttempt.teleportPos.getX(),
                    currentAttempt.teleportPos.getY(),
                    currentAttempt.teleportPos.getZ(),
                    locatedStructure.box().minX(),
                    locatedStructure.box().minY(),
                    locatedStructure.box().minZ(),
                    locatedStructure.box().maxX(),
                    locatedStructure.box().maxY(),
                    locatedStructure.box().maxZ(),
                    locatedStructure.placeChunks().size(),
                    locatedStructure.pieceCount(),
                    locatedStructure.level().dimension().location()
            );
        }

        private boolean tickLoadChunks() {
            long start = System.currentTimeMillis();
            ServerLevel level = currentAttempt.level;
            int totalChunks = currentAttempt.placeChunks.size();
            if (!currentAttempt.chunkLoadingStarted) {
                currentAttempt.chunkLoadingStarted = true;
                sendPlayerMessage(
                        server,
                        "jei_structures.command.debug_capture.progress.place_loading",
                        DebugStructureCaptureSupport.getStructureDisplayComponent(currentTarget.structureId()),
                        1,
                        0,
                        totalChunks,
                        currentAttempt.pieceCount,
                        currentDimensionKey != null ? currentDimensionKey.location() : "",
                        currentDimensionCaptureCompleted + 1,
                        currentDimensionCaptureTotal,
                        completedStructureIds.size(),
                        targets.size(),
                        speedMultiplier
                );
                return false;
            }
            int startIndex = currentAttempt.loadedChunkCount;
            int endIndex = Math.min(startIndex + JeiStructuresConfig.captureChunkLoadsPerTick(), totalChunks);
            JeiStructures.LOGGER.info(
                    "结构调试区块批次开始：structure={}，dimension={}，progress={}/{}，batch={}..{}，chunks={}",
                    currentTarget.structureId(),
                    level.dimension().location(),
                    startIndex,
                    totalChunks,
                    startIndex + 1,
                    endIndex,
                    describeChunkBatch(startIndex, endIndex)
            );
            for (int index = startIndex; index < endIndex; index++) {
                ChunkPos chunkPos = currentAttempt.placeChunks.get(index);
                long chunkStart = System.currentTimeMillis();
                JeiStructures.LOGGER.info(
                        "结构调试准备加载区块：structure={}，dimension={}，index={}/{}，chunk=({}, {})",
                        currentTarget.structureId(),
                        level.dimension().location(),
                        index + 1,
                        totalChunks,
                        chunkPos.x,
                        chunkPos.z
                );
                level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true);
                long chunkMillis = System.currentTimeMillis() - chunkStart;
                if (chunkMillis >= CHUNK_LOAD_SLOW_WARN_MILLIS) {
                    JeiStructures.LOGGER.warn(
                            "结构调试慢区块加载：structure={}，dimension={}，index={}/{}，chunk=({}, {})，耗时={}ms",
                            currentTarget.structureId(),
                            level.dimension().location(),
                            index + 1,
                            totalChunks,
                            chunkPos.x,
                            chunkPos.z,
                            chunkMillis
                    );
                } else {
                    JeiStructures.LOGGER.info(
                            "结构调试区块加载完成：structure={}，dimension={}，index={}/{}，chunk=({}, {})，耗时={}ms",
                            currentTarget.structureId(),
                            level.dimension().location(),
                            index + 1,
                            totalChunks,
                            chunkPos.x,
                            chunkPos.z,
                            chunkMillis
                    );
                }
            }
            currentAttempt.loadedChunkCount = endIndex;
            long batchMillis = System.currentTimeMillis() - start;
            timingStats.loadChunksMillis += batchMillis;
            if (batchMillis >= CHUNK_LOAD_BATCH_SLOW_WARN_MILLIS) {
                JeiStructures.LOGGER.warn(
                        "结构调试慢区块批次：structure={}，dimension={}，progress={}/{}，batch={}..{}，耗时={}ms",
                        currentTarget.structureId(),
                        level.dimension().location(),
                        currentAttempt.loadedChunkCount,
                        totalChunks,
                        startIndex + 1,
                        endIndex,
                        batchMillis
                );
            } else {
                JeiStructures.LOGGER.info(
                        "结构调试区块批次完成：structure={}，dimension={}，progress={}/{}，batch={}..{}，耗时={}ms",
                        currentTarget.structureId(),
                        level.dimension().location(),
                        currentAttempt.loadedChunkCount,
                        totalChunks,
                        startIndex + 1,
                        endIndex,
                        batchMillis
                );
            }
            if (currentAttempt.loadedChunkCount < totalChunks) {
                sendPlayerMessage(
                        server,
                        "jei_structures.command.debug_capture.progress.place_loading",
                        DebugStructureCaptureSupport.getStructureDisplayComponent(currentTarget.structureId()),
                        1,
                        currentAttempt.loadedChunkCount,
                        totalChunks,
                        currentAttempt.pieceCount,
                        currentDimensionKey != null ? currentDimensionKey.location() : "",
                        currentDimensionCaptureCompleted + 1,
                        currentDimensionCaptureTotal,
                        completedStructureIds.size(),
                        targets.size(),
                        speedMultiplier
                );
                return false;
            }
            sendPlayerMessage(
                    server,
                    "jei_structures.command.debug_capture.progress.place_finish",
                    DebugStructureCaptureSupport.getStructureDisplayComponent(currentTarget.structureId()),
                    1,
                    currentAttempt.loadedChunkCount,
                    totalChunks,
                    currentAttempt.pieceCount,
                    currentDimensionKey != null ? currentDimensionKey.location() : "",
                    currentDimensionCaptureCompleted + 1,
                    currentDimensionCaptureTotal,
                    completedStructureIds.size(),
                    targets.size(),
                    speedMultiplier
            );
            enterPhase(Phase.SCAN_LOOT);
            return false;
        }

        private String describeChunkBatch(int startIndex, int endIndex) {
            if (currentAttempt == null || currentAttempt.placeChunks == null || currentAttempt.placeChunks.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            for (int index = startIndex; index < endIndex && index < currentAttempt.placeChunks.size(); index++) {
                if (!builder.isEmpty()) {
                    builder.append(", ");
                }
                ChunkPos chunkPos = currentAttempt.placeChunks.get(index);
                builder.append('(').append(chunkPos.x).append(", ").append(chunkPos.z).append(')');
            }
            return builder.toString();
        }

        private boolean tickScanLoot() {
            long start = System.currentTimeMillis();
            scanLootBindings();
            timingStats.lootMillis += System.currentTimeMillis() - start;
            sendPlayerMessage(
                    server,
                    "jei_structures.command.debug_capture.progress.loot_finish",
                    DebugStructureCaptureSupport.getStructureDisplayComponent(currentTarget.structureId()),
                    1,
                    currentAttempt.lootPositions.size(),
                    currentAttempt.aggregate.getLootBindingCount(),
                    currentAttempt.aggregate.getLootTableCount(),
                    currentAttempt.aggregate.getLootItemCount(),
                    speedMultiplier
            );
            currentAttempt.mobWaitTicks = DebugStructureCaptureCommon.scaleWaitTicks(BASE_MOB_WAIT_TICKS, speedMultiplier);
            enterPhase(Phase.WAIT_MOBS);
            return false;
        }

        private boolean tickWaitMobs() {
            if (currentAttempt.mobWaitTicks > 0) {
                currentAttempt.mobWaitTicks--;
                return false;
            }
            enterPhase(Phase.SCAN_MOBS);
            return false;
        }

        private boolean tickScanMobs() {
            long start = System.currentTimeMillis();
            DebugStructureCaptureScanning.MobSampleResult result = DebugStructureCaptureScanning.captureLivingEntities(
                    new DebugStructureCaptureScanning.ServerLevelContext(
                            currentAttempt.level,
                            entity -> entity instanceof Player player && player.getUUID().equals(playerId)
                    ),
                    currentAttempt.box,
                    currentAttempt.aggregate.mobIds,
                    true
            );
            timingStats.mobScanMillis += System.currentTimeMillis() - start;
            sendPlayerMessage(
                    server,
                    "jei_structures.command.debug_capture.progress.mob_sample",
                    DebugStructureCaptureSupport.getStructureDisplayComponent(currentTarget.structureId()),
                    1,
                    1,
                    1,
                    result.sampledLivingCount(),
                    result.newMobCount(),
                    result.totalMobCount(),
                    speedMultiplier
            );
            enterPhase(Phase.CLEAR);
            return false;
        }

        private boolean tickClear() {
            long start = System.currentTimeMillis();
            clearRemainingMobs();
            timingStats.clearMillis += System.currentTimeMillis() - start;
            enterPhase(Phase.WRITE);
            return false;
        }

        private boolean tickWrite() throws Exception {
            if (currentAttempt != null && currentAttempt.successful) {
                long start = System.currentTimeMillis();
                writeCurrentStructureFiles();
                timingStats.writeMillis += System.currentTimeMillis() - start;
                completedStructureIds.add(currentTarget.structureId().toString());
                currentDimensionCaptureCompleted++;
                sendPlayerMessage(
                        server,
                        "jei_structures.command.debug_capture.progress.attempt_finish",
                        DebugStructureCaptureSupport.getStructureDisplayComponent(currentTarget.structureId()),
                        1,
                        currentAttempt.aggregate.getMobCount(),
                        currentAttempt.aggregate.getLootBindingCount(),
                        currentAttempt.aggregate.getLootTableCount(),
                        currentAttempt.aggregate.getLootItemCount(),
                        currentAttempt.aggregate.getMobCount(),
                        currentAttempt.aggregate.getLootBindingCount(),
                        currentDimensionCaptureCompleted,
                        currentDimensionCaptureTotal,
                        completedStructureIds.size(),
                        targets.size()
                );
            }
            currentTarget = null;
            currentAttempt = null;
            currentStructureStartMillis = 0L;
            if (stopRequested) {
                stoppedEarly = completedStructureIds.size() < targets.size();
                finish();
                return true;
            }
            if (locatedCaptureQueue.isEmpty()) {
                return startNextDimensionOrFinish(server);
            }
            enterPhase(Phase.COOLDOWN);
            cooldownTicks = DebugStructureCaptureCommon.scaleWaitTicks(BASE_COOLDOWN_TICKS, speedMultiplier);
            return false;
        }

        private boolean startNextDimensionOrFinish(MinecraftServer server) throws Exception {
            currentTarget = null;
            currentAttempt = null;
            currentStructureStartMillis = 0L;
            activeLocateRequests.clear();
            locatedCaptureQueue.clear();
            while (++currentDimensionIndex < orderedDimensions.size()) {
                currentDimensionKey = orderedDimensions.get(currentDimensionIndex);
                ServerLevel level = server.getLevel(currentDimensionKey);
                if (level == null) {
                    continue;
                }
                currentDimensionTargets = collectCurrentDimensionTargets(currentDimensionKey);
                currentDimensionNextLocateIndex = 0;
                currentDimensionLocateSubmitted = 0;
                currentDimensionLocateCompleted = 0;
                currentDimensionLocateSucceeded = 0;
                currentDimensionCaptureTotal = 0;
                currentDimensionCaptureCompleted = 0;
                if (currentDimensionTargets.isEmpty()) {
                    continue;
                }
                sendPlayerMessage(
                        server,
                        "jei_structures.command.debug_capture.progress.dimension_locate_start",
                        currentDimensionKey.location(),
                        currentDimensionIndex + 1,
                        orderedDimensions.size(),
                        currentDimensionTargets.size(),
                        completedStructureIds.size(),
                        targets.size(),
                        MAX_CONCURRENT_LOCATE_REQUESTS
                );
                enterPhase(Phase.LOCATE_DIMENSION);
                return false;
            }
            currentDimensionKey = null;
            currentDimensionTargets = List.of();
            finish();
            return true;
        }

        private List<StructureTarget> collectCurrentDimensionTargets(ResourceKey<Level> levelKey) {
            List<StructureTarget> result = new ArrayList<>();
            for (StructureTarget target : targets) {
                String structureId = target.structureId().toString();
                if (completedStructureIds.contains(structureId) || failedStructureIds.contains(structureId)) {
                    continue;
                }
                if (target.candidateLevels().contains(levelKey)) {
                    result.add(target);
                }
            }
            return List.copyOf(result);
        }

        private void submitLocateRequests(MinecraftServer server) {
            if (server == null || currentDimensionKey == null) {
                return;
            }
            ServerLevel level = server.getLevel(currentDimensionKey);
            if (level == null) {
                while (currentDimensionNextLocateIndex < currentDimensionTargets.size()) {
                    markDimensionLocateFailure(currentDimensionTargets.get(currentDimensionNextLocateIndex++), currentDimensionKey);
                    currentDimensionLocateSubmitted++;
                    currentDimensionLocateCompleted++;
                }
                sendDimensionLocateProgress(server);
                return;
            }
            while (activeLocateRequests.size() < MAX_CONCURRENT_LOCATE_REQUESTS && currentDimensionNextLocateIndex < currentDimensionTargets.size()) {
                StructureTarget target = currentDimensionTargets.get(currentDimensionNextLocateIndex++);
                if (completedStructureIds.contains(target.structureId().toString()) || failedStructureIds.contains(target.structureId().toString())) {
                    currentDimensionLocateSubmitted++;
                    currentDimensionLocateCompleted++;
                    continue;
                }
                submitLocateRequest(server, level, target);
            }
        }

        private void submitLocateRequest(MinecraftServer server, ServerLevel level, StructureTarget target) {
            UUID requestId = UUID.randomUUID();
            BlockPos locateOrigin = resolveLocateOrigin(level.dimension(), level);
            Registry<Structure> structureRegistry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
            Holder<Structure> structureHolder = structureRegistry.wrapAsHolder(target.structure());
            HolderSet<Structure> structures = HolderSet.direct(structureHolder);
            currentDimensionLocateSubmitted++;
            AsyncLocator.LocateTask<Pair<BlockPos, Holder<Structure>>> locateTask;
            DebugLocateRadiusLimiter.begin(requestId, DEFAULT_LOCATE_RADIUS);
            try {
                locateTask = AsyncLocator.locate(level, structures, locateOrigin, DEFAULT_LOCATE_RADIUS, false);
            } catch (RuntimeException exception) {
                DebugLocateRadiusLimiter.end(requestId);
                markDimensionLocateFailure(target, level.dimension());
                currentDimensionLocateCompleted++;
                JeiStructures.LOGGER.warn("结构调试定位提交失败：{} @ {}", target.structureId(), level.dimension().location(), exception);
                sendDimensionLocateProgress(server);
                return;
            }
            AsyncLocateRequest request = new AsyncLocateRequest(
                    requestId,
                    target,
                    level,
                    locateOrigin,
                    System.currentTimeMillis(),
                    locateTask
            );
            activeLocateRequests.put(requestId, request);
            locateTask.thenOnServerThread(result -> acceptAsyncLocateResult(requestId, result));
        }

        private boolean handleLocateTimeouts(MinecraftServer server) {
            if (server == null || activeLocateRequests.isEmpty()) {
                return false;
            }
            long now = System.currentTimeMillis();
            boolean changed = false;
            for (AsyncLocateRequest request : List.copyOf(activeLocateRequests.values())) {
                if (now - request.startMillis() < LOCATE_REQUEST_TIMEOUT_MILLIS) {
                    continue;
                }
                if (activeLocateRequests.remove(request.requestId()) == null) {
                    continue;
                }
                if (request.task() != null) {
                    request.task().cancel();
                }
                DebugLocateRadiusLimiter.end(request.requestId());
                markDimensionLocateFailure(request.target(), request.level().dimension());
                currentDimensionLocateCompleted++;
                changed = true;
                sendPlayerMessage(
                        server,
                        "jei_structures.command.debug_capture.progress.dimension_locate_timeout",
                        DebugStructureCaptureSupport.getStructureDisplayComponent(request.target().structureId()),
                        request.level().dimension().location(),
                        LOCATE_REQUEST_TIMEOUT_MILLIS / 1000L,
                        currentDimensionLocateCompleted,
                        currentDimensionTargets.size()
                );
            }
            return changed;
        }

        private void acceptAsyncLocateResult(UUID requestId, Pair<BlockPos, Holder<Structure>> result) {
            if (requestId == null) {
                return;
            }
            AsyncLocateRequest request = activeLocateRequests.remove(requestId);
            if (request == null) {
                return;
            }
            DebugLocateRadiusLimiter.end(requestId);
            currentDimensionLocateCompleted++;
            if (result == null || result.getFirst() == null) {
                markDimensionLocateFailure(request.target(), request.level().dimension());
                submitLocateRequests(request.level().getServer());
                sendDimensionLocateProgress(request.level().getServer());
                return;
            }
            LocatedStructure locatedStructure = buildLocatedStructureFromLocateCommand(request.level(), request.target(), result.getFirst().immutable());
            if (locatedStructure == null) {
                markDimensionLocateFailure(request.target(), request.level().dimension());
                submitLocateRequests(request.level().getServer());
                sendDimensionLocateProgress(request.level().getServer());
                return;
            }
            String structureId = request.target().structureId().toString();
            if (!completedStructureIds.contains(structureId) && !failedStructureIds.contains(structureId)) {
                locatedCaptureQueue.addLast(new PrelocatedTarget(request.target(), locatedStructure));
                currentDimensionLocateSucceeded++;
            }
            submitLocateRequests(request.level().getServer());
            sendDimensionLocateProgress(request.level().getServer());
        }

        private void markDimensionLocateFailure(StructureTarget target, ResourceKey<Level> levelKey) {
            if (target == null || levelKey == null) {
                return;
            }
            failedLocateByStructureId.computeIfAbsent(target.structureId().toString(), ignored -> new LinkedHashSet<>()).add(levelKey);
        }

        private void sendDimensionLocateProgress(MinecraftServer server) {
            if (currentDimensionKey == null) {
                return;
            }
            AsyncLocateRequest longestLocateRequest = getLongestActiveLocateRequest();
            sendPlayerMessage(
                    server,
                    "jei_structures.command.debug_capture.progress.dimension_locate_update",
                    currentDimensionKey.location(),
                    currentDimensionLocateCompleted,
                    currentDimensionTargets.size(),
                    currentDimensionLocateSucceeded,
                    activeLocateRequests.size(),
                    MAX_CONCURRENT_LOCATE_REQUESTS,
                    longestLocateRequest != null ? DebugStructureCaptureSupport.getStructureDisplayComponent(longestLocateRequest.target().structureId()) : Component.literal("-"),
                    longestLocateRequest != null ? formatDuration(toSeconds(System.currentTimeMillis() - longestLocateRequest.startMillis())) : "-",
                    completedStructureIds.size(),
                    targets.size()
            );
        }

        private AsyncLocateRequest getLongestActiveLocateRequest() {
            AsyncLocateRequest longest = null;
            for (AsyncLocateRequest request : activeLocateRequests.values()) {
                if (request == null) {
                    continue;
                }
                if (longest == null || request.startMillis() < longest.startMillis()) {
                    longest = request;
                }
            }
            return longest;
        }

        private BlockPos resolveLocateOrigin(ResourceKey<Level> levelKey, ServerLevel level) {
            BlockPos base = levelKey.equals(playerLevelKey) ? baseOrigin : level.getSharedSpawnPos();
            if (Level.END.equals(levelKey)) {
                return base.offset(END_OUTER_ISLAND_OFFSET_X, 0, 0);
            }
            return base;
        }

        private LocatedStructure buildLocatedStructureFromLocateCommand(ServerLevel level, StructureTarget target, BlockPos locatePos) {
            if (level == null || target == null || locatePos == null) {
                return null;
            }
            StructureStart structureStart = level.structureManager().getStructureWithPieceAt(locatePos, target.structure());
            if (structureStart == null || !structureStart.isValid()) {
                ChunkPos chunkPos = new ChunkPos(locatePos);
                structureStart = level.structureManager().getStartForStructure(
                        SectionPos.of(locatePos),
                        target.structure(),
                        level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS)
                );
            }
            if (structureStart == null || !structureStart.isValid()) {
                return null;
            }
            BoundingBox box = structureStart.getBoundingBox();
            DebugStructureCaptureWorld.ChunkCoverage coverage = DebugStructureCaptureWorld.collectStructureChunkCoverage(structureStart, box);
            return new LocatedStructure(
                    level,
                    new BlockPos((box.minX() + box.maxX()) >> 1, Math.max(box.minY(), level.getMinBuildHeight()), (box.minZ() + box.maxZ()) >> 1),
                    structureStart,
                    box,
                    coverage.chunks(),
                    structureStart.getPieces().size()
            );
        }

        private void scanLootBindings() {
            currentAttempt.lootPositions = DebugStructureCaptureScanning.collectLootPositions(currentAttempt.level, currentAttempt.box, currentAttempt.placeChunks);
            for (BlockPos pos : currentAttempt.lootPositions) {
                BlockEntity blockEntity = currentAttempt.level.getBlockEntity(pos);
                if (blockEntity == null) {
                    continue;
                }
                BlockState blockState = currentAttempt.level.getBlockState(pos);
                ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(blockState.getBlock());
                if (blockId == null) {
                    continue;
                }
                var nbt = blockEntity.saveWithId();
                String lootTableId = DebugStructureCaptureScanning.readLootTable(nbt);
                LinkedHashSet<String> storedItems = StoredItemNbtReader.readStoredItems(nbt);
                StructureIndexCache.LootTableDetail detail = null;
                LinkedHashSet<String> lootItems = new LinkedHashSet<>();
                if (!lootTableId.isBlank()) {
                    detail = buildLootTableDetail(lootTableId);
                    if (detail != null) {
                        for (StructureIndexCache.LootItemEntry entry : detail.entries) {
                            if (entry != null && entry.itemId != null && !entry.itemId.isBlank()) {
                                lootItems.add(entry.itemId);
                            }
                        }
                    }
                    if (lootItems.isEmpty()) {
                        ResourceLocation lootId = ResourceLocation.tryParse(lootTableId);
                        if (lootId != null) {
                            lootItems.addAll(lootResolver.resolveLootItems(lootId));
                        }
                    }
                }
                if (storedItems.isEmpty() && lootItems.isEmpty() && detail == null) {
                    continue;
                }
                currentAttempt.aggregate.recordLoot(blockId.toString(), storedItems, lootItems, detail);
            }
            currentAttempt.successful = true;
        }

        private StructureIndexCache.LootTableDetail buildLootTableDetail(String lootTableId) {
            if (lootTableId == null || lootTableId.isBlank()) {
                return null;
            }
            ResourceLocation lootId = ResourceLocation.tryParse(lootTableId);
            if (lootId == null) {
                return null;
            }
            return lootResolver.resolveLootTableDetail(lootId);
        }

        private void clearRemainingMobs() {
            if (currentAttempt == null || currentAttempt.level == null || currentAttempt.box == null) {
                return;
            }
            var bounds = new net.minecraft.world.phys.AABB(
                    currentAttempt.box.minX(),
                    currentAttempt.box.minY(),
                    currentAttempt.box.minZ(),
                    currentAttempt.box.maxX() + 1.0D,
                    currentAttempt.box.maxY() + 1.0D,
                    currentAttempt.box.maxZ() + 1.0D
            );
            currentAttempt.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, bounds, entity -> !(entity instanceof Player player && player.getUUID().equals(playerId)))
                    .forEach(net.minecraft.world.entity.Entity::discard);
        }

        private void writeCurrentStructureFiles() throws Exception {
            if (currentTarget == null || currentAttempt == null) {
                return;
            }
            List<JsonObject> lootBindingObjects = currentAttempt.aggregate.toJsonObjects();
            DebugStructureCaptureExport.writeCurrentStructureFiles(
                    structureToMobsRoot,
                    structureLootBindingsRoot,
                    currentTarget.structureId(),
                    currentAttempt.aggregate.structureId,
                    currentAttempt.aggregate.mobIds,
                    lootBindingObjects
            );
        }

        private void recordFailure(String phaseName, String reason) {
            if (currentTarget == null) {
                return;
            }
            ResourceKey<Level> levelKey = currentAttempt != null && currentAttempt.level != null ? currentAttempt.level.dimension() : currentTarget.primaryLevel();
            String levelId = levelKey != null ? levelKey.location().toString() : "";
            BlockPos origin = currentAttempt != null && currentAttempt.locatePos != null ? currentAttempt.locatePos : baseOrigin;
            recordFailure(currentTarget, phaseName, reason, levelKey, levelId, origin);
        }

        private void recordFailure(StructureTarget target, String phaseName, String reason, ResourceKey<Level> levelKey, String levelId, BlockPos origin) {
            if (target == null) {
                return;
            }
            failedStructureIds.add(target.structureId().toString());
            failures.add(DebugStructureCaptureExport.createFailureEntry(
                    target.structureId().toString(),
                    target.entry().structureType,
                    1,
                    levelKey,
                    levelId,
                    origin,
                    phaseName,
                    reason
            ));
        }

        private void fail(Exception exception) throws Exception {
            if (currentTarget != null) {
                recordFailure(currentPhase.displayName, exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName());
            }
            stoppedEarly = true;
            finish();
        }

        private void finish() throws Exception {
            refreshCurrentPhaseTiming();
            try {
                recordUnresolvedFailures();
                long start = System.currentTimeMillis();
                DebugStructureCaptureExport.writeFailureFile(outputRoot, "failed_structures.json", failures);
                timingStats.writeMillis += System.currentTimeMillis() - start;
            } finally {
                DebugCaptureOptimizationGuard.disable();
                restorePlayer(server);
                cleanupSession();
            }
            sendPlayerMessage(
                    server,
                    stoppedEarly ? "jei_structures.command.debug_capture.finished_stopped" : "jei_structures.command.debug_capture.finished",
                    outputRoot,
                    completedStructureIds.size(),
                    failures.size()
            );
            sendTimingSummary(server);
        }

        private void recordUnresolvedFailures() {
            if (stoppedEarly || stopRequested) {
                return;
            }
            for (StructureTarget target : targets) {
                String structureId = target.structureId().toString();
                if (completedStructureIds.contains(structureId) || failedStructureIds.contains(structureId)) {
                    continue;
                }
                ResourceKey<Level> levelKey = target.primaryLevel();
                String levelId = levelKey != null ? levelKey.location().toString() : "";
                recordFailure(target, "定位结构", "所有候选维度均未定位到自然结构", levelKey, levelId, baseOrigin);
            }
        }

        private void cleanupSession() {
            if (cleanedUp) {
                return;
            }
            for (AsyncLocateRequest request : activeLocateRequests.values()) {
                if (request.task() != null) {
                    request.task().cancel();
                }
                DebugLocateRadiusLimiter.end(request.requestId());
            }
            activeLocateRequests.clear();
            DebugCaptureOptimizationGuard.disable();
            DebugStructureCheckConcurrency.disable();
            cleanedUp = true;
        }

        private void enterPhase(Phase phase) {
            long now = System.currentTimeMillis();
            if (phaseStartMillis > 0L) {
                long elapsed = now - phaseStartMillis;
                addPhaseElapsed(currentPhase, elapsed);
                JeiStructures.LOGGER.info(
                        "结构调试阶段耗时：from={}，to={}，elapsed={}ms，structure={}，dimension={}",
                        currentPhase.displayName,
                        phase.displayName,
                        elapsed,
                        currentTarget != null ? currentTarget.structureId() : "-",
                        currentDimensionKey != null ? currentDimensionKey.location() : "-"
                );
            }
            currentPhase = phase;
            phaseStartMillis = now;
        }

        private void refreshCurrentPhaseTiming() {
            long now = System.currentTimeMillis();
            if (phaseStartMillis <= 0L) {
                return;
            }
            long elapsed = now - phaseStartMillis;
            addPhaseElapsed(currentPhase, elapsed);
            JeiStructures.LOGGER.info(
                    "结构调试阶段耗时刷新：phase={}，elapsed={}ms，structure={}，dimension={}",
                    currentPhase.displayName,
                    elapsed,
                    currentTarget != null ? currentTarget.structureId() : "-",
                    currentDimensionKey != null ? currentDimensionKey.location() : "-"
            );
            phaseStartMillis = now;
        }

        private void addPhaseElapsed(Phase phase, long elapsed) {
            switch (phase) {
                case COOLDOWN -> timingStats.cooldownMillis += elapsed;
                case LOCATE_DIMENSION -> timingStats.locateMillis += elapsed;
                case PREPARE_CAPTURE -> timingStats.prepareMillis += elapsed;
                case LOAD_CHUNKS -> timingStats.loadChunksMillis += 0L;
                case SCAN_LOOT -> timingStats.lootMillis += 0L;
                case WAIT_MOBS -> timingStats.mobWaitMillis += elapsed;
                case SCAN_MOBS -> timingStats.mobScanMillis += 0L;
                case CLEAR -> timingStats.clearMillis += 0L;
                case WRITE -> timingStats.writeMillis += 0L;
            }
        }

        private void sendTimingSummary(MinecraftServer server) {
            DebugStructureCaptureTypes.TimingSnapshot timingSnapshot = createTimingSnapshot();
            sendPlayerMessage(
                    server,
                    "jei_structures.command.debug_capture.finished_timing",
                    timingSnapshot.totalDuration(),
                    timingSnapshot.cooldownDuration(),
                    timingSnapshot.locateDuration(),
                    timingSnapshot.prepareDuration(),
                    timingSnapshot.placeDuration(),
                    timingSnapshot.lootDuration(),
                    timingSnapshot.mobWaitDuration(),
                    timingSnapshot.mobScanDuration(),
                    timingSnapshot.clearDuration(),
                    timingSnapshot.writeDuration(),
                    timingSnapshot.otherDuration(),
                    timingSnapshot.slowLocateStructureCount()
            );
            JeiStructures.LOGGER.info(
                    "结构调试耗时统计：total={}，cooldown={}，locate={}，prepare={}，chunks={}，loot={}，mobWait={}，mobScan={}，clear={}，write={}，other={}，slowLocate={}",
                    timingSnapshot.totalDuration(),
                    timingSnapshot.cooldownDuration(),
                    timingSnapshot.locateDuration(),
                    timingSnapshot.prepareDuration(),
                    timingSnapshot.placeDuration(),
                    timingSnapshot.lootDuration(),
                    timingSnapshot.mobWaitDuration(),
                    timingSnapshot.mobScanDuration(),
                    timingSnapshot.clearDuration(),
                    timingSnapshot.writeDuration(),
                    timingSnapshot.otherDuration(),
                    timingSnapshot.slowLocateStructureCount()
            );
        }

        private void sendPlayerMessage(MinecraftServer server, String key, Object... args) {
            if (server == null) {
                return;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return;
            }
            CommandSourceStack source = player.createCommandSourceStack();
            source.sendSuccess(() -> createPlayerMessage(key, args), false);
        }

        private Component createPlayerMessage(String key, Object... args) {
            MutableComponent message = Component.translatable(key, args);
            if (key != null && key.startsWith("jei_structures.command.debug_capture.progress.")) {
                return Component.translatable(
                        "jei_structures.command.debug_capture.progress.with_elapsed",
                        message,
                        formatDuration(toSeconds(System.currentTimeMillis() - sessionStartMillis))
                );
            }
            return message;
        }

        private BlockPos teleportPlayerToCapturePosition(MinecraftServer server, LocatedStructure locatedStructure) {
            BlockPos teleportPos = resolveTeleportPos(locatedStructure);
            if (server == null) {
                return teleportPos;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return teleportPos;
            }
            ServerLevel targetLevel = locatedStructure.level();
            player.teleportTo(
                    targetLevel,
                    teleportPos.getX() + 0.5D,
                    teleportPos.getY(),
                    teleportPos.getZ() + 0.5D,
                    player.getYRot(),
                    player.getXRot()
            );
            return teleportPos;
        }

        private BlockPos resolveTeleportPos(LocatedStructure locatedStructure) {
            ServerLevel level = locatedStructure.level();
            BoundingBox box = locatedStructure.box();
            int centerX = (box.minX() + box.maxX()) >> 1;
            int centerZ = (box.minZ() + box.maxZ()) >> 1;
            int minBuildY = level.getMinBuildHeight();
            int maxBuildY = level.getMaxBuildHeight() - 2;
            int baseY = Math.max(box.maxY() + 2, minBuildY + 1);
            List<BlockPos> candidates = new ArrayList<>();
            candidates.add(new BlockPos(centerX, baseY, centerZ));
            int[] offsets = new int[]{0, 4, -4, 8, -8, 12, -12};
            for (int offsetX : offsets) {
                for (int offsetZ : offsets) {
                    int x = centerX + offsetX;
                    int z = centerZ + offsetZ;
                    int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    int startY = Math.max(surfaceY + 1, baseY);
                    candidates.add(new BlockPos(x, Math.min(startY, maxBuildY), z));
                }
            }
            for (BlockPos candidate : candidates) {
                BlockPos safePos = findSafeStandingPos(level, candidate, minBuildY, maxBuildY);
                if (safePos != null) {
                    return safePos;
                }
            }
            return new BlockPos(centerX, Math.min(Math.max(baseY, minBuildY + 1), maxBuildY), centerZ);
        }

        private BlockPos findSafeStandingPos(ServerLevel level, BlockPos origin, int minBuildY, int maxBuildY) {
            int x = origin.getX();
            int z = origin.getZ();
            int startY = Math.min(Math.max(origin.getY(), minBuildY + 1), maxBuildY);
            for (int step = 0; step <= 20; step++) {
                int upY = startY + step;
                if (upY <= maxBuildY) {
                    BlockPos candidate = new BlockPos(x, upY, z);
                    if (isSafeStandingPos(level, candidate)) {
                        return candidate;
                    }
                }
                if (step == 0) {
                    continue;
                }
                int downY = startY - step;
                if (downY > minBuildY) {
                    BlockPos candidate = new BlockPos(x, downY, z);
                    if (isSafeStandingPos(level, candidate)) {
                        return candidate;
                    }
                }
            }
            return null;
        }

        private boolean isSafeStandingPos(ServerLevel level, BlockPos pos) {
            BlockPos above = pos.above();
            BlockPos below = pos.below();
            BlockState feetState = level.getBlockState(pos);
            BlockState headState = level.getBlockState(above);
            BlockState belowState = level.getBlockState(below);
            return feetState.getCollisionShape(level, pos).isEmpty()
                    && headState.getCollisionShape(level, above).isEmpty()
                    && !belowState.getCollisionShape(level, below).isEmpty();
        }

        private void restorePlayer(MinecraftServer server) {
            if (server == null) {
                return;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return;
            }
            ServerLevel level = server.getLevel(playerLevelKey);
            if (level == null) {
                return;
            }
            player.teleportTo(
                    level,
                    baseOrigin.getX() + 0.5D,
                    baseOrigin.getY(),
                    baseOrigin.getZ() + 0.5D,
                    baseYaw,
                    basePitch
            );
        }
    }

    private static final class AttemptContext {
        private final StructureAggregate aggregate;
        private ServerLevel level;
        private BoundingBox box;
        private List<ChunkPos> placeChunks = List.of();
        private List<BlockPos> lootPositions = List.of();
        private BlockPos locatePos;
        private BlockPos teleportPos = BlockPos.ZERO;
        private int pieceCount;
        private int mobWaitTicks;
        private int loadedChunkCount;
        private boolean chunkLoadingStarted;
        private boolean successful;

        private AttemptContext(String structureId, String structureType) {
            this.aggregate = new StructureAggregate(structureId, structureType);
        }

        private void finishLocate(LocatedStructure locatedStructure) {
            this.level = locatedStructure.level();
            this.box = locatedStructure.box();
            this.placeChunks = List.copyOf(locatedStructure.placeChunks());
            this.locatePos = locatedStructure.locatePos().immutable();
            this.pieceCount = locatedStructure.pieceCount();
        }
    }

    private static final class StructureAggregate {
        private final String structureId;
        private final String structureType;
        private final LinkedHashSet<String> mobIds = new LinkedHashSet<>();
        private final LinkedHashMap<String, LootBindingAggregate> lootBindings = new LinkedHashMap<>();

        private StructureAggregate(String structureId, String structureType) {
            this.structureId = structureId;
            this.structureType = structureType != null ? structureType : "";
        }

        private void recordLoot(String blockId, Set<String> storedItems, Set<String> lootItems, StructureIndexCache.LootTableDetail detail) {
            if (blockId == null || blockId.isBlank()) {
                return;
            }
            LootBindingAggregate aggregate = lootBindings.computeIfAbsent(blockId, LootBindingAggregate::new);
            if (storedItems != null) {
                aggregate.storedItems.addAll(storedItems);
            }
            if (lootItems != null) {
                aggregate.itemIds.addAll(lootItems);
            }
            if (detail != null && detail.lootTableId != null && !detail.lootTableId.isBlank()) {
                aggregate.addLootTable(detail);
            }
        }

        private int getMobCount() {
            return mobIds.size();
        }

        private int getLootBindingCount() {
            return lootBindings.size();
        }

        private int getLootTableCount() {
            int total = 0;
            for (LootBindingAggregate aggregate : lootBindings.values()) {
                total += aggregate.lootTables.size();
            }
            return total;
        }

        private int getLootItemCount() {
            int total = 0;
            for (LootBindingAggregate aggregate : lootBindings.values()) {
                total += aggregate.itemIds.size();
            }
            return total;
        }

        private List<JsonObject> toJsonObjects() {
            List<JsonObject> result = new ArrayList<>();
            for (LootBindingAggregate aggregate : lootBindings.values()) {
                result.add(aggregate.toJson(structureId));
            }
            return result;
        }
    }

    private static final class LootBindingAggregate {
        private final String blockId;
        private final LinkedHashSet<String> storedItems = new LinkedHashSet<>();
        private final LinkedHashSet<String> itemIds = new LinkedHashSet<>();
        private final LinkedHashMap<String, StructureIndexCache.LootTableDetail> lootTables = new LinkedHashMap<>();

        private LootBindingAggregate(String blockId) {
            this.blockId = blockId;
        }

        private void addLootTable(StructureIndexCache.LootTableDetail detail) {
            StructureIndexCache.LootTableDetail existing = lootTables.get(detail.lootTableId);
            if (existing == null) {
                StructureIndexCache.LootTableDetail copy = new StructureIndexCache.LootTableDetail();
                copy.lootTableId = detail.lootTableId;
                copy.entries.addAll(detail.entries);
                lootTables.put(copy.lootTableId, copy);
                return;
            }
            existing.entries.addAll(detail.entries);
        }

        private JsonObject toJson(String structureId) {
            JsonObject json = new JsonObject();
            json.addProperty("structure_id", structureId);
            json.addProperty("block_id", blockId);
            JsonArray lootTablesArray = new JsonArray();
            JsonArray lootTableDetails = new JsonArray();
            for (StructureIndexCache.LootTableDetail detail : lootTables.values()) {
                lootTablesArray.add(detail.lootTableId);
                JsonObject detailJson = new JsonObject();
                detailJson.addProperty("loot_table_id", detail.lootTableId);
                JsonArray entriesArray = new JsonArray();
                for (StructureIndexCache.LootItemEntry entry : detail.entries) {
                    JsonObject entryJson = new JsonObject();
                    entryJson.addProperty("item_id", entry.itemId);
                    entryJson.addProperty("weight", entry.weight);
                    entryJson.addProperty("quality", entry.quality);
                    entryJson.addProperty("rolls_text", entry.rollsText);
                    entryJson.addProperty("bonus_rolls_text", entry.bonusRollsText);
                    entryJson.addProperty("chance_text", entry.chanceText);
                    entryJson.addProperty("count_text", entry.countText);
                    entriesArray.add(entryJson);
                }
                detailJson.add("entries", entriesArray);
                lootTableDetails.add(detailJson);
            }
            json.add("loot_tables", lootTablesArray);
            json.add("loot_table_details", lootTableDetails);
            JsonArray storedItemsArray = new JsonArray();
            storedItems.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(storedItemsArray::add);
            json.add("stored_items", storedItemsArray);
            JsonArray itemsArray = new JsonArray();
            itemIds.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(itemsArray::add);
            json.add("items", itemsArray);
            return json;
        }
    }

    private static final class TimingStats {
        private long cooldownMillis;
        private long locateMillis;
        private long prepareMillis;
        private long loadChunksMillis;
        private long lootMillis;
        private long mobWaitMillis;
        private long mobScanMillis;
        private long clearMillis;
        private long writeMillis;

        private long otherMillis(long totalMillis) {
            return Math.max(totalMillis - cooldownMillis - locateMillis - prepareMillis - loadChunksMillis - lootMillis - mobWaitMillis - mobScanMillis - clearMillis - writeMillis, 0L);
        }
    }

    private record PrelocatedTarget(StructureTarget target, LocatedStructure locatedStructure) {
    }

    private record AsyncLocateRequest(
            UUID requestId,
            StructureTarget target,
            ServerLevel level,
            BlockPos searchOrigin,
            long startMillis,
            AsyncLocator.LocateTask<Pair<BlockPos, Holder<Structure>>> task
    ) {
    }

    private record LocatedStructure(
            ServerLevel level,
            BlockPos locatePos,
            StructureStart structureStart,
            BoundingBox box,
            List<ChunkPos> placeChunks,
            int pieceCount
    ) {
    }

    private static List<ResourceKey<Level>> collectOrderedDimensions(MinecraftServer server, List<StructureTarget> targets) {
        LinkedHashSet<ResourceKey<Level>> candidateDimensions = new LinkedHashSet<>();
        if (targets != null) {
            for (StructureTarget target : targets) {
                if (target == null || target.candidateLevels() == null) {
                    continue;
                }
                candidateDimensions.addAll(target.candidateLevels());
            }
        }
        LinkedHashSet<ResourceKey<Level>> ordered = new LinkedHashSet<>();
        addDimensionIfCandidate(server, candidateDimensions, ordered, Level.OVERWORLD);
        addDimensionIfCandidate(server, candidateDimensions, ordered, Level.NETHER);
        addDimensionIfCandidate(server, candidateDimensions, ordered, Level.END);
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                addDimensionIfCandidate(server, candidateDimensions, ordered, level.dimension());
            }
        }
        for (ResourceKey<Level> levelKey : candidateDimensions) {
            addDimensionIfCandidate(server, candidateDimensions, ordered, levelKey);
        }
        return List.copyOf(ordered);
    }

    private static void addDimensionIfCandidate(MinecraftServer server, Set<ResourceKey<Level>> candidates, LinkedHashSet<ResourceKey<Level>> ordered, ResourceKey<Level> levelKey) {
        if (levelKey == null || candidates == null || !candidates.contains(levelKey)) {
            return;
        }
        if (server != null && server.getLevel(levelKey) == null) {
            return;
        }
        ordered.add(levelKey);
    }

    private static String createRunId() {
        return LocalDateTime.now().format(RUN_ID_FORMAT) + "_" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000)).toLowerCase(Locale.ROOT);
    }

    private static String formatDuration(long totalSeconds) {
        long safeSeconds = Math.max(totalSeconds, 0L);
        long hours = safeSeconds / 3600L;
        long minutes = (safeSeconds % 3600L) / 60L;
        long seconds = safeSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static long toSeconds(long millis) {
        return Math.max(millis, 0L) / 1000L;
    }
}
