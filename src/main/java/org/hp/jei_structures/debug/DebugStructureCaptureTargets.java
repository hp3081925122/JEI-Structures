package org.hp.jei_structures.debug;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.hp.jei_structures.JeiStructures;
import org.hp.jei_structures.data.StructureBindingPaths;
import org.hp.jei_structures.data.StructureIndexCache;
import org.hp.jei_structures.data.StructureIndexCacheLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DebugStructureCaptureTargets {

    private static final int DEFAULT_CAPTURE_ATTEMPTS = 1;

    private DebugStructureCaptureTargets() {
    }

    public static List<StructureTarget> buildTargets(MinecraftServer server, ResourceLocation singleId, String namespace, ResourceLocation dimensionId, boolean skipReported, String excludedNamespace, ResourceLocation excludedDimensionId) {
        StructureIndexCache cache = StructureIndexCacheLoader.load();
        if (cache.structures == null || cache.structures.isEmpty()) {
            return List.of();
        }
        Registry<Structure> structureRegistry = server.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY);
        List<ResourceKey<Level>> orderedLevels = collectOrderedLevels(server);
        Comparator<ResourceKey<Level>> levelComparator = createLevelComparator(orderedLevels);
        List<StructureTarget> targets = new ArrayList<>();
        String normalizedNamespace = namespace != null && !namespace.isBlank() ? namespace.trim().toLowerCase(Locale.ROOT) : null;
        String normalizedExcludedNamespace = excludedNamespace != null && !excludedNamespace.isBlank() ? excludedNamespace.trim().toLowerCase(Locale.ROOT) : null;
        Set<String> reportedStructureIds = skipReported ? collectReportedStructureIds() : Set.of();
        for (StructureIndexCache.StructureEntry entry : cache.structures) {
            if (entry == null || entry.structureId == null || entry.structureId.isBlank()) {
                continue;
            }
            ResourceLocation structureId = ResourceLocation.tryParse(entry.structureId);
            if (structureId == null) {
                continue;
            }
            if (singleId != null && !singleId.equals(structureId)) {
                continue;
            }
            if (normalizedNamespace != null && !normalizedNamespace.equals(structureId.getNamespace().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (normalizedExcludedNamespace != null && normalizedExcludedNamespace.equals(structureId.getNamespace().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (skipReported && reportedStructureIds.contains(structureId.toString())) {
                continue;
            }
            Structure structure = structureRegistry.get(structureId);
            if (structure == null) {
                continue;
            }
            List<ResourceKey<Level>> candidateLevels = collectCandidateLevels(server, entry, dimensionId, excludedDimensionId, levelComparator);
            if (candidateLevels.isEmpty()) {
                JeiStructures.LOGGER.warn(
                        "Structure debug capture skipped structure because candidate dimensions are empty: {}, generationBiomeDimensions={}, dimensionId={}, excludedDimensionId={}",
                        structureId,
                        entry.generationBiomeDimensions != null && !entry.generationBiomeDimensions.isEmpty(),
                        dimensionId,
                        excludedDimensionId
                );
                continue;
            }
            targets.add(new StructureTarget(entry, structureId, structure, DEFAULT_CAPTURE_ATTEMPTS, candidateLevels, candidateLevels.get(0)));
        }
        targets.sort(Comparator
                .comparing(StructureTarget::primaryLevel, levelComparator)
                .thenComparing(target -> target.structureId().toString(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(targets);
    }

    private static Set<String> collectReportedStructureIds() {
        LinkedHashSet<String> structureIds = new LinkedHashSet<>();
        Path reportsRoot = StructureBindingPaths.getReportsRoot();
        if (!Files.isDirectory(reportsRoot)) {
            return Set.of();
        }
        try (var paths = Files.walk(reportsRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        String structureId = toReportedStructureId(reportsRoot, path);
                        if (structureId != null && !structureId.isBlank()) {
                            structureIds.add(structureId);
                        }
                    });
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to scan datapack report directory: {}", reportsRoot, exception);
        }
        return Set.copyOf(structureIds);
    }

    private static String toReportedStructureId(Path reportsRoot, Path file) {
        if (reportsRoot == null || file == null) {
            return null;
        }
        Path relativePath;
        try {
            relativePath = reportsRoot.relativize(file);
        } catch (Exception exception) {
            return null;
        }
        if (relativePath.getNameCount() < 4) {
            return null;
        }
        String groupDir = relativePath.getName(1).toString();
        if (!"structure_to_mobs".equals(groupDir) && !"structure_loot_bindings".equals(groupDir)) {
            return null;
        }
        String namespace = relativePath.getName(2).toString();
        String filename = relativePath.getFileName().toString();
        if (!filename.endsWith(".json")) {
            return null;
        }
        String path = filename.substring(0, filename.length() - 5).replace("__", "/");
        return namespace.isBlank() || path.isBlank() ? null : namespace + ":" + path;
    }

    private static List<ResourceKey<Level>> collectCandidateLevels(MinecraftServer server, StructureIndexCache.StructureEntry entry, ResourceLocation dimensionId, ResourceLocation excludedDimensionId, Comparator<ResourceKey<Level>> levelComparator) {
        LinkedHashSet<ResourceKey<Level>> candidateLevels = new LinkedHashSet<>();
        if (entry != null && entry.generationBiomeDimensions != null) {
            for (List<String> dimensionIds : entry.generationBiomeDimensions.values()) {
                if (dimensionIds == null) {
                    continue;
                }
                for (String candidateDimensionId : dimensionIds) {
                    ResourceLocation location = ResourceLocation.tryParse(candidateDimensionId);
                    if (location != null) {
                        ResourceKey<Level> levelKey = ResourceKey.create(Registry.DIMENSION_REGISTRY, location);
                        if (server.getLevel(levelKey) != null) {
                            candidateLevels.add(levelKey);
                        }
                    }
                }
            }
        }
        if (dimensionId != null) {
            ResourceKey<Level> requiredLevelKey = ResourceKey.create(Registry.DIMENSION_REGISTRY, dimensionId);
            candidateLevels.removeIf(levelKey -> !requiredLevelKey.equals(levelKey));
        }
        if (excludedDimensionId != null) {
            ResourceKey<Level> excludedLevelKey = ResourceKey.create(Registry.DIMENSION_REGISTRY, excludedDimensionId);
            candidateLevels.removeIf(excludedLevelKey::equals);
        }
        List<ResourceKey<Level>> sortedLevels = new ArrayList<>(candidateLevels);
        sortedLevels.sort(levelComparator);
        return List.copyOf(sortedLevels);
    }

    private static List<ResourceKey<Level>> collectOrderedLevels(MinecraftServer server) {
        LinkedHashSet<ResourceKey<Level>> orderedLevels = new LinkedHashSet<>();
        ResourceKey<Level> overworld = Level.OVERWORLD;
        ResourceKey<Level> nether = Level.NETHER;
        ResourceKey<Level> end = Level.END;
        if (server.getLevel(overworld) != null) {
            orderedLevels.add(overworld);
        }
        if (server.getLevel(nether) != null) {
            orderedLevels.add(nether);
        }
        if (server.getLevel(end) != null) {
            orderedLevels.add(end);
        }
        for (ServerLevel level : server.getAllLevels()) {
            orderedLevels.add(level.dimension());
        }
        return List.copyOf(orderedLevels);
    }

    private static Comparator<ResourceKey<Level>> createLevelComparator(List<ResourceKey<Level>> orderedLevels) {
        Map<ResourceKey<Level>, Integer> levelIndex = new HashMap<>();
        for (int index = 0; index < orderedLevels.size(); index++) {
            levelIndex.put(orderedLevels.get(index), index);
        }
        return Comparator
                .comparingInt((ResourceKey<Level> levelKey) -> levelIndex.getOrDefault(levelKey, Integer.MAX_VALUE))
                .thenComparing(levelKey -> levelKey.location().toString(), String.CASE_INSENSITIVE_ORDER);
    }

    public record StructureTarget(StructureIndexCache.StructureEntry entry, ResourceLocation structureId, Structure structure, int attemptCount, List<ResourceKey<Level>> candidateLevels, ResourceKey<Level> primaryLevel) {
    }
}
