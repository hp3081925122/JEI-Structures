package org.hp.jei_structures.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StructureIndexCache implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int CURRENT_VERSION = 14;
    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    public int version = CURRENT_VERSION;
    public String generatedAt = "";
    public List<StructureEntry> structures = new ArrayList<>();
    public Map<String, LootTableDetail> lootTableDetails = new LinkedHashMap<>();

    public boolean prepareRuntimeLootTables() {
        if (version != CURRENT_VERSION) {
            return false;
        }
        if (lootTableDetails == null) {
            lootTableDetails = new LinkedHashMap<>();
        }
        for (StructureEntry structure : structures) {
            if (structure == null) {
                continue;
            }
            prepareRuntimeLootTables(structure.containers);
            prepareRuntimeLootTables(structure.suspiciousBlocks);
            prepareRuntimeLootTables(structure.manualLootBindings);
        }
        version = CURRENT_VERSION;
        return true;
    }

    public void compactLootTables() {
        lootTableDetails = new LinkedHashMap<>();
        for (StructureEntry structure : structures) {
            if (structure == null) {
                continue;
            }
            compactLootTables(structure.containers);
            compactLootTables(structure.suspiciousBlocks);
            compactLootTables(structure.manualLootBindings);
        }
        version = CURRENT_VERSION;
    }

    private void prepareRuntimeLootTables(List<LootBinding> bindings) {
        if (bindings == null) {
            return;
        }
        for (LootBinding binding : bindings) {
            if (binding == null) {
                continue;
            }
            LinkedHashSet<String> ids = new LinkedHashSet<>(binding.lootTableIds);
            if (binding.lootTables != null) {
                for (LootTableDetail detail : binding.lootTables) {
                    if (detail == null || detail.lootTableId == null || detail.lootTableId.isBlank()) {
                        continue;
                    }
                    lootTableDetails.putIfAbsent(detail.lootTableId, detail);
                    ids.add(detail.lootTableId);
                }
            }
            List<LootTableDetail> resolved = new ArrayList<>();
            for (String id : ids) {
                LootTableDetail detail = lootTableDetails.get(id);
                if (detail != null) {
                    resolved.add(detail);
                }
            }
            binding.lootTableIds = new ArrayList<>(ids);
            binding.lootTables = resolved;
        }
    }

    private void compactLootTables(List<LootBinding> bindings) {
        if (bindings == null) {
            return;
        }
        for (LootBinding binding : bindings) {
            if (binding == null) {
                continue;
            }
            LinkedHashSet<String> ids = new LinkedHashSet<>(binding.lootTableIds);
            if (binding.lootTables != null) {
                for (LootTableDetail detail : binding.lootTables) {
                    if (detail == null || detail.lootTableId == null || detail.lootTableId.isBlank()) {
                        continue;
                    }
                    lootTableDetails.putIfAbsent(detail.lootTableId, detail);
                    ids.add(detail.lootTableId);
                }
            }
            binding.lootTableIds = new ArrayList<>(ids);
            binding.lootTables = new ArrayList<>();
        }
    }

    public static final class StructureEntry implements Serializable {

        private static final long serialVersionUID = 1L;
        public String structureId = "";
        public String structureType = "";
        public String generationStep = "";
        public String terrainAdjustment = "";
        public List<String> generationBiomes = new ArrayList<>();
        public List<String> resolvedGenerationBiomes = new ArrayList<>();
        public List<GenerationBiomeGroup> generationBiomeGroups = new ArrayList<>();
        public Map<String, List<String>> generationBiomeDimensions = new LinkedHashMap<>();
        public List<String> templateIds = new ArrayList<>();
        public List<String> spawnOverridesEntities = new ArrayList<>();
        public List<String> templateEntities = new ArrayList<>();
        public List<String> spawnedEntities = new ArrayList<>();
        public List<String> allMobEntityIds = new ArrayList<>();
        public List<String> allMobEggItemIds = new ArrayList<>();
        public List<String> entityLootItems = new ArrayList<>();
        public List<String> containerLootItems = new ArrayList<>();
        public List<String> suspiciousLootItems = new ArrayList<>();
        public List<String> allEntityLootItemIds = new ArrayList<>();
        public List<String> allContainerLootItemIds = new ArrayList<>();
        public List<String> allSuspiciousLootItemIds = new ArrayList<>();
        public List<String> allLootItemIds = new ArrayList<>();
        public List<String> specialDisplayBlocks = new ArrayList<>();
        public List<SpecialInfoEntry> specialInfos = new ArrayList<>();
        public List<SpawnerEntry> spawners = new ArrayList<>();
        public List<LootBinding> containers = new ArrayList<>();
        public List<LootBinding> suspiciousBlocks = new ArrayList<>();
        public List<LootBinding> manualLootBindings = new ArrayList<>();
    }

    public static final class SpawnerEntry implements Serializable {

        private static final long serialVersionUID = 1L;
        public String templateId = "";
        public String entityId = "";
    }

    public static final class GenerationBiomeGroup implements Serializable {

        private static final long serialVersionUID = 1L;
        public String selector = "";
        public String selectorType = "";
        public List<String> resolvedBiomeIds = new ArrayList<>();
    }

    public static final class LootBinding implements Serializable {

        private static final long serialVersionUID = 1L;
        public String templateId = "";
        public String blockId = "";
        public String lootTableId = "";
        public List<String> storedItemIds = new ArrayList<>();
        public List<ItemStackSnapshot> storedItemStacks = new ArrayList<>();
        public List<String> itemIds = new ArrayList<>();
        public List<String> lootTableIds = new ArrayList<>();
        public List<LootTableDetail> lootTables = new ArrayList<>();
    }

    public static final class SpecialInfoEntry implements Serializable {

        private static final long serialVersionUID = 1L;
        public String targetType = "";
        public String targetId = "";
        public String translationKey = "";
    }

    public static final class LootTableDetail implements Serializable {

        private static final long serialVersionUID = 1L;
        public String lootTableId = "";
        public List<LootItemEntry> entries = new ArrayList<>();
    }

    public static final class LootItemEntry implements Serializable {

        private static final long serialVersionUID = 1L;
        public String itemId = "";
        public String itemStackTag = "";
        public int weight;
        public int quality;
        public String rollsText = "";
        public String bonusRollsText = "";
        public String chanceText = "";
        public String countText = "";
        public List<LootTextEntry> chanceNotes = new ArrayList<>();
        public List<LootTextEntry> countNotes = new ArrayList<>();
    }

    public static final class LootTextEntry implements Serializable {

        private static final long serialVersionUID = 1L;
        public String translationKey = "";
        public List<String> args = new ArrayList<>();
    }

    public static final class ItemStackSnapshot implements Serializable {

        private static final long serialVersionUID = 1L;
        public String itemId = "";
        public String stackTag = "";
    }
}
