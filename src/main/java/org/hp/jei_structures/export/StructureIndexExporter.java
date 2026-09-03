package org.hp.jei_structures.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
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
import net.minecraft.core.registries.BuiltInRegistries;
import org.hp.jei_structures.JeiStructures;
import org.hp.jei_structures.data.ItemStackSnapshotHelper;
import org.hp.jei_structures.data.LootTableItemResolver;
import org.hp.jei_structures.data.StoredItemNbtReader;
import org.hp.jei_structures.data.StructureBlacklistData;
import org.hp.jei_structures.data.StructureBlacklistLoader;
import org.hp.jei_structures.data.StructureBindingData;
import org.hp.jei_structures.data.StructureBindingLoader;
import org.hp.jei_structures.data.StructureBindingPaths;
import org.hp.jei_structures.data.StructureIndexCache;
import org.hp.jei_structures.data.StructureIndexCacheLoader;
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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class StructureIndexExporter {

    private static final Set<String> SUSPICIOUS_BLOCKS = Set.of("minecraft:suspicious_sand", "minecraft:suspicious_gravel");
    private static final TagKey<Block> SPECIAL_DISPLAY_BLOCKS_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(JeiStructures.MODID, "special_display_blocks"));

    private StructureIndexExporter() {
    }

    public static Path export(MinecraftServer server) throws Exception {
        ResourceManager resourceManager = server.getResourceManager();
        RegistryAccess registryAccess = server.registryAccess();
        Registry<Item> itemRegistry = server.registryAccess().lookupOrThrow(Registries.ITEM);
        Registry<Biome> biomeRegistry = registryAccess.lookupOrThrow(Registries.BIOME);
        Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Map<String, List<String>> biomeDimensions = collectBiomeDimensions(server, biomeRegistry);
        StructureBindingData bindingData = StructureBindingLoader.loadAll(resourceManager);
        StructureBlacklistData blacklistData = StructureBlacklistLoader.loadAll(resourceManager);
        StructureSpecialInfoData specialInfoData = StructureSpecialInfoLoader.loadAll(resourceManager);
        Files.createDirectories(StructureBindingPaths.getExportsRoot());
        JeiStructures.LOGGER.info("Starting structure index export. Registered structures: {}", structureRegistry.size());

        StructureIndexCache cache = new StructureIndexCache();
        cache.generatedAt = Instant.now().toString();

        LootTableItemResolver lootResolver = new LootTableItemResolver(resourceManager, itemRegistry, registryAccess, server.overworld());
        List<StructureIndexCache.StructureEntry> entries = new ArrayList<>();
        int skippedCount = 0;

        for (Identifier structureId : structureRegistry.keySet()) {
            try {
                Structure structure = structureRegistry.get(structureId).map(reference -> reference.value()).orElse(null);
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

        applyExportBindings(entries, bindingData, lootResolver, blacklistData, specialInfoData);
        entries.sort(Comparator.comparing(entry -> entry.structureId));
        cache.structures = entries;
        cache.compactLootTables();
        JeiStructures.LOGGER.info(
                "Structure scan completed. Exportable: {}, skipped: {}, imported export structures: {}",
                entries.size(),
                skippedCount,
                bindingData.getExportStructureToMobs().size() + bindingData.getExportStructureToLootBindings().keySet().stream()
                        .filter(structureId -> !bindingData.getExportStructureToMobs().containsKey(structureId))
                        .count()
        );
        JeiStructures.LOGGER.info("Compacted structure loot table details. Unique tables: {}", cache.lootTableDetails.size());

        Path path = StructureIndexPaths.getBinaryCachePath();
        Files.createDirectories(path.getParent());
        StructureIndexCacheLoader.writeExportedCache(cache);
        Files.deleteIfExists(StructureIndexPaths.getJsonPath());
        JeiStructures.LOGGER.info("Structure index export completed. Output file: {}", path);
        return path;
    }

    private static StructureIndexCache.StructureEntry exportStructure(Identifier structureId, Structure structure, ResourceManager resourceManager, LootTableItemResolver lootResolver, Registry<Biome> biomeRegistry, Map<String, List<String>> biomeDimensions, StructureBindingData bindingData, StructureBlacklistData blacklistData, StructureSpecialInfoData specialInfoData) {
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

        Identifier startPool = null;
        if (structureJson != null) {
            startPool = getIdentifier(structureJson, "start_pool");
            if (startPool != null) {
                collectTemplatesFromPool(resourceManager, startPool, templateIds, new LinkedHashSet<>(), new LinkedHashSet<>());
            }
        }
        JeiStructures.LOGGER.debug("Structure {} start pool: {}, template count: {}", structureId, startPool, templateIds.size());

        for (String templateId : templateIds) {
            TemplateScanResult result = scanTemplate(resourceManager, Identifier.tryParse(templateId));
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
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(Identifier.tryParse(entityId)).map(reference -> reference.value()).orElse(null);
            if (entityType == null) {
                continue;
            }
            ItemStack eggStack = findEgg(entityType);
            if (!eggStack.isEmpty()) {
                Identifier eggId = BuiltInRegistries.ITEM.getKey(eggStack.getItem());
                if (eggId != null) {
                    mobEggItemIds.add(eggId.toString());
                }
            }
            Identifier lootTable = entityType.getDefaultLootTable().map(resourceKey -> resourceKey.identifier()).orElse(null);
            if (lootTable == null) {
                continue;
            }
            entityLootItems.addAll(lootResolver.resolveLootItems(lootTable));
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

    private static void applyExportBindings(List<StructureIndexCache.StructureEntry> entries, StructureBindingData bindingData, LootTableItemResolver lootResolver, StructureBlacklistData blacklistData, StructureSpecialInfoData specialInfoData) {
        if (entries == null || bindingData == null) {
            return;
        }
        Map<String, StructureIndexCache.StructureEntry> entriesById = new LinkedHashMap<>();
        for (StructureIndexCache.StructureEntry entry : entries) {
            if (entry != null && entry.structureId != null && !entry.structureId.isBlank()) {
                entriesById.put(entry.structureId, entry);
            }
        }

        LinkedHashSet<String> exportedStructureIds = new LinkedHashSet<>();
        exportedStructureIds.addAll(bindingData.getExportStructureToMobs().keySet());
        exportedStructureIds.addAll(bindingData.getExportStructureToLootBindings().keySet());
        for (String structureIdString : exportedStructureIds.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
            Identifier structureId = Identifier.tryParse(structureIdString);
            if (structureId == null) {
                JeiStructures.LOGGER.warn("Skipped export data with an invalid structure id: {}", structureIdString);
                continue;
            }
            StructureIndexCache.StructureEntry entry = entriesById.get(structureIdString);
            if (entry == null) {
                entry = new StructureIndexCache.StructureEntry();
                entry.structureId = structureIdString;
                entriesById.put(structureIdString, entry);
            }

            LinkedHashSet<String> allMobEntityIds = new LinkedHashSet<>(entry.allMobEntityIds != null ? entry.allMobEntityIds : List.of());
            List<String> exportedMobIds = bindingData.getExportStructureToMobs().get(structureIdString);
            if (exportedMobIds != null) {
                allMobEntityIds.addAll(exportedMobIds);
            }
            if (blacklistData != null) {
                allMobEntityIds.removeIf(entityId -> blacklistData.isEntityBlocked(structureIdString, entityId));
            }
            entry.allMobEntityIds = new ArrayList<>(allMobEntityIds);

            List<StructureLootBinding> exportedLootBindings = bindingData.getExportStructureToLootBindings().get(structureIdString);
            mergeExportLootBindings(entry, exportedLootBindings, lootResolver, structureIdString);
            applySpecialInfoBindings(entry, allMobEntityIds, specialInfoData);
            applyLootBlacklist(structureId, entry, blacklistData);
            refreshDerivedData(entry, lootResolver);
        }

        entries.clear();
        entries.addAll(entriesById.values());
    }

    private static void mergeExportLootBindings(StructureIndexCache.StructureEntry entry, List<StructureLootBinding> bindings, LootTableItemResolver lootResolver, String structureId) {
        if (entry == null || bindings == null || bindings.isEmpty()) {
            return;
        }
        for (StructureLootBinding source : bindings) {
            if (source == null) {
                continue;
            }
            StructureIndexCache.LootBinding target = findMatchingLootBinding(entry, source);
            if (target == null) {
                target = new StructureIndexCache.LootBinding();
                target.templateId = "exports:" + structureId;
                target.blockId = source.blockId != null ? source.blockId : "";
                if (!target.blockId.isBlank()) {
                    entry.containers.add(target);
                } else {
                    entry.manualLootBindings.add(target);
                }
            }
            mergeExportLootBinding(target, source, lootResolver);
        }
    }

    private static StructureIndexCache.LootBinding findMatchingLootBinding(StructureIndexCache.StructureEntry entry, StructureLootBinding source) {
        List<List<StructureIndexCache.LootBinding>> groups = List.of(entry.containers, entry.suspiciousBlocks, entry.manualLootBindings);
        for (List<StructureIndexCache.LootBinding> group : groups) {
            for (StructureIndexCache.LootBinding target : group) {
                if (matchesLootBinding(target, source)) {
                    return target;
                }
            }
        }
        return null;
    }

    private static boolean matchesLootBinding(StructureIndexCache.LootBinding target, StructureLootBinding source) {
        if (target == null || source == null) {
            return false;
        }
        String targetBlockId = target.blockId != null ? target.blockId : "";
        String sourceBlockId = source.blockId != null ? source.blockId : "";
        if (!targetBlockId.isBlank() || !sourceBlockId.isBlank()) {
            if (targetBlockId.isBlank() || !targetBlockId.equals(sourceBlockId)) {
                return false;
            }
        }
        LinkedHashSet<String> targetLootTableIds = new LinkedHashSet<>();
        if (target.lootTableId != null && !target.lootTableId.isBlank()) {
            targetLootTableIds.add(target.lootTableId);
        }
        targetLootTableIds.addAll(target.lootTableIds != null ? target.lootTableIds : List.of());
        for (StructureIndexCache.LootTableDetail detail : target.lootTables) {
            if (detail != null && detail.lootTableId != null && !detail.lootTableId.isBlank()) {
                targetLootTableIds.add(detail.lootTableId);
            }
        }
        LinkedHashSet<String> sourceLootTableIds = new LinkedHashSet<>(source.lootTables != null ? source.lootTables : List.of());
        if (!targetLootTableIds.isEmpty() && !sourceLootTableIds.isEmpty()) {
            sourceLootTableIds.retainAll(targetLootTableIds);
            return !sourceLootTableIds.isEmpty();
        }
        if (!targetLootTableIds.isEmpty() || !sourceLootTableIds.isEmpty()) {
            return true;
        }
        LinkedHashSet<String> targetItemIds = new LinkedHashSet<>(target.itemIds != null ? target.itemIds : List.of());
        LinkedHashSet<String> sourceItemIds = new LinkedHashSet<>(source.items != null ? source.items : List.of());
        return targetItemIds.isEmpty() || sourceItemIds.isEmpty() || !java.util.Collections.disjoint(targetItemIds, sourceItemIds);
    }

    private static void mergeExportLootBinding(StructureIndexCache.LootBinding target, StructureLootBinding source, LootTableItemResolver lootResolver) {
        if (target == null || source == null) {
            return;
        }
        LinkedHashSet<String> tableIds = new LinkedHashSet<>();
        if (target.lootTableId != null && !target.lootTableId.isBlank()) {
            tableIds.add(target.lootTableId);
        }
        if (target.lootTableIds != null) {
            tableIds.addAll(target.lootTableIds);
        }
        for (StructureIndexCache.LootTableDetail detail : target.lootTables) {
            if (detail != null && detail.lootTableId != null && !detail.lootTableId.isBlank()) {
                tableIds.add(detail.lootTableId);
            }
        }
        tableIds.addAll(source.lootTables);
        for (StructureIndexCache.LootTableDetail detail : source.lootTableDetails) {
            if (detail != null && detail.lootTableId != null && !detail.lootTableId.isBlank()) {
                tableIds.add(detail.lootTableId);
            }
        }
        if ((target.lootTableId == null || target.lootTableId.isBlank()) && !tableIds.isEmpty()) {
            target.lootTableId = tableIds.iterator().next();
        }
        target.lootTableIds = new ArrayList<>(tableIds);

        target.storedItemIds = mergeLists(target.storedItemIds, source.storedItems);
        target.storedItemStacks = mergeItemStackSnapshots(target.storedItemStacks, source.itemStacks);
        target.itemIds = mergeLists(target.itemIds, source.items);
        for (String tableId : tableIds) {
            StructureIndexCache.LootTableDetail capturedDetail = findLootTableDetail(source.lootTableDetails, tableId);
            if (capturedDetail != null && capturedDetail.entries != null && !capturedDetail.entries.isEmpty()) {
                mergeLootTableDetail(target, capturedDetail, true);
                continue;
            }
            if (findLootTableDetail(target.lootTables, tableId) == null && lootResolver != null) {
                StructureIndexCache.LootTableDetail resolvedDetail = buildLootTableDetail(tableId, lootResolver);
                if (resolvedDetail != null) {
                    mergeLootTableDetail(target, resolvedDetail, false);
                }
            }
        }
        for (StructureIndexCache.LootTableDetail detail : source.lootTableDetails) {
            mergeLootTableDetail(target, detail, true);
        }
        LinkedHashSet<String> itemIds = new LinkedHashSet<>(target.itemIds != null ? target.itemIds : List.of());
        itemIds.addAll(target.storedItemIds != null ? target.storedItemIds : List.of());
        mergeSnapshotItemIds(itemIds, target.storedItemStacks);
        for (StructureIndexCache.LootTableDetail detail : target.lootTables) {
            if (detail == null || detail.entries == null) {
                continue;
            }
            for (StructureIndexCache.LootItemEntry itemEntry : detail.entries) {
                if (itemEntry != null && itemEntry.itemId != null && !itemEntry.itemId.isBlank()) {
                    itemIds.add(itemEntry.itemId);
                }
            }
        }
        target.itemIds = new ArrayList<>(itemIds);
    }

    private static List<StructureIndexCache.ItemStackSnapshot> mergeItemStackSnapshots(List<StructureIndexCache.ItemStackSnapshot> first, List<StructureIndexCache.ItemStackSnapshot> second) {
        List<StructureIndexCache.ItemStackSnapshot> result = new ArrayList<>();
        for (StructureIndexCache.ItemStackSnapshot snapshot : first != null ? first : List.<StructureIndexCache.ItemStackSnapshot>of()) {
            addItemStackSnapshot(result, snapshot);
        }
        for (StructureIndexCache.ItemStackSnapshot snapshot : second != null ? second : List.<StructureIndexCache.ItemStackSnapshot>of()) {
            addItemStackSnapshot(result, snapshot);
        }
        return result;
    }

    private static void addItemStackSnapshot(List<StructureIndexCache.ItemStackSnapshot> output, StructureIndexCache.ItemStackSnapshot source) {
        if (output == null || ItemStackSnapshotHelper.isEmptySnapshot(source)) {
            return;
        }
        String itemId = source.itemId != null ? source.itemId : "";
        String stackTag = source.stackTag != null ? source.stackTag : "";
        for (StructureIndexCache.ItemStackSnapshot existing : output) {
            if (itemId.equals(existing.itemId) && stackTag.equals(existing.stackTag)) {
                return;
            }
        }
        StructureIndexCache.ItemStackSnapshot copy = new StructureIndexCache.ItemStackSnapshot();
        copy.itemId = itemId;
        copy.stackTag = stackTag;
        output.add(copy);
    }

    private static void mergeLootTableDetail(StructureIndexCache.LootBinding target, StructureIndexCache.LootTableDetail incoming, boolean captured) {
        if (target == null || incoming == null || incoming.lootTableId == null || incoming.lootTableId.isBlank()) {
            return;
        }
        for (int index = 0; index < target.lootTables.size(); index++) {
            StructureIndexCache.LootTableDetail existing = target.lootTables.get(index);
            if (existing != null && incoming.lootTableId.equals(existing.lootTableId)) {
                if (captured && incoming.entries != null && !incoming.entries.isEmpty()) {
                    target.lootTables.set(index, copyLootTableDetail(incoming));
                }
                return;
            }
        }
        target.lootTables.add(copyLootTableDetail(incoming));
    }

    private static StructureIndexCache.LootTableDetail findLootTableDetail(List<StructureIndexCache.LootTableDetail> details, String lootTableId) {
        if (details == null || lootTableId == null || lootTableId.isBlank()) {
            return null;
        }
        for (StructureIndexCache.LootTableDetail detail : details) {
            if (detail != null && lootTableId.equals(detail.lootTableId)) {
                return detail;
            }
        }
        return null;
    }

    private static StructureIndexCache.LootTableDetail copyLootTableDetail(StructureIndexCache.LootTableDetail source) {
        StructureIndexCache.LootTableDetail copy = new StructureIndexCache.LootTableDetail();
        if (source == null) {
            return copy;
        }
        copy.lootTableId = source.lootTableId != null ? source.lootTableId : "";
        if (source.entries != null) {
            for (StructureIndexCache.LootItemEntry sourceEntry : source.entries) {
                if (sourceEntry == null) {
                    continue;
                }
                StructureIndexCache.LootItemEntry entry = new StructureIndexCache.LootItemEntry();
                entry.itemId = sourceEntry.itemId != null ? sourceEntry.itemId : "";
                entry.itemStackTag = sourceEntry.itemStackTag != null ? sourceEntry.itemStackTag : "";
                entry.weight = sourceEntry.weight;
                entry.quality = sourceEntry.quality;
                entry.rollsText = sourceEntry.rollsText != null ? sourceEntry.rollsText : "";
                entry.bonusRollsText = sourceEntry.bonusRollsText != null ? sourceEntry.bonusRollsText : "";
                entry.chanceText = sourceEntry.chanceText != null ? sourceEntry.chanceText : "";
                entry.countText = sourceEntry.countText != null ? sourceEntry.countText : "";
                entry.chanceNotes = copyLootTextEntries(sourceEntry.chanceNotes);
                entry.countNotes = copyLootTextEntries(sourceEntry.countNotes);
                copy.entries.add(entry);
            }
        }
        return copy;
    }

    private static List<StructureIndexCache.LootTextEntry> copyLootTextEntries(List<StructureIndexCache.LootTextEntry> source) {
        List<StructureIndexCache.LootTextEntry> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (StructureIndexCache.LootTextEntry sourceEntry : source) {
            if (sourceEntry == null) {
                continue;
            }
            StructureIndexCache.LootTextEntry entry = new StructureIndexCache.LootTextEntry();
            entry.translationKey = sourceEntry.translationKey != null ? sourceEntry.translationKey : "";
            entry.args = sourceEntry.args != null ? new ArrayList<>(sourceEntry.args) : new ArrayList<>();
            copy.add(entry);
        }
        return copy;
    }

    private static void refreshDerivedData(StructureIndexCache.StructureEntry entry, LootTableItemResolver lootResolver) {
        if (entry == null) {
            return;
        }
        LinkedHashSet<String> entityLootItems = new LinkedHashSet<>();
        LinkedHashSet<String> mobEggItemIds = new LinkedHashSet<>();
        List<String> mobIds = entry.allMobEntityIds != null ? entry.allMobEntityIds : List.of();
        for (String entityId : mobIds) {
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(Identifier.tryParse(entityId)).map(reference -> reference.value()).orElse(null);
            if (entityType == null) {
                continue;
            }
            ItemStack eggStack = findEgg(entityType);
            if (!eggStack.isEmpty()) {
                Identifier eggId = BuiltInRegistries.ITEM.getKey(eggStack.getItem());
                if (eggId != null) {
                    mobEggItemIds.add(eggId.toString());
                }
            }
            Identifier lootTable = entityType.getDefaultLootTable().map(resourceKey -> resourceKey.identifier()).orElse(null);
            if (lootTable != null && lootResolver != null) {
                entityLootItems.addAll(lootResolver.resolveLootItems(lootTable));
            }
        }
        mergeResolvedLootItems(entry.containers, lootResolver);
        mergeResolvedLootItems(entry.suspiciousBlocks, lootResolver);
        mergeResolvedLootItems(entry.manualLootBindings, lootResolver);

        entry.allMobEggItemIds = new ArrayList<>(mobEggItemIds);
        entry.entityLootItems = new ArrayList<>(entityLootItems);
        entry.containerLootItems = mergeLists(flattenItems(entry.containers), flattenItems(entry.manualLootBindings));
        entry.suspiciousLootItems = flattenItems(entry.suspiciousBlocks);
        entry.allEntityLootItemIds = new ArrayList<>(entityLootItems);
        entry.allContainerLootItemIds = new ArrayList<>(entry.containerLootItems);
        entry.allSuspiciousLootItemIds = new ArrayList<>(entry.suspiciousLootItems);
        entry.allLootItemIds = mergeOrdered(entityLootItems, new LinkedHashSet<>(entry.containerLootItems), new LinkedHashSet<>(entry.suspiciousLootItems));
    }

    private static void mergeResolvedLootItems(List<StructureIndexCache.LootBinding> bindings, LootTableItemResolver lootResolver) {
        if (bindings == null) {
            return;
        }
        for (StructureIndexCache.LootBinding binding : bindings) {
            mergeResolvedLootItems(binding, lootResolver);
        }
    }

    private static void applyConfiguredMobBindings(Identifier structureId, LinkedHashSet<String> allMobEntityIds, StructureBindingData bindingData) {
        List<String> configuredEntityIds = bindingData.getStructureToMobs().get(structureId.toString());
        if (configuredEntityIds != null) {
            allMobEntityIds.addAll(configuredEntityIds);
        }
    }

    private static void applyConfiguredSpawnedEntityBindings(Identifier structureId, LinkedHashSet<String> spawnOverrideEntities, LinkedHashSet<String> templateEntities, StructureBindingData bindingData) {
        List<String> configuredEntityIds = bindingData.getStructureToMobs().get(structureId.toString());
        if (configuredEntityIds != null) {
            spawnOverrideEntities.addAll(configuredEntityIds);
            templateEntities.addAll(configuredEntityIds);
        }
    }

    private static void applyConfiguredLootBindings(Identifier structureId, StructureIndexCache.StructureEntry entry, LootTableItemResolver lootResolver, StructureBindingData bindingData, StructureBlacklistData blacklistData) {
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
                itemIds.addAll(lootResolver.resolveLootItems(Identifier.tryParse(lootTableId)));
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

    private static void applyEntityAndBlockBlacklist(Identifier structureId, StructureIndexCache.StructureEntry entry, LinkedHashSet<String> spawnOverrideEntities, LinkedHashSet<String> templateEntities, LinkedHashSet<String> allMobEntityIds, StructureBlacklistData blacklistData) {
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

    private static void applyLootBlacklist(Identifier structureId, StructureIndexCache.StructureEntry entry, StructureBlacklistData blacklistData) {
        if (structureId == null || entry == null || blacklistData == null) {
            return;
        }
        filterLootBindings(structureId, entry.containers, blacklistData, true);
        filterLootBindings(structureId, entry.suspiciousBlocks, blacklistData, true);
        filterLootBindings(structureId, entry.manualLootBindings, blacklistData, false);
    }

    private static void filterLootBindings(Identifier structureId, List<StructureIndexCache.LootBinding> bindings, StructureBlacklistData blacklistData, boolean checkContainer) {
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
            return binding.storedItemIds.isEmpty()
                    && binding.storedItemStacks.isEmpty()
                    && binding.lootTables.isEmpty()
                    && binding.itemIds.isEmpty();
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

    private static boolean isLootBindingContainerBlocked(Identifier structureId, StructureIndexCache.LootBinding binding, StructureBlacklistData blacklistData) {
        return structureId != null
                && binding != null
                && binding.blockId != null
                && !binding.blockId.isBlank()
                && blacklistData != null
                && blacklistData.isContainerBlocked(structureId.toString(), binding.blockId);
    }

    private static void mergeResolvedLootItems(StructureIndexCache.LootBinding binding, LootTableItemResolver lootResolver) {
        if (binding == null || lootResolver == null) {
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
            itemIds.addAll(lootResolver.resolveLootItems(Identifier.tryParse(binding.lootTableId)));
        }
        binding.itemIds = new ArrayList<>(itemIds);
    }

    private static void applySpecialInfoBindings(StructureIndexCache.StructureEntry entry, Set<String> allMobEntityIds, StructureSpecialInfoData specialInfoData) {
        if (entry == null || specialInfoData == null) {
            return;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (StructureIndexCache.SpecialInfoEntry infoEntry : entry.specialInfos) {
            if (infoEntry != null && infoEntry.targetType != null && infoEntry.targetId != null) {
                seen.add(infoEntry.targetType + ":" + infoEntry.targetId);
            }
        }
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
        return lootResolver.resolveLootTableDetail(Identifier.tryParse(lootTableId));
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
            Identifier dimensionId = level.dimension().identifier();
            for (var holder : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
                Identifier biomeId = biomeRegistry.getKey(holder.value());
                if (biomeId == null) {
                    continue;
                }
                dimensionsByBiome.computeIfAbsent(biomeId.toString(), key -> new LinkedHashSet<>()).add(dimensionId.toString());
            }
        }
        for (var entry : biomeRegistry.entrySet()) {
            Identifier biomeId = entry.getKey().identifier();
            if (biomeId == null) {
                continue;
            }
            LinkedHashSet<String> dimensionIds = dimensionsByBiome.computeIfAbsent(biomeId.toString(), key -> new LinkedHashSet<>());
            if (dimensionIds.contains(Level.OVERWORLD.identifier().toString())) {
                continue;
            }
            Biome biome = entry.getValue();
            if (biome == null) {
                continue;
            }
            var holder = biomeRegistry.wrapAsHolder(biome);
            if (holder.is(BiomeTags.IS_OVERWORLD)) {
                dimensionIds.add(Level.OVERWORLD.identifier().toString());
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

    private static Map<String, List<String>> collectEntryBiomeDimensions(Identifier structureId, List<String> biomeIds, Map<String, List<String>> biomeDimensions) {
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
            Identifier tagId = Identifier.tryParse(selector.substring(1));
            if (tagId == null) {
                return;
            }
            TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, tagId);
            for (var holder : biomeRegistry.getTagOrEmpty(tagKey)) {
                Identifier biomeId = biomeRegistry.getKey(holder.value());
                if (biomeId != null) {
                    resolved.add(biomeId.toString());
                }
            }
            return;
        }
        Identifier biomeId = Identifier.tryParse(selector);
        if (biomeId != null && biomeRegistry.containsKey(biomeId)) {
            resolved.add(biomeId.toString());
        }
    }

    private static String getBiomeDisplayName(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return "";
        }
        Identifier id = Identifier.tryParse(biomeId);
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
        Identifier id = Identifier.tryParse(dimensionId);
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
        return SpawnEggItem.byId(entityType)
                .map(holder -> new ItemStack(holder.value()))
                .orElseGet(() -> new ItemStack(Items.AIR));
    }

    private static List<String> flattenItems(List<StructureIndexCache.LootBinding> bindings) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (StructureIndexCache.LootBinding binding : bindings) {
            result.addAll(binding.itemIds);
        }
        return new ArrayList<>(result);
    }

    private static void mergeSnapshotItemIds(Set<String> itemIds, List<StructureIndexCache.ItemStackSnapshot> snapshots) {
        if (itemIds == null || snapshots == null || snapshots.isEmpty()) {
            return;
        }
        for (StructureIndexCache.ItemStackSnapshot snapshot : snapshots) {
            String itemId = ItemStackSnapshotHelper.snapshotItemId(snapshot);
            if (itemId != null && !itemId.isBlank() && !"minecraft:air".equals(itemId)) {
                itemIds.add(itemId);
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
                Identifier entityId = getIdentifier(spawnElement.getAsJsonObject(), "type");
                if (entityId != null) {
                    entityIds.add(entityId.toString());
                }
            }
        }
    }

    private static void collectTemplatesFromPool(ResourceManager resourceManager, Identifier poolId, Set<String> templateIds, Set<String> visitedPools, Set<String> scannedTemplates) {
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
            Identifier location = getIdentifier(element, "location");
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
        Identifier projectionPool = getIdentifier(element, "pool");
        if (projectionPool != null) {
            collectTemplatesFromPool(resourceManager, projectionPool, templateIds, visitedPools, scannedTemplates);
        }
    }

    private static void collectChildPoolsFromTemplate(ResourceManager resourceManager, Identifier templateId, Set<String> templateIds, Set<String> visitedPools, Set<String> scannedTemplates) {
        if (templateId == null || !scannedTemplates.add(templateId.toString())) {
            return;
        }
        CompoundTag root = readTemplateRoot(resourceManager, templateId);
        if (root == null) {
            return;
        }
        List<String> palette = readTemplatePalette(root);
        ListTag blocks = root.getListOrEmpty("blocks");
        for (int index = 0; index < blocks.size(); index++) {
            Optional<CompoundTag> blockOptional = blocks.getCompound(index);
            if (blockOptional.isEmpty()) {
                continue;
            }
            CompoundTag block = blockOptional.get();
            int stateIndex = block.getIntOr("state", -1);
            String blockId = stateIndex >= 0 && stateIndex < palette.size() ? palette.get(stateIndex) : "";
            if (!"minecraft:jigsaw".equals(blockId) || !block.contains("nbt")) {
                continue;
            }
            block.getCompound("nbt").ifPresent(blockNbt -> {
                Identifier childPool = getNbtIdentifier(blockNbt, "pool");
                if (childPool != null) {
                    collectTemplatesFromPool(resourceManager, childPool, templateIds, visitedPools, scannedTemplates);
                }
            });
        }
    }

    private static TemplateScanResult scanTemplate(ResourceManager resourceManager, Identifier templateId) {
        if (templateId == null) {
            return null;
        }
        CompoundTag root = readTemplateRoot(resourceManager, templateId);
        if (root == null) {
            return null;
        }

        return parseTemplate(templateId, root);
    }

    private static CompoundTag readTemplateRoot(ResourceManager resourceManager, Identifier templateId) {
        Optional<Resource> resource = resourceManager.getResource(toTemplateLocation(templateId));
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

    private static TemplateScanResult parseTemplate(Identifier templateId, CompoundTag root) {
        TemplateScanResult result = new TemplateScanResult();
        List<String> palette = readTemplatePalette(root);

        ListTag blocks = root.getListOrEmpty("blocks");
        for (int index = 0; index < blocks.size(); index++) {
            Optional<CompoundTag> blockOptional = blocks.getCompound(index);
            if (blockOptional.isEmpty()) {
                continue;
            }
            CompoundTag block = blockOptional.get();
            int stateIndex = block.getIntOr("state", -1);
            String blockId = stateIndex >= 0 && stateIndex < palette.size() ? palette.get(stateIndex) : "";
            addSpecialDisplayBlock(blockId, result);
            CompoundTag blockEntity = block.getCompound("nbt").orElse(null);
            if (blockId.isEmpty() || blockEntity == null) {
                continue;
            }
            addSpawner(templateId, blockId, blockEntity, result);
            addLootBinding(templateId, blockId, blockEntity, result);
        }

        ListTag entities = root.getListOrEmpty("entities");
        for (int index = 0; index < entities.size(); index++) {
            Optional<CompoundTag> entityEntryOptional = entities.getCompound(index);
            if (entityEntryOptional.isEmpty()) {
                continue;
            }
            CompoundTag entityEntry = entityEntryOptional.get();
            if (!entityEntry.contains("nbt")) {
                continue;
            }
            entityEntry.getCompound("nbt").ifPresent(entityNbt -> {
                String entityId = entityNbt.getStringOr("id", "");
                if (!entityId.isBlank()) {
                    result.directEntities.add(entityId);
                }
            });
        }

        return result;
    }

    private static void addSpecialDisplayBlock(String blockId, TemplateScanResult result) {
        if (blockId == null || blockId.isBlank() || result == null) {
            return;
        }
        Identifier blockKey = Identifier.tryParse(blockId);
        if (blockKey == null) {
            return;
        }
        Block block = BuiltInRegistries.BLOCK.get(blockKey).map(reference -> reference.value()).orElse(null);
        if (block != null && block.defaultBlockState().is(SPECIAL_DISPLAY_BLOCKS_TAG)) {
            result.specialDisplayBlocks.add(blockId);
        }
    }

    private static void addSpawner(Identifier templateId, String blockId, CompoundTag blockEntity, TemplateScanResult result) {
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

    private static void addLootBinding(Identifier templateId, String blockId, CompoundTag blockEntity, TemplateScanResult result) {
        String lootTable = findLootTable(blockEntity);
        List<StructureIndexCache.ItemStackSnapshot> storedItemStacks = StoredItemNbtReader.readStoredItemSnapshots(blockEntity);
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
        if (blockEntity.contains("SpawnData")) {
            CompoundTag spawnData = blockEntity.getCompoundOrEmpty("SpawnData");
            Optional<String> entityId = spawnData.getCompound("entity").flatMap(entity -> entity.getString("id"));
            if (entityId.isPresent() && !entityId.get().isBlank()) {
                return entityId.get();
            }
            entityId = spawnData.getCompound("Entity").flatMap(entity -> entity.getString("id"));
            if (entityId.isPresent() && !entityId.get().isBlank()) {
                return entityId.get();
            }
        }
        if (blockEntity.contains("SpawnPotentials")) {
            ListTag spawnPotentials = blockEntity.getListOrEmpty("SpawnPotentials");
            for (int index = 0; index < spawnPotentials.size(); index++) {
                Optional<CompoundTag> potentialOptional = spawnPotentials.getCompound(index);
                if (potentialOptional.isEmpty()) {
                    continue;
                }
                CompoundTag potential = potentialOptional.get();
                if (potential.contains("data")) {
                    Optional<String> entityId = potential.getCompound("data")
                            .flatMap(data -> data.getCompound("entity"))
                            .flatMap(entity -> entity.getString("id"));
                    if (entityId.isPresent() && !entityId.get().isBlank()) {
                        return entityId.get();
                    }
                }
            }
        }
        return "";
    }

    private static String findLootTable(CompoundTag blockEntity) {
        if (blockEntity.contains("LootTable")) {
            return blockEntity.getStringOr("LootTable", "");
        }
        if (blockEntity.contains("loot_table")) {
            return blockEntity.getStringOr("loot_table", "");
        }
        return "";
    }

    private static List<String> readTemplatePalette(CompoundTag root) {
        List<String> palette = new ArrayList<>();
        ListTag paletteTag = root.getListOrEmpty("palette");
        for (int index = 0; index < paletteTag.size(); index++) {
            Optional<CompoundTag> paletteEntry = paletteTag.getCompound(index);
            if (paletteEntry.isPresent()) {
                palette.add(paletteEntry.get().getStringOr("Name", ""));
            }
        }
        return palette;
    }

    private static Identifier getNbtIdentifier(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key)) {
            return null;
        }
        String value = tag.getStringOr(key, "");
        return value.isBlank() ? null : Identifier.tryParse(value);
    }

    private static JsonObject readJson(ResourceManager resourceManager, Identifier location) {
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

    private static Identifier toStructureJsonLocation(Identifier id) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "worldgen/structure/" + id.getPath() + ".json");
    }

    private static Identifier toPoolJsonLocation(Identifier id) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "worldgen/template_pool/" + id.getPath() + ".json");
    }

    private static Identifier toTemplateLocation(Identifier id) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "structures/" + id.getPath() + ".nbt");
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

    private static Identifier getIdentifier(JsonObject object, String key) {
        String value = getString(object, key);
        return value.isBlank() ? null : Identifier.tryParse(value);
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
                Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
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
