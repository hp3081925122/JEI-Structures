package org.hp.jei_structures.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootTable;
import org.hp.jei_structures.JeiStructures;
import org.hp.jei_structures.data.ItemStackSnapshotHelper;
import org.hp.jei_structures.data.LootTableItemResolver;
import org.hp.jei_structures.data.StoredItemNbtReader;
import org.hp.jei_structures.data.StructureBlacklistData;
import org.hp.jei_structures.data.StructureBlacklistLoader;
import org.hp.jei_structures.data.StructureBindingData;
import org.hp.jei_structures.data.StructureBindingLoader;
import org.hp.jei_structures.data.StructureIndexCache;
import org.hp.jei_structures.data.StructureLootBinding;
import org.hp.jei_structures.data.StructureIndexPaths;
import org.hp.jei_structures.data.StructureSpecialInfoData;
import org.hp.jei_structures.data.StructureSpecialInfoLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class StructureIndexExporter {

    private static final Set<String> SUSPICIOUS_BLOCKS = Set.of("minecraft:suspicious_sand", "minecraft:suspicious_gravel");
    private static final TagKey<Block> SPECIAL_DISPLAY_BLOCKS_TAG = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(JeiStructures.MODID, "special_display_blocks"));

    private StructureIndexExporter() {
    }

    public static Path export(MinecraftServer server) throws Exception {
        ResourceManager resourceManager = server.getResourceManager();
        RegistryAccess registryAccess = server.registryAccess();
        Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);
        Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
        Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ServerLevel lootResolverLevel = server.overworld();
        if (lootResolverLevel == null) {
            lootResolverLevel = server.getAllLevels().iterator().next();
        }
        Map<String, List<String>> biomeDimensions = collectBiomeDimensions(server, biomeRegistry);
        StructureBindingData bindingData = StructureBindingLoader.loadAll(resourceManager);
        StructureBlacklistData blacklistData = StructureBlacklistLoader.loadAll(resourceManager);
        StructureSpecialInfoData specialInfoData = StructureSpecialInfoLoader.loadAll(resourceManager);
        JeiStructures.LOGGER.info("Starting structure index export. Registered structures: {}", structureRegistry.size());

        StructureIndexCache cache = new StructureIndexCache();
        cache.generatedAt = Instant.now().toString();

        LootTableItemResolver lootResolver = new LootTableItemResolver(resourceManager, itemRegistry, registryAccess, lootResolverLevel);
        List<StructureIndexCache.StructureEntry> entries = new ArrayList<>();
        int skippedCount = 0;

        for (ResourceLocation structureId : structureRegistry.keySet()) {
            try {
                Structure structure = structureRegistry.get(structureId);
                StructureIndexCache.StructureEntry entry = exportStructure(structureId, structure, resourceManager, lootResolver, biomeRegistry, biomeDimensions, bindingData, blacklistData, specialInfoData);
                if (entry != null) {
                    entries.add(entry);
                } else {
                    skippedCount++;
                    JeiStructures.LOGGER.debug("Structure skipped because no directly readable datapack definition was found: {}", structureId);
                }
            } catch (Exception exception) {
                JeiStructures.LOGGER.error("Failed while exporting structure: {}", structureId, exception);
                throw exception;
            }
        }

        entries.sort(Comparator.comparing(entry -> entry.structureId));
        cache.structures = entries;
        JeiStructures.LOGGER.info("Structure scan completed. Exportable: {}, skipped: {}", entries.size(), skippedCount);

        Path path = StructureIndexPaths.getCachePath();
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            StructureIndexCache.GSON.toJson(cache, writer);
        }
        JeiStructures.LOGGER.info("Structure index export completed. Output file: {}", path);
        return path;
    }

    private static StructureIndexCache.StructureEntry exportStructure(ResourceLocation structureId, Structure structure, ResourceManager resourceManager, LootTableItemResolver lootResolver, Registry<Biome> biomeRegistry, Map<String, List<String>> biomeDimensions, StructureBindingData bindingData, StructureBlacklistData blacklistData, StructureSpecialInfoData specialInfoData) {
        JsonObject structureJson = readJson(resourceManager, toStructureJsonLocation(structureId));
        if (structureJson == null) {
            return null;
        }

        StructureIndexCache.StructureEntry entry = new StructureIndexCache.StructureEntry();
        entry.structureId = structureId.toString();
        entry.structureType = getString(structureJson, "type");
        entry.generationStep = getGenerationStep(structureJson);
        entry.terrainAdjustment = getTerrainAdjustment(structureJson);
        entry.generationBiomes = collectGenerationBiomes(structureJson);
        entry.resolvedGenerationBiomes = resolveGenerationBiomes(entry.generationBiomes, biomeRegistry);
        entry.generationBiomeGroups = collectGenerationBiomeGroups(entry.generationBiomes, biomeRegistry);
        entry.generationBiomeDimensions = collectEntryBiomeDimensions(structureId, entry.resolvedGenerationBiomes, biomeDimensions);

        LinkedHashSet<String> templateIds = new LinkedHashSet<>();
        LinkedHashSet<String> spawnOverrideEntities = new LinkedHashSet<>();
        LinkedHashSet<String> templateEntities = new LinkedHashSet<>();
        LinkedHashSet<String> allMobEntityIds = new LinkedHashSet<>();

        collectStructureSpawns(structureJson, spawnOverrideEntities);
        allMobEntityIds.addAll(spawnOverrideEntities);

        ResourceLocation startPool = getResourceLocation(structureJson, "start_pool");
        if (startPool != null) {
            collectTemplatesFromPool(resourceManager, startPool, templateIds, new LinkedHashSet<>(), new LinkedHashSet<>());
        }
        JeiStructures.LOGGER.debug("Structure {} start pool: {}, template count: {}", structureId, startPool, templateIds.size());

        for (String templateId : templateIds) {
            TemplateScanResult result = scanTemplate(resourceManager, ResourceLocation.tryParse(templateId));
            if (result == null) {
                continue;
            }
            templateEntities.addAll(result.directEntities);
            allMobEntityIds.addAll(result.directEntities);
            entry.spawners.addAll(result.spawners);
            entry.containers.addAll(result.containers);
            entry.suspiciousBlocks.addAll(result.suspiciousBlocks);
            entry.specialDisplayBlocks = mergeLists(entry.specialDisplayBlocks, new ArrayList<>(result.specialDisplayBlocks));
        }
        if (structure != null) {
            LinkedHashSet<String> markerEntities = StructureCodeAnalyzer.collectStructureGeneratedEntities(structure);
            templateEntities.addAll(markerEntities);
            allMobEntityIds.addAll(markerEntities);
        }

        applyConfiguredMobBindings(structureId, allMobEntityIds, bindingData);
        applyConfiguredSpawnedEntityBindings(structureId, spawnOverrideEntities, templateEntities, bindingData);
        applyEntityAndBlockBlacklist(structureId, entry, spawnOverrideEntities, templateEntities, allMobEntityIds, blacklistData);
        applySpecialInfoBindings(entry, allMobEntityIds, specialInfoData);

        LinkedHashSet<String> entityLootItems = new LinkedHashSet<>();
        LinkedHashSet<String> mobEggItemIds = new LinkedHashSet<>();
        for (String entityId : allMobEntityIds) {
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(entityId));
            if (entityType == null) {
                continue;
            }
            ItemStack eggStack = findEgg(entityType);
            if (!eggStack.isEmpty()) {
                ResourceLocation eggId = BuiltInRegistries.ITEM.getKey(eggStack.getItem());
                if (eggId != null) {
                    mobEggItemIds.add(eggId.toString());
                }
            }
            ResourceKey<LootTable> lootTable = entityType.getDefaultLootTable();
            if (lootTable == null) {
                continue;
            }
            entityLootItems.addAll(lootResolver.resolveLootItems(lootTable.location()));
        }

        for (StructureIndexCache.LootBinding binding : entry.containers) {
            mergeResolvedLootItems(binding, lootResolver);
        }
        for (StructureIndexCache.LootBinding binding : entry.suspiciousBlocks) {
            mergeResolvedLootItems(binding, lootResolver);
        }

        applyConfiguredLootBindings(structureId, entry, lootResolver, bindingData, blacklistData);
        applyLootBlacklist(structureId, entry, blacklistData);

        entry.templateIds = new ArrayList<>(templateIds);
        entry.spawnOverridesEntities = new ArrayList<>(spawnOverrideEntities);
        entry.templateEntities = new ArrayList<>(templateEntities);
        entry.spawnedEntities = mergeOrdered(spawnOverrideEntities, templateEntities);
        entry.allMobEntityIds = new ArrayList<>(allMobEntityIds);
        entry.allMobEggItemIds = new ArrayList<>(mobEggItemIds);
        entry.entityLootItems = new ArrayList<>(entityLootItems);
        entry.containerLootItems = mergeLists(flattenItems(entry.containers), flattenItems(entry.manualLootBindings));
        entry.suspiciousLootItems = flattenItems(entry.suspiciousBlocks);
        entry.allEntityLootItemIds = new ArrayList<>(entityLootItems);
        entry.allContainerLootItemIds = new ArrayList<>(entry.containerLootItems);
        entry.allSuspiciousLootItemIds = new ArrayList<>(entry.suspiciousLootItems);
        entry.allLootItemIds = mergeOrdered(entityLootItems, new LinkedHashSet<>(entry.containerLootItems), new LinkedHashSet<>(entry.suspiciousLootItems));
        JeiStructures.LOGGER.debug(
                "Structure {} export finished. Templates: {}, mobs: {}, spawners: {}, containers: {}, suspicious blocks: {}",
                structureId,
                entry.templateIds.size(),
                entry.allMobEntityIds.size(),
                entry.spawners.size(),
                entry.containers.size(),
                entry.suspiciousBlocks.size()
        );
        return entry;
    }

    private static void applyConfiguredMobBindings(ResourceLocation structureId, LinkedHashSet<String> allMobEntityIds, StructureBindingData bindingData) {
        List<String> configuredEntityIds = bindingData.getStructureToMobs().get(structureId.toString());
        if (configuredEntityIds != null) {
            allMobEntityIds.addAll(configuredEntityIds);
        }
    }

    private static void applyConfiguredSpawnedEntityBindings(ResourceLocation structureId, LinkedHashSet<String> spawnOverrideEntities, LinkedHashSet<String> templateEntities, StructureBindingData bindingData) {
        List<String> configuredEntityIds = bindingData.getStructureToMobs().get(structureId.toString());
        if (configuredEntityIds != null) {
            spawnOverrideEntities.addAll(configuredEntityIds);
            templateEntities.addAll(configuredEntityIds);
        }
    }

    private static void applyConfiguredLootBindings(ResourceLocation structureId, StructureIndexCache.StructureEntry entry, LootTableItemResolver lootResolver, StructureBindingData bindingData, StructureBlacklistData blacklistData) {
        List<StructureLootBinding> bindings = bindingData.getStructureToLootBindings().get(structureId.toString());
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        for (StructureLootBinding binding : bindings) {
            StructureIndexCache.LootBinding lootBinding = new StructureIndexCache.LootBinding();
            lootBinding.templateId = "config:" + structureId;
            lootBinding.blockId = binding.blockId != null ? binding.blockId : "";
            lootBinding.lootTableId = binding.lootTables.isEmpty() ? "" : binding.lootTables.get(0);
            lootBinding.storedItemIds = new ArrayList<>(new LinkedHashSet<>(binding.items));
            lootBinding.storedItemStacks = copyItemStackSnapshots(binding.itemStacks);
            if (isLootBindingContainerBlocked(structureId, lootBinding, blacklistData)) {
                continue;
            }
            LinkedHashSet<String> itemIds = new LinkedHashSet<>(lootBinding.storedItemIds);
            mergeSnapshotItemIds(itemIds, lootBinding.storedItemStacks);
            for (String lootTableId : binding.lootTables) {
                if (blacklistData != null && blacklistData.isLootTableBlocked(structureId.toString(), lootTableId)) {
                    continue;
                }
                StructureIndexCache.LootTableDetail detail = buildLootTableDetail(lootTableId, lootResolver);
                if (detail != null) {
                    lootBinding.lootTables.add(detail);
                }
                itemIds.addAll(lootResolver.resolveLootItems(ResourceLocation.tryParse(lootTableId)));
            }
            if (lootBinding.lootTableId != null && blacklistData != null && blacklistData.isLootTableBlocked(structureId.toString(), lootBinding.lootTableId)) {
                lootBinding.lootTableId = lootBinding.lootTables.isEmpty() ? "" : lootBinding.lootTables.get(0).lootTableId;
            }
            lootBinding.itemIds = new ArrayList<>(itemIds);
            if (lootBinding.itemIds.isEmpty() && lootBinding.lootTables.isEmpty()) {
                continue;
            }
            if (!lootBinding.blockId.isBlank()) {
                entry.containers.add(lootBinding);
            } else {
                entry.manualLootBindings.add(lootBinding);
            }
        }
    }

    private static void applyEntityAndBlockBlacklist(ResourceLocation structureId, StructureIndexCache.StructureEntry entry, LinkedHashSet<String> spawnOverrideEntities, LinkedHashSet<String> templateEntities, LinkedHashSet<String> allMobEntityIds, StructureBlacklistData blacklistData) {
        if (structureId == null || blacklistData == null) {
            return;
        }
        String id = structureId.toString();
        spawnOverrideEntities.removeIf(entityId -> blacklistData.isEntityBlocked(id, entityId));
        templateEntities.removeIf(entityId -> blacklistData.isEntityBlocked(id, entityId));
        allMobEntityIds.removeIf(entityId -> blacklistData.isEntityBlocked(id, entityId));
        entry.spawners.removeIf(spawner -> spawner == null || blacklistData.isEntityBlocked(id, spawner.entityId));
        entry.specialDisplayBlocks.removeIf(blockId -> blacklistData.isBlockBlocked(id, blockId));
    }

    private static void applyLootBlacklist(ResourceLocation structureId, StructureIndexCache.StructureEntry entry, StructureBlacklistData blacklistData) {
        if (structureId == null || entry == null || blacklistData == null) {
            return;
        }
        filterLootBindings(structureId, entry.containers, blacklistData, true);
        filterLootBindings(structureId, entry.suspiciousBlocks, blacklistData, true);
        filterLootBindings(structureId, entry.manualLootBindings, blacklistData, false);
    }

    private static void filterLootBindings(ResourceLocation structureId, List<StructureIndexCache.LootBinding> bindings, StructureBlacklistData blacklistData, boolean checkContainer) {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        String id = structureId.toString();
        bindings.removeIf(binding -> {
            if (binding == null) {
                return true;
            }
            if (checkContainer && isLootBindingContainerBlocked(structureId, binding, blacklistData)) {
                return true;
            }
            filterLootTables(id, binding, blacklistData);
            return binding.storedItemIds.isEmpty() && binding.lootTables.isEmpty() && binding.itemIds.isEmpty();
        });
    }

    private static void filterLootTables(String structureId, StructureIndexCache.LootBinding binding, StructureBlacklistData blacklistData) {
        if (binding == null || blacklistData == null) {
            return;
        }
        if (binding.lootTableId != null && !binding.lootTableId.isBlank() && blacklistData.isLootTableBlocked(structureId, binding.lootTableId)) {
            binding.lootTableId = "";
        }
        if (binding.lootTables != null) {
            binding.lootTables.removeIf(detail -> detail == null || blacklistData.isLootTableBlocked(structureId, detail.lootTableId));
        }
        if (binding.lootTableId == null || binding.lootTableId.isBlank()) {
            binding.lootTableId = binding.lootTables == null || binding.lootTables.isEmpty() ? "" : binding.lootTables.get(0).lootTableId;
        }
        LinkedHashSet<String> itemIds = new LinkedHashSet<>();
        if (binding.storedItemIds != null) {
            itemIds.addAll(binding.storedItemIds);
        }
        mergeSnapshotItemIds(itemIds, binding.storedItemStacks);
        if (binding.lootTables != null) {
            for (StructureIndexCache.LootTableDetail detail : binding.lootTables) {
                if (detail == null || detail.entries == null) {
                    continue;
                }
                for (StructureIndexCache.LootItemEntry entry : detail.entries) {
                    if (entry != null && entry.itemId != null && !entry.itemId.isBlank()) {
                        itemIds.add(entry.itemId);
                    }
                }
            }
        }
        binding.itemIds = new ArrayList<>(itemIds);
    }

    private static boolean isLootBindingContainerBlocked(ResourceLocation structureId, StructureIndexCache.LootBinding binding, StructureBlacklistData blacklistData) {
        return structureId != null
                && binding != null
                && binding.blockId != null
                && !binding.blockId.isBlank()
                && blacklistData != null
                && blacklistData.isContainerBlocked(structureId.toString(), binding.blockId);
    }

    private static void mergeResolvedLootItems(StructureIndexCache.LootBinding binding, LootTableItemResolver lootResolver) {
        if (binding == null) {
            return;
        }
        LinkedHashSet<String> itemIds = new LinkedHashSet<>(binding.storedItemIds);
        mergeSnapshotItemIds(itemIds, binding.storedItemStacks);
        if (binding.lootTableId != null && !binding.lootTableId.isBlank()) {
            if (binding.lootTables.isEmpty()) {
                StructureIndexCache.LootTableDetail detail = buildLootTableDetail(binding.lootTableId, lootResolver);
                if (detail != null) {
                    binding.lootTables.add(detail);
                }
            }
            itemIds.addAll(lootResolver.resolveLootItems(ResourceLocation.tryParse(binding.lootTableId)));
        }
        binding.itemIds = new ArrayList<>(itemIds);
    }

    private static void applySpecialInfoBindings(StructureIndexCache.StructureEntry entry, Set<String> allMobEntityIds, StructureSpecialInfoData specialInfoData) {
        if (entry == null || specialInfoData == null) {
            return;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String blockId : entry.specialDisplayBlocks) {
            String translationKey = specialInfoData.getBlockTranslations().get(blockId);
            if (translationKey == null || translationKey.isBlank()) {
                continue;
            }
            String marker = "block:" + blockId;
            if (!seen.add(marker)) {
                continue;
            }
            StructureIndexCache.SpecialInfoEntry infoEntry = new StructureIndexCache.SpecialInfoEntry();
            infoEntry.targetType = "block";
            infoEntry.targetId = blockId;
            infoEntry.translationKey = translationKey;
            entry.specialInfos.add(infoEntry);
        }
        if (allMobEntityIds == null) {
            return;
        }
        allMobEntityIds.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(entityId -> {
                    String translationKey = specialInfoData.getEntityTranslations().get(entityId);
                    if (translationKey == null || translationKey.isBlank()) {
                        return;
                    }
                    String marker = "entity:" + entityId;
                    if (!seen.add(marker)) {
                        return;
                    }
                    StructureIndexCache.SpecialInfoEntry infoEntry = new StructureIndexCache.SpecialInfoEntry();
                    infoEntry.targetType = "entity";
                    infoEntry.targetId = entityId;
                    infoEntry.translationKey = translationKey;
                    entry.specialInfos.add(infoEntry);
                });
    }

    private static StructureIndexCache.LootTableDetail buildLootTableDetail(String lootTableId, LootTableItemResolver lootResolver) {
        if (lootTableId == null || lootTableId.isBlank() || lootResolver == null) {
            return null;
        }
        return lootResolver.resolveLootTableDetail(ResourceLocation.tryParse(lootTableId));
    }

    private static String getGenerationStep(JsonObject structureJson) {
        String step = getString(structureJson, "step");
        if (!step.isBlank()) {
            return step;
        }
        JsonObject placement = getObject(structureJson, "placement");
        if (placement != null) {
            return getString(placement, "step");
        }
        return "";
    }

    private static String getTerrainAdjustment(JsonObject structureJson) {
        String terrainAdjustment = getString(structureJson, "terrain_adaptation");
        if (!terrainAdjustment.isBlank()) {
            return terrainAdjustment;
        }
        JsonObject placement = getObject(structureJson, "placement");
        if (placement != null) {
            return getString(placement, "terrain_adaptation");
        }
        return "";
    }

    private static List<String> collectGenerationBiomes(JsonObject structureJson) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        JsonElement biomes = structureJson.get("biomes");
        collectBiomeElement(biomes, result);
        JsonObject placement = getObject(structureJson, "placement");
        if (placement != null) {
            collectBiomeElement(placement.get("biomes"), result);
        }
        return new ArrayList<>(result);
    }

    private static List<String> resolveGenerationBiomes(List<String> selectors, Registry<Biome> biomeRegistry) {
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        for (String selector : selectors) {
            collectResolvedBiomeSelector(selector, biomeRegistry, resolved);
        }
        return resolved.stream()
                .sorted(Comparator
                        .comparing(StructureIndexExporter::getBiomeDisplayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(String::compareToIgnoreCase))
                .toList();
    }

    private static List<StructureIndexCache.GenerationBiomeGroup> collectGenerationBiomeGroups(List<String> selectors, Registry<Biome> biomeRegistry) {
        if (selectors == null || selectors.isEmpty()) {
            return List.of();
        }
        List<StructureIndexCache.GenerationBiomeGroup> groups = new ArrayList<>();
        for (String selector : selectors) {
            if (selector == null || selector.isBlank()) {
                continue;
            }
            LinkedHashSet<String> resolved = new LinkedHashSet<>();
            collectResolvedBiomeSelector(selector, biomeRegistry, resolved);
            StructureIndexCache.GenerationBiomeGroup group = new StructureIndexCache.GenerationBiomeGroup();
            group.selector = selector;
            group.selectorType = selector.startsWith("#") ? "tag" : "biome";
            group.resolvedBiomeIds = resolved.stream()
                    .sorted(Comparator
                            .comparing(StructureIndexExporter::getBiomeDisplayName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(String::compareToIgnoreCase))
                    .toList();
            groups.add(group);
        }
        return groups;
    }

    private static Map<String, List<String>> collectBiomeDimensions(MinecraftServer server, Registry<Biome> biomeRegistry) {
        Map<String, LinkedHashSet<String>> dimensionsByBiome = new HashMap<>();
        for (ServerLevel level : server.forgeGetWorldMap().values()) {
            ResourceLocation dimensionId = level.dimension().location();
            for (var holder : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
                ResourceLocation biomeId = biomeRegistry.getKey(holder.value());
                if (biomeId == null) {
                    continue;
                }
                dimensionsByBiome.computeIfAbsent(biomeId.toString(), key -> new LinkedHashSet<>()).add(dimensionId.toString());
            }
        }
        for (var entry : biomeRegistry.entrySet()) {
            ResourceLocation biomeId = entry.getKey().location();
            if (biomeId == null) {
                continue;
            }
            LinkedHashSet<String> dimensionIds = dimensionsByBiome.computeIfAbsent(biomeId.toString(), key -> new LinkedHashSet<>());
            if (dimensionIds.contains(Level.OVERWORLD.location().toString())) {
                continue;
            }
            Biome biome = entry.getValue();
            if (biome == null) {
                continue;
            }
            var holder = biomeRegistry.wrapAsHolder(biome);
            if (holder.is(BiomeTags.IS_OVERWORLD)) {
                dimensionIds.add(Level.OVERWORLD.location().toString());
            }
        }
        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : dimensionsByBiome.entrySet()) {
            result.put(entry.getKey(), entry.getValue().stream()
                    .sorted(Comparator
                            .comparing(StructureIndexExporter::getDimensionDisplayName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(String::compareToIgnoreCase))
                    .toList());
        }
        return result;
    }

    private static Map<String, List<String>> collectEntryBiomeDimensions(ResourceLocation structureId, List<String> biomeIds, Map<String, List<String>> biomeDimensions) {
        Map<String, List<String>> result = new HashMap<>();
        for (String biomeId : biomeIds) {
            List<String> dimensionIds = biomeDimensions.get(biomeId);
            if (dimensionIds != null && !dimensionIds.isEmpty()) {
                result.put(biomeId, List.copyOf(dimensionIds));
            }
        }
        return result;
    }

    private static void collectResolvedBiomeSelector(String selector, Registry<Biome> biomeRegistry, Set<String> resolved) {
        if (selector == null || selector.isBlank()) {
            return;
        }
        if (selector.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(selector.substring(1));
            if (tagId == null) {
                return;
            }
            TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, tagId);
            Optional<HolderSet.Named<Biome>> tag = biomeRegistry.getTag(tagKey);
            if (tag.isEmpty()) {
                return;
            }
            for (var holder : tag.get()) {
                ResourceLocation biomeId = biomeRegistry.getKey(holder.value());
                if (biomeId != null) {
                    resolved.add(biomeId.toString());
                }
            }
            return;
        }
        ResourceLocation biomeId = ResourceLocation.tryParse(selector);
        if (biomeId != null && biomeRegistry.containsKey(biomeId)) {
            resolved.add(biomeId.toString());
        }
    }

    private static String getBiomeDisplayName(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return "";
        }
        ResourceLocation id = ResourceLocation.tryParse(biomeId);
        if (id == null) {
            return biomeId;
        }
        String translationKey = "biome." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return net.minecraft.network.chat.Component.translatable(translationKey).getString();
    }

    private static String getDimensionDisplayName(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return "";
        }
        ResourceLocation id = ResourceLocation.tryParse(dimensionId);
        if (id == null) {
            return dimensionId;
        }
        String translationKey = "dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return net.minecraft.network.chat.Component.translatable(translationKey).getString();
    }

    private static void collectBiomeElement(JsonElement element, Set<String> result) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            if (!value.isBlank()) {
                result.add(value);
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectBiomeElement(child, result);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("biomes")) {
            collectBiomeElement(object.get("biomes"), result);
        }
        if (object.has("values")) {
            collectBiomeElement(object.get("values"), result);
        }
        String tag = getString(object, "tag");
        if (!tag.isBlank()) {
            result.add("#" + tag);
        }
        String id = getString(object, "id");
        if (!id.isBlank()) {
            result.add(id);
        }
    }

    private static ItemStack findEgg(EntityType<?> entityType) {
        SpawnEggItem spawnEggItem = SpawnEggItem.byId(entityType);
        if (spawnEggItem != null) {
            return new ItemStack(spawnEggItem);
        }
        return new ItemStack(Items.AIR);
    }

    private static List<String> flattenItems(List<StructureIndexCache.LootBinding> bindings) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (StructureIndexCache.LootBinding binding : bindings) {
            result.addAll(binding.itemIds);
        }
        return new ArrayList<>(result);
    }

    private static List<String> mergeLists(List<String> first, List<String> second) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        return new ArrayList<>(merged);
    }

    private static void mergeSnapshotItemIds(Set<String> output, List<StructureIndexCache.ItemStackSnapshot> snapshots) {
        if (output == null || snapshots == null || snapshots.isEmpty()) {
            return;
        }
        for (StructureIndexCache.ItemStackSnapshot snapshot : snapshots) {
            String itemId = ItemStackSnapshotHelper.snapshotItemId(snapshot);
            if (itemId != null && !itemId.isBlank()) {
                output.add(itemId);
            }
        }
    }

    private static List<StructureIndexCache.ItemStackSnapshot> copyItemStackSnapshots(List<StructureIndexCache.ItemStackSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return new ArrayList<>();
        }
        List<StructureIndexCache.ItemStackSnapshot> copies = new ArrayList<>(snapshots.size());
        for (StructureIndexCache.ItemStackSnapshot snapshot : snapshots) {
            if (ItemStackSnapshotHelper.isEmptySnapshot(snapshot)) {
                continue;
            }
            StructureIndexCache.ItemStackSnapshot copy = new StructureIndexCache.ItemStackSnapshot();
            copy.itemId = snapshot.itemId != null ? snapshot.itemId : "";
            copy.stackTag = snapshot.stackTag != null ? snapshot.stackTag : "";
            copies.add(copy);
        }
        return copies;
    }

    @SafeVarargs
    private static List<String> mergeOrdered(Set<String>... sets) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Set<String> set : sets) {
            result.addAll(set);
        }
        return new ArrayList<>(result);
    }

    private static void collectStructureSpawns(JsonObject structureJson, Set<String> entityIds) {
        JsonObject spawnOverrides = getObject(structureJson, "spawn_overrides");
        if (spawnOverrides == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : spawnOverrides.entrySet()) {
            JsonObject override = entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject() : null;
            if (override == null) {
                continue;
            }
            JsonArray spawns = getArray(override, "spawns");
            if (spawns == null) {
                continue;
            }
            for (JsonElement spawnElement : spawns) {
                if (!spawnElement.isJsonObject()) {
                    continue;
                }
                ResourceLocation entityId = getResourceLocation(spawnElement.getAsJsonObject(), "type");
                if (entityId != null) {
                    entityIds.add(entityId.toString());
                }
            }
        }
    }

    private static void collectTemplatesFromPool(ResourceManager resourceManager, ResourceLocation poolId, Set<String> templateIds, Set<String> visitedPools, Set<String> scannedTemplates) {
        if (!visitedPools.add(poolId.toString())) {
            return;
        }
        JsonObject poolJson = readJson(resourceManager, toPoolJsonLocation(poolId));
        if (poolJson == null) {
            return;
        }
        JsonArray elements = getArray(poolJson, "elements");
        if (elements == null) {
            return;
        }
        for (JsonElement element : elements) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject weighted = element.getAsJsonObject();
            JsonObject poolElement = getObject(weighted, "element");
            if (poolElement != null) {
                collectTemplatesFromPoolElement(resourceManager, poolElement, templateIds, visitedPools, scannedTemplates);
            }
        }
    }

    private static void collectTemplatesFromPoolElement(ResourceManager resourceManager, JsonObject element, Set<String> templateIds, Set<String> visitedPools, Set<String> scannedTemplates) {
        String elementType = getString(element, "element_type");
        if ("minecraft:single_pool_element".equals(elementType) || "minecraft:legacy_single_pool_element".equals(elementType)) {
            ResourceLocation location = getResourceLocation(element, "location");
            if (location != null && !"minecraft:empty".equals(location.toString())) {
                templateIds.add(location.toString());
                collectChildPoolsFromTemplate(resourceManager, location, templateIds, visitedPools, scannedTemplates);
            }
            return;
        }
        if ("minecraft:list_pool_element".equals(elementType)) {
            JsonArray children = getArray(element, "elements");
            if (children == null) {
                return;
            }
            for (JsonElement child : children) {
                if (child.isJsonObject()) {
                    collectTemplatesFromPoolElement(resourceManager, child.getAsJsonObject(), templateIds, visitedPools, scannedTemplates);
                }
            }
            return;
        }
        ResourceLocation projectionPool = getResourceLocation(element, "pool");
        if (projectionPool != null) {
            collectTemplatesFromPool(resourceManager, projectionPool, templateIds, visitedPools, scannedTemplates);
        }
    }

    private static void collectChildPoolsFromTemplate(ResourceManager resourceManager, ResourceLocation templateId, Set<String> templateIds, Set<String> visitedPools, Set<String> scannedTemplates) {
        if (templateId == null || !scannedTemplates.add(templateId.toString())) {
            return;
        }
        CompoundTag root = readTemplateRoot(resourceManager, templateId);
        if (root == null) {
            return;
        }
        List<String> palette = readTemplatePalette(root);
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            int stateIndex = block.getInt("state");
            String blockId = stateIndex >= 0 && stateIndex < palette.size() ? palette.get(stateIndex) : "";
            if (!"minecraft:jigsaw".equals(blockId) || !block.contains("nbt", Tag.TAG_COMPOUND)) {
                continue;
            }
            ResourceLocation childPool = getNbtResourceLocation(block.getCompound("nbt"), "pool");
            if (childPool != null) {
                collectTemplatesFromPool(resourceManager, childPool, templateIds, visitedPools, scannedTemplates);
            }
        }
    }

    private static TemplateScanResult scanTemplate(ResourceManager resourceManager, ResourceLocation templateId) {
        if (templateId == null) {
            return null;
        }
        CompoundTag root = readTemplateRoot(resourceManager, templateId);
        if (root == null) {
            return null;
        }

        return parseTemplate(templateId, root);
    }

    private static CompoundTag readTemplateRoot(ResourceManager resourceManager, ResourceLocation templateId) {
        Optional<Resource> resource = resourceManager.getResource(toTemplateLocation(templateId));
        if (resource.isEmpty()) {
            resource = resourceManager.getResource(toLegacyTemplateLocation(templateId));
        }
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream inputStream = resource.get().open()) {
            return NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap());
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to read structure template: {}", templateId, exception);
            return null;
        }
    }

    private static TemplateScanResult parseTemplate(ResourceLocation templateId, CompoundTag root) {
        TemplateScanResult result = new TemplateScanResult();
        List<String> palette = readTemplatePalette(root);

        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            int stateIndex = block.getInt("state");
            String blockId = stateIndex >= 0 && stateIndex < palette.size() ? palette.get(stateIndex) : "";
            addSpecialDisplayBlock(blockId, result);
            CompoundTag blockEntity = block.contains("nbt", Tag.TAG_COMPOUND) ? block.getCompound("nbt") : null;
            if (blockId.isEmpty() || blockEntity == null) {
                continue;
            }
            addSpawner(templateId, blockId, blockEntity, result);
            addLootBinding(templateId, blockId, blockEntity, result);
        }

        ListTag entities = root.getList("entities", Tag.TAG_COMPOUND);
        for (int index = 0; index < entities.size(); index++) {
            CompoundTag entityEntry = entities.getCompound(index);
            if (!entityEntry.contains("nbt", Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag entityNbt = entityEntry.getCompound("nbt");
            String entityId = entityNbt.getString("id");
            if (!entityId.isBlank()) {
                result.directEntities.add(entityId);
            }
        }

        return result;
    }

    private static void addSpecialDisplayBlock(String blockId, TemplateScanResult result) {
        if (blockId == null || blockId.isBlank() || result == null) {
            return;
        }
        ResourceLocation blockKey = ResourceLocation.tryParse(blockId);
        if (blockKey == null) {
            return;
        }
        Block block = BuiltInRegistries.BLOCK.get(blockKey);
        if (block != null && block.defaultBlockState().is(SPECIAL_DISPLAY_BLOCKS_TAG)) {
            result.specialDisplayBlocks.add(blockId);
        }
    }

    private static void addSpawner(ResourceLocation templateId, String blockId, CompoundTag blockEntity, TemplateScanResult result) {
        if (!"minecraft:spawner".equals(blockId)) {
            return;
        }
        String entityId = findSpawnerEntity(blockEntity);
        if (entityId.isBlank()) {
            return;
        }
        StructureIndexCache.SpawnerEntry spawnerEntry = new StructureIndexCache.SpawnerEntry();
        spawnerEntry.templateId = templateId.toString();
        spawnerEntry.entityId = entityId;
        result.spawners.add(spawnerEntry);
        result.directEntities.add(entityId);
    }

    private static void addLootBinding(ResourceLocation templateId, String blockId, CompoundTag blockEntity, TemplateScanResult result) {
        String lootTable = findLootTable(blockEntity);
        List<StructureIndexCache.ItemStackSnapshot> storedItemStacks = StoredItemNbtReader.readStoredItemSnapshots(blockEntity, null);
        LinkedHashSet<String> storedItems = new LinkedHashSet<>();
        mergeSnapshotItemIds(storedItems, storedItemStacks);
        if (lootTable.isBlank() && storedItems.isEmpty() && storedItemStacks.isEmpty()) {
            return;
        }
        StructureIndexCache.LootBinding binding = new StructureIndexCache.LootBinding();
        binding.templateId = templateId.toString();
        binding.blockId = blockId;
        binding.lootTableId = lootTable;
        binding.storedItemIds = new ArrayList<>(storedItems);
        binding.storedItemStacks = copyItemStackSnapshots(storedItemStacks);
        binding.itemIds = new ArrayList<>(storedItems);
        if (SUSPICIOUS_BLOCKS.contains(blockId)) {
            result.suspiciousBlocks.add(binding);
        } else {
            result.containers.add(binding);
        }
    }

    private static String findSpawnerEntity(CompoundTag blockEntity) {
        if (blockEntity.contains("SpawnData", Tag.TAG_COMPOUND)) {
            CompoundTag spawnData = blockEntity.getCompound("SpawnData");
            if (spawnData.contains("entity", Tag.TAG_COMPOUND)) {
                String entityId = spawnData.getCompound("entity").getString("id");
                if (!entityId.isBlank()) {
                    return entityId;
                }
            }
            if (spawnData.contains("Entity", Tag.TAG_COMPOUND)) {
                String entityId = spawnData.getCompound("Entity").getString("id");
                if (!entityId.isBlank()) {
                    return entityId;
                }
            }
        }
        if (blockEntity.contains("SpawnPotentials", Tag.TAG_LIST)) {
            ListTag spawnPotentials = blockEntity.getList("SpawnPotentials", Tag.TAG_COMPOUND);
            for (int index = 0; index < spawnPotentials.size(); index++) {
                CompoundTag potential = spawnPotentials.getCompound(index);
                if (potential.contains("data", Tag.TAG_COMPOUND)) {
                    String entityId = potential.getCompound("data").getCompound("entity").getString("id");
                    if (!entityId.isBlank()) {
                        return entityId;
                    }
                }
            }
        }
        return "";
    }

    private static String findLootTable(CompoundTag blockEntity) {
        if (blockEntity.contains("LootTable", Tag.TAG_STRING)) {
            return blockEntity.getString("LootTable");
        }
        if (blockEntity.contains("loot_table", Tag.TAG_STRING)) {
            return blockEntity.getString("loot_table");
        }
        return "";
    }

    private static JsonObject readJson(ResourceManager resourceManager, ResourceLocation location) {
        try {
            Optional<Resource> resource = resourceManager.getResource(location);
            if (resource.isEmpty()) {
                return null;
            }
            try (Reader reader = resource.get().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                return json != null && json.isJsonObject() ? json.getAsJsonObject() : null;
            }
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to read JSON resource: {}", location, exception);
            return null;
        }
    }

    private static ResourceLocation toStructureJsonLocation(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "worldgen/structure/" + id.getPath() + ".json");
    }

    private static ResourceLocation toPoolJsonLocation(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "worldgen/template_pool/" + id.getPath() + ".json");
    }

    private static ResourceLocation toTemplateLocation(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt");
    }

    private static ResourceLocation toLegacyTemplateLocation(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "structures/" + id.getPath() + ".nbt");
    }

    private static List<String> readTemplatePalette(CompoundTag root) {
        List<String> palette = new ArrayList<>();
        ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        for (int index = 0; index < paletteTag.size(); index++) {
            palette.add(paletteTag.getCompound(index).getString("Name"));
        }
        return palette;
    }

    private static ResourceLocation getNbtResourceLocation(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key, Tag.TAG_STRING)) {
            return null;
        }
        String value = tag.getString(key);
        return value.isBlank() ? null : ResourceLocation.tryParse(value);
    }

    private static String getString(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static JsonObject getObject(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(key);
    }

    private static JsonArray getArray(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return null;
        }
        return object.getAsJsonArray(key);
    }

    private static ResourceLocation getResourceLocation(JsonObject object, String key) {
        String value = getString(object, key);
        return value.isBlank() ? null : ResourceLocation.tryParse(value);
    }

    private static final class TemplateScanResult {
        private final LinkedHashSet<String> directEntities = new LinkedHashSet<>();
        private final LinkedHashSet<String> specialDisplayBlocks = new LinkedHashSet<>();
        private final List<StructureIndexCache.SpawnerEntry> spawners = new ArrayList<>();
        private final List<StructureIndexCache.LootBinding> containers = new ArrayList<>();
        private final List<StructureIndexCache.LootBinding> suspiciousBlocks = new ArrayList<>();
    }

    private static final class StructureCodeAnalyzer {
        private static final String ENTITY_TYPE_OWNER = EntityType.class.getName().replace('.', '/');
        private static final String MOB_SPAWN_TYPE_OWNER = "net/minecraft/world/entity/MobSpawnType";
        private static final String TEMPLATE_PIECE_OWNER = "net/minecraft/world/level/levelgen/structure/TemplateStructurePiece";
        private static final String STRUCTURE_PIECE_OWNER = "net/minecraft/world/level/levelgen/structure/StructurePiece";

        private StructureCodeAnalyzer() {
        }

        private static LinkedHashSet<String> collectStructureGeneratedEntities(Structure structure) {
            LinkedHashSet<String> entityIds = new LinkedHashSet<>();
            if (structure == null) {
                return entityIds;
            }
            Class<?> structureClass = structure.getClass();
            String packageName = getPackageName(structureClass.getName());
            List<String> roots = buildNameRoots(structureClass.getSimpleName());
            Deque<String> pending = new ArrayDeque<>();
            LinkedHashSet<String> visited = new LinkedHashSet<>();
            pending.addLast(structureClass.getName().replace('.', '/'));
            while (!pending.isEmpty()) {
                String className = pending.removeFirst();
                if (!visited.add(className)) {
                    continue;
                }
                ClassScanResult scanResult = scanClass(className, packageName, roots);
                if (scanResult == null) {
                    continue;
                }
                if (scanResult.structurePieceLike && scanResult.referencesStructureSpawnType) {
                    for (String fieldName : scanResult.entityTypeFieldNames) {
                        String entityId = resolveEntityId(fieldName);
                        if (!entityId.isBlank()) {
                            entityIds.add(entityId);
                        }
                    }
                }
                for (String referencedClass : scanResult.referencedClassNames) {
                    if (!visited.contains(referencedClass)) {
                        pending.addLast(referencedClass);
                    }
                }
            }
            return entityIds;
        }

        private static ClassScanResult scanClass(String className, String packageName, List<String> roots) {
            try (InputStream inputStream = openClassStream(className)) {
                if (inputStream == null) {
                    return null;
                }
                ClassReader reader = new ClassReader(inputStream);
                ClassScanResult result = new ClassScanResult();
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                        result.structurePieceLike = TEMPLATE_PIECE_OWNER.equals(superName) || STRUCTURE_PIECE_OWNER.equals(superName);
                    }

                    @Override
                    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                        return super.visitField(access, name, descriptor, signature, value);
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                                if (ENTITY_TYPE_OWNER.equals(owner) && ("L" + ENTITY_TYPE_OWNER + ";").equals(fieldDescriptor)) {
                                    result.entityTypeFieldNames.add(fieldName);
                                }
                                if (MOB_SPAWN_TYPE_OWNER.equals(owner)) {
                                    result.referencesStructureSpawnType = true;
                                }
                                maybeAddReference(owner, packageName, roots, result.referencedClassNames);
                                super.visitFieldInsn(opcode, owner, fieldName, fieldDescriptor);
                            }

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                                maybeAddReference(owner, packageName, roots, result.referencedClassNames);
                                super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                            }

                            @Override
                            public void visitTypeInsn(int opcode, String type) {
                                maybeAddReference(type, packageName, roots, result.referencedClassNames);
                                super.visitTypeInsn(opcode, type);
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return result;
            } catch (Exception exception) {
                JeiStructures.LOGGER.debug("Failed to scan structure-related class: {}", className, exception);
                return null;
            }
        }

        private static void maybeAddReference(String internalName, String packageName, List<String> roots, Set<String> target) {
            if (internalName == null || internalName.isBlank()) {
                return;
            }
            String binaryName = internalName.replace('/', '.');
            if (!binaryName.startsWith(packageName + ".")) {
                return;
            }
            String simpleName = binaryName.substring(packageName.length() + 1);
            if (simpleName.isBlank()) {
                return;
            }
            for (String root : roots) {
                if (simpleName.startsWith(root) || simpleName.contains(root + "$") || simpleName.contains(root + "Pieces")) {
                    target.add(internalName);
                    return;
                }
            }
        }

        private static InputStream openClassStream(String className) {
            String resourcePath = className + ".class";
            ClassLoader classLoader = Structure.class.getClassLoader();
            if (classLoader != null) {
                InputStream inputStream = classLoader.getResourceAsStream(resourcePath);
                if (inputStream != null) {
                    return inputStream;
                }
            }
            return ClassLoader.getSystemResourceAsStream(resourcePath);
        }

        private static List<String> buildNameRoots(String simpleName) {
            LinkedHashSet<String> roots = new LinkedHashSet<>();
            roots.add(simpleName);
            if (simpleName.endsWith("Structure") && simpleName.length() > "Structure".length()) {
                roots.add(simpleName.substring(0, simpleName.length() - "Structure".length()));
            }
            return new ArrayList<>(roots);
        }

        private static String getPackageName(String className) {
            int packageSeparator = className.lastIndexOf('.');
            return packageSeparator >= 0 ? className.substring(0, packageSeparator) : "";
        }

        private static String resolveEntityId(String fieldName) {
            if (fieldName == null || fieldName.isBlank()) {
                return "";
            }
            try {
                Field field = EntityType.class.getDeclaredField(fieldName);
                if (!Modifier.isStatic(field.getModifiers())) {
                    return "";
                }
                field.setAccessible(true);
                Object value = field.get(null);
                if (!(value instanceof EntityType<?> entityType)) {
                    return "";
                }
                ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
                return entityId != null ? entityId.toString() : "";
            } catch (Exception exception) {
                return "";
            }
        }

        private static final class ClassScanResult {
            private boolean structurePieceLike;
            private boolean referencesStructureSpawnType;
            private final LinkedHashSet<String> entityTypeFieldNames = new LinkedHashSet<>();
            private final LinkedHashSet<String> referencedClassNames = new LinkedHashSet<>();
        }
    }

}
