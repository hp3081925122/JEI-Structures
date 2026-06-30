package org.hp.jei_structures.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.hp.jei_structures.JeiStructures;
import org.hp.jei_structures.data.ItemStackSnapshotHelper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;

public final class DebugStructureCaptureManager {

    private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final int DEFAULT_LOCATE_RADIUS = 128;
    private static final int END_OUTER_ISLAND_OFFSET_X = 1200;
    private static final int BASE_COOLDOWN_TICKS = 5;
    private static final int PRELOAD_NEXT_STRUCTURE_CHUNKS_PER_TICK = 1;
    private static final int MAX_CONCURRENT_LOCATE_REQUESTS = 3;
    private static final long LOCATE_REQUEST_TIMEOUT_MILLIS = 20_000L;
    private static final ExecutorService LOCATE_EXECUTOR = Executors.newFixedThreadPool(
            MAX_CONCURRENT_LOCATE_REQUESTS,
            new ThreadFactory() {
                private int nextThreadId = 1;

                @Override
                public synchronized Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "jei-structures-quick-locate-" + nextThreadId++);
                    thread.setDaemon(true);
                    return thread;
                }
            }
    );

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
            JeiStructures.LOGGER.error("Structure debug capture failed while ticking", exception);
            try {
                activeSession.fail(exception);
            } catch (Exception nested) {
                JeiStructures.LOGGER.error("Structure debug capture failed while cleaning up", nested);
            } finally {
                activeSession = null;
                DebugStructureCaptureCoordinator.markQuickStopped();
            }
        }
    }

    public static synchronized void recordJoinedEntity(Entity entity, Level level) {
        if (activeSession == null) {
            return;
        }
        activeSession.recordJoinedEntity(entity, level);
    }

    private enum Phase {
        COOLDOWN("jei_structures.command.debug_capture.phase.cooldown"),
        LOCATE_DIMENSION("jei_structures.command.debug_capture.phase.locate_dimension"),
        PREPARE_CAPTURE("jei_structures.command.debug_capture.phase.prepare_capture"),
        LOAD_CHUNKS("jei_structures.command.debug_capture.phase.load_chunks"),
        SCAN_LOOT("jei_structures.command.debug_capture.phase.scan_loot"),
        WAIT_MOBS("jei_structures.command.debug_capture.phase.wait_mobs"),
        SCAN_MOBS("jei_structures.command.debug_capture.phase.scan_mobs"),
        CLEAR("jei_structures.command.debug_capture.phase.clear"),
        WRITE("jei_structures.command.debug_capture.phase.write");

        private final String translationKey;

        Phase(String translationKey) {
            this.translationKey = translationKey;
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
            this.lootResolver = new LootTableItemResolver(server.getResourceManager(), server.registryAccess().registryOrThrow(Registries.ITEM), server.registryAccess(), server.overworld());
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
                    currentPhase.translationKey,
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
                currentAttempt.loadedChunkCount = prelocatedTarget.preloadedChunkCount();
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
            int startIndex = currentAttempt.loadedChunkCount;
            int endIndex = Math.min(startIndex + JeiStructuresConfig.captureChunkLoadsPerTick(), totalChunks);
            for (int index = startIndex; index < endIndex; index++) {
                ChunkPos chunkPos = currentAttempt.placeChunks.get(index);
                level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true);
            }
            currentAttempt.loadedChunkCount = endIndex;
            long batchMillis = System.currentTimeMillis() - start;
            timingStats.loadChunksMillis += batchMillis;
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
            currentAttempt.mobWaitTicks = JeiStructuresConfig.captureMobWaitTicks();
            enterPhase(Phase.WAIT_MOBS);
            return false;
        }

        private boolean tickWaitMobs() {
            if (currentAttempt.mobWaitTicks > 0) {
                preloadNextQueuedStructureChunks();
                currentAttempt.mobWaitTicks--;
                return false;
            }
            enterPhase(Phase.SCAN_MOBS);
            return false;
        }

        private void preloadNextQueuedStructureChunks() {
            PrelocatedTarget prelocatedTarget = findNextPreloadTarget();
            if (prelocatedTarget == null) {
                return;
            }
            LocatedStructure locatedStructure = prelocatedTarget.locatedStructure();
            List<ChunkPos> chunks = locatedStructure.placeChunks();
            int totalChunks = chunks.size();
            if (prelocatedTarget.preloadedChunkCount() >= totalChunks) {
                return;
            }
            int endIndex = Math.min(prelocatedTarget.preloadedChunkCount() + PRELOAD_NEXT_STRUCTURE_CHUNKS_PER_TICK, totalChunks);
            for (int index = prelocatedTarget.preloadedChunkCount(); index < endIndex; index++) {
                ChunkPos chunkPos = chunks.get(index);
                locatedStructure.level().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true);
            }
            prelocatedTarget.setPreloadedChunkCount(endIndex);
        }

        private PrelocatedTarget findNextPreloadTarget() {
            for (PrelocatedTarget prelocatedTarget : locatedCaptureQueue) {
                if (prelocatedTarget == null || prelocatedTarget.target() == null) {
                    continue;
                }
                String structureId = prelocatedTarget.target().structureId().toString();
                if (completedStructureIds.contains(structureId) || failedStructureIds.contains(structureId)) {
                    continue;
                }
                return prelocatedTarget;
            }
            return null;
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
                    false
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
                JeiStructures.LOGGER.info(
                        "Structure debug capture completed: structure={}, dimension={}, collected={}/{}, dimensionProgress={}/{}, chunks={}, lootBlocks={}, lootTables={}, lootItems={}, mobs={}, elapsed={}",
                        currentTarget.structureId(),
                        currentAttempt.level.dimension().location(),
                        completedStructureIds.size(),
                        targets.size(),
                        currentDimensionCaptureCompleted,
                        currentDimensionCaptureTotal,
                        currentAttempt.loadedChunkCount,
                        currentAttempt.aggregate.getLootBindingCount(),
                        currentAttempt.aggregate.getLootTableCount(),
                        currentAttempt.aggregate.getLootItemCount(),
                        currentAttempt.aggregate.getMobCount(),
                        formatDuration(toSeconds(System.currentTimeMillis() - currentStructureStartMillis))
                );
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
                    currentDimensionLocateCompleted++;
                }
                sendDimensionLocateProgress(server);
                return;
            }
            while (activeLocateRequests.size() < MAX_CONCURRENT_LOCATE_REQUESTS && currentDimensionNextLocateIndex < currentDimensionTargets.size()) {
                StructureTarget target = currentDimensionTargets.get(currentDimensionNextLocateIndex++);
                if (completedStructureIds.contains(target.structureId().toString()) || failedStructureIds.contains(target.structureId().toString())) {
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
            AsyncLocateRequest request = new AsyncLocateRequest(
                    requestId,
                    target,
                    level,
                    locateOrigin,
                    System.currentTimeMillis(),
                    null
            );
            activeLocateRequests.put(requestId, request);
            Future<?> future = LOCATE_EXECUTOR.submit(() -> {
                Pair<BlockPos, Holder<Structure>> result = null;
                RuntimeException failure = null;
                try {
                    result = level.getChunkSource()
                            .getGenerator()
                            .findNearestMapStructure(level, structures, locateOrigin, DEFAULT_LOCATE_RADIUS, false);
                } catch (RuntimeException exception) {
                    failure = exception;
                }
                Pair<BlockPos, Holder<Structure>> finalResult = result;
                RuntimeException finalFailure = failure;
                server.execute(() -> acceptAsyncLocateResult(requestId, finalResult, finalFailure));
            });
            activeLocateRequests.put(requestId, request.withFuture(future));
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
                if (request.future() != null) {
                    request.future().cancel(true);
                }
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

        private void acceptAsyncLocateResult(UUID requestId, Pair<BlockPos, Holder<Structure>> result, RuntimeException failure) {
            if (requestId == null) {
                return;
            }
            AsyncLocateRequest request = activeLocateRequests.remove(requestId);
            if (request == null) {
                return;
            }
            MinecraftServer server = request.level().getServer();
            acceptLocateResult(server, request.level(), request.target(), result, failure);
            submitLocateRequests(server);
        }

        private void acceptLocateResult(MinecraftServer server, ServerLevel level, StructureTarget target, Pair<BlockPos, Holder<Structure>> result, RuntimeException failure) {
            currentDimensionLocateCompleted++;
            if (failure != null) {
                markDimensionLocateFailure(target, level.dimension());
                JeiStructures.LOGGER.warn("Structure debug locate failed: {} @ {}", target.structureId(), level.dimension().location(), failure);
                sendDimensionLocateProgress(server);
                return;
            }
            if (result == null || result.getFirst() == null) {
                markDimensionLocateFailure(target, level.dimension());
                sendDimensionLocateProgress(server);
                return;
            }
            LocatedStructure locatedStructure = buildLocatedStructureFromLocateCommand(level, target, result.getFirst().immutable());
            if (locatedStructure == null) {
                markDimensionLocateFailure(target, level.dimension());
                sendDimensionLocateProgress(server);
                return;
            }
            String structureId = target.structureId().toString();
            if (!completedStructureIds.contains(structureId) && !failedStructureIds.contains(structureId)) {
                locatedCaptureQueue.addLast(new PrelocatedTarget(target, locatedStructure));
                currentDimensionLocateSucceeded++;
                JeiStructures.LOGGER.info(
                        "Structure debug located: structure={}, dimension={}, pos=({}, {}, {}), located={}/{}, success={}, active={}, collected={}/{}",
                        target.structureId(),
                        level.dimension().location(),
                        result.getFirst().getX(),
                        result.getFirst().getY(),
                        result.getFirst().getZ(),
                        currentDimensionLocateCompleted,
                        currentDimensionTargets.size(),
                        currentDimensionLocateSucceeded,
                        activeLocateRequests.size(),
                        completedStructureIds.size(),
                        targets.size()
                );
            }
            sendDimensionLocateProgress(server);
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
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
                if (blockId == null) {
                    continue;
                }
                var nbt = blockEntity.saveWithId(currentAttempt.level.registryAccess());
                String lootTableId = DebugStructureCaptureScanning.readLootTable(nbt);
                List<StructureIndexCache.ItemStackSnapshot> storedItemStacks = StoredItemNbtReader.readStoredItemSnapshots(nbt, currentAttempt.level.registryAccess());
                LinkedHashSet<String> storedItems = new LinkedHashSet<>();
                for (StructureIndexCache.ItemStackSnapshot snapshot : storedItemStacks) {
                    String itemId = ItemStackSnapshotHelper.snapshotItemId(snapshot);
                    if (itemId != null && !itemId.isBlank()) {
                        storedItems.add(itemId);
                    }
                }
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
                if (storedItems.isEmpty() && storedItemStacks.isEmpty() && lootItems.isEmpty() && detail == null) {
                    continue;
                }
                currentAttempt.aggregate.recordLoot(blockId.toString(), storedItems, storedItemStacks, lootItems, detail);
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

        private void recordJoinedEntity(Entity entity, Level level) {
            if (!(entity instanceof LivingEntity livingEntity) || entity instanceof Player) {
                return;
            }
            if (level != currentAttemptLevel()) {
                return;
            }
            if (currentTarget == null || currentAttempt == null || currentAttempt.box == null) {
                return;
            }
            if (!isInsideCurrentBox(entity)) {
                return;
            }
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(livingEntity.getType());
            if (entityId == null) {
                return;
            }
            currentAttempt.aggregate.mobIds.add(entityId.toString());
        }

        private Level currentAttemptLevel() {
            return currentAttempt != null ? currentAttempt.level : null;
        }

        private boolean isInsideCurrentBox(Entity entity) {
            return entity.getX() >= currentAttempt.box.minX()
                    && entity.getX() < currentAttempt.box.maxX() + 1.0D
                    && entity.getY() >= currentAttempt.box.minY()
                    && entity.getY() < currentAttempt.box.maxY() + 1.0D
                    && entity.getZ() >= currentAttempt.box.minZ()
                    && entity.getZ() < currentAttempt.box.maxZ() + 1.0D;
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
                recordFailure(currentPhase.name(), exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName());
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
                    outputRoot.toString(),
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
                recordFailure(target, "locate_structure", "no_natural_structure_found_in_candidate_dimensions", levelKey, levelId, baseOrigin);
            }
        }

        private void cleanupSession() {
            if (cleanedUp) {
                return;
            }
            for (AsyncLocateRequest request : activeLocateRequests.values()) {
                if (request != null && request.future() != null) {
                    request.future().cancel(true);
                }
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
            phaseStartMillis = now;
        }

        private void addPhaseElapsed(Phase phase, long elapsed) {
            switch (phase) {
                case COOLDOWN -> timingStats.cooldownMillis += elapsed;
                case LOCATE_DIMENSION -> timingStats.locateMillis += elapsed;
                case PREPARE_CAPTURE -> timingStats.prepareMillis += elapsed;
                case LOAD_CHUNKS -> timingStats.loadChunksMillis += elapsed;
                case SCAN_LOOT -> timingStats.lootMillis += elapsed;
                case WAIT_MOBS -> timingStats.mobWaitMillis += elapsed;
                case SCAN_MOBS -> timingStats.mobScanMillis += elapsed;
                case CLEAR -> timingStats.clearMillis += elapsed;
                case WRITE -> timingStats.writeMillis += elapsed;
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
                    "Structure debug timing summary: total={}, cooldown={}, locate={}, prepare={}, chunks={}, loot={}, mobWait={}, mobScan={}, clear={}, write={}, other={}, slowLocate={}",
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
            MutableComponent message = Component.translatable(key, sanitizeTranslatableArgs(args));
            if (key != null && key.startsWith("jei_structures.command.debug_capture.progress.")) {
                return Component.translatable(
                        "jei_structures.command.debug_capture.progress.with_elapsed",
                        message,
                        formatDuration(toSeconds(System.currentTimeMillis() - sessionStartMillis))
                );
            }
            return message;
        }

        private Object[] sanitizeTranslatableArgs(Object[] args) {
            if (args == null || args.length == 0) {
                return new Object[0];
            }
            Object[] sanitized = new Object[args.length];
            for (int index = 0; index < args.length; index++) {
                Object arg = args[index];
                if (arg instanceof Component || arg instanceof Number || arg instanceof Boolean || arg instanceof String) {
                    sanitized[index] = arg;
                } else {
                    sanitized[index] = arg != null ? arg.toString() : "";
                }
            }
            return sanitized;
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
            teleportPlayerTo(locatedStructure.level(), teleportPos);
            return teleportPos;
        }

        private void teleportPlayerTo(ServerLevel level, BlockPos pos) {
            if (level == null || pos == null) {
                return;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return;
            }
            player.teleportTo(
                    level,
                    pos.getX() + 0.5D,
                    pos.getY(),
                    pos.getZ() + 0.5D,
                    player.getYRot(),
                    player.getXRot()
            );
        }

        private BlockPos resolveTeleportPos(LocatedStructure locatedStructure) {
            ServerLevel level = locatedStructure.level();
            BoundingBox box = locatedStructure.box();
            int centerX = (box.minX() + box.maxX()) >> 1;
            int centerY = (box.minY() + box.maxY()) >> 1;
            int centerZ = (box.minZ() + box.maxZ()) >> 1;
            int minBuildY = level.getMinBuildHeight();
            int maxBuildY = level.getMaxBuildHeight() - 2;
            return new BlockPos(centerX, Math.min(Math.max(centerY, minBuildY + 1), maxBuildY), centerZ);
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

        private void recordLoot(String blockId, Set<String> storedItems, List<StructureIndexCache.ItemStackSnapshot> storedItemStacks, Set<String> lootItems, StructureIndexCache.LootTableDetail detail) {
            if (blockId == null || blockId.isBlank()) {
                return;
            }
            LootBindingAggregate aggregate = lootBindings.computeIfAbsent(blockId, LootBindingAggregate::new);
            if (storedItems != null) {
                aggregate.storedItems.addAll(storedItems);
            }
            if (storedItemStacks != null) {
                for (StructureIndexCache.ItemStackSnapshot snapshot : storedItemStacks) {
                    aggregate.addStoredItemStack(snapshot);
                }
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
        private final LinkedHashMap<String, StructureIndexCache.ItemStackSnapshot> storedItemStacks = new LinkedHashMap<>();
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

        private void addStoredItemStack(StructureIndexCache.ItemStackSnapshot snapshot) {
            if (ItemStackSnapshotHelper.isEmptySnapshot(snapshot)) {
                return;
            }
            String key = (snapshot.itemId != null ? snapshot.itemId : "") + "|" + (snapshot.stackTag != null ? snapshot.stackTag : "");
            if (storedItemStacks.containsKey(key)) {
                return;
            }
            StructureIndexCache.ItemStackSnapshot copy = new StructureIndexCache.ItemStackSnapshot();
            copy.itemId = snapshot.itemId != null ? snapshot.itemId : "";
            copy.stackTag = snapshot.stackTag != null ? snapshot.stackTag : "";
            storedItemStacks.put(key, copy);
            if (!copy.itemId.isBlank()) {
                storedItems.add(copy.itemId);
            }
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
                    entryJson.addProperty("item_stack_tag", entry.itemStackTag);
                    entryJson.addProperty("weight", entry.weight);
                    entryJson.addProperty("quality", entry.quality);
                    entryJson.addProperty("rolls_text", entry.rollsText);
                    entryJson.addProperty("bonus_rolls_text", entry.bonusRollsText);
                    entryJson.addProperty("chance_text", entry.chanceText);
                    entryJson.addProperty("count_text", entry.countText);
                    entryJson.add("chance_notes", toLootTextJson(entry.chanceNotes));
                    entryJson.add("count_notes", toLootTextJson(entry.countNotes));
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
            JsonArray itemStacksArray = new JsonArray();
            for (StructureIndexCache.ItemStackSnapshot snapshot : storedItemStacks.values()) {
                JsonObject stackJson = new JsonObject();
                stackJson.addProperty("item_id", snapshot.itemId != null ? snapshot.itemId : "");
                stackJson.addProperty("stack_tag", snapshot.stackTag != null ? snapshot.stackTag : "");
                itemStacksArray.add(stackJson);
            }
            json.add("item_stacks", itemStacksArray);
            JsonArray itemsArray = new JsonArray();
            itemIds.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(itemsArray::add);
            json.add("items", itemsArray);
            return json;
        }

        private JsonArray toLootTextJson(List<StructureIndexCache.LootTextEntry> notes) {
            JsonArray array = new JsonArray();
            if (notes == null || notes.isEmpty()) {
                return array;
            }
            for (StructureIndexCache.LootTextEntry note : notes) {
                if (note == null || note.translationKey == null || note.translationKey.isBlank()) {
                    continue;
                }
                JsonObject json = new JsonObject();
                json.addProperty("translation_key", note.translationKey);
                JsonArray args = new JsonArray();
                if (note.args != null) {
                    note.args.forEach(args::add);
                }
                json.add("args", args);
                array.add(json);
            }
            return array;
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

    private static final class PrelocatedTarget {
        private final StructureTarget target;
        private final LocatedStructure locatedStructure;
        private int preloadedChunkCount;

        private PrelocatedTarget(StructureTarget target, LocatedStructure locatedStructure) {
            this.target = target;
            this.locatedStructure = locatedStructure;
        }

        private StructureTarget target() {
            return target;
        }

        private LocatedStructure locatedStructure() {
            return locatedStructure;
        }

        private int preloadedChunkCount() {
            return preloadedChunkCount;
        }

        private void setPreloadedChunkCount(int preloadedChunkCount) {
            this.preloadedChunkCount = preloadedChunkCount;
        }
    }

    private record AsyncLocateRequest(
            UUID requestId,
            StructureTarget target,
            ServerLevel level,
            BlockPos searchOrigin,
            long startMillis,
            Future<?> future
    ) {
        private AsyncLocateRequest withFuture(Future<?> future) {
            return new AsyncLocateRequest(requestId, target, level, searchOrigin, startMillis, future);
        }
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
