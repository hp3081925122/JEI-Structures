package org.hp.jei_structures.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StructureIndexCache implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int CURRENT_VERSION = 13;
    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    public int version = CURRENT_VERSION;
    public String generatedAt = "";
    public List<StructureEntry> structures = new ArrayList<>();
    public Map<String, LootTableDetail> lootTableDetails = new LinkedHashMap<>();

    public void compactLootTables() {
        if (lootTableDetails == null) {
            lootTableDetails = new LinkedHashMap<>();
        }
        for (LootTableDetail detail : lootTableDetails.values()) {
            removeDuplicateLootEntries(detail);
        }
        for (StructureEntry structure : structures) {
            if (structure == null) {
                continue;
            }
            collectLootTables(structure.containers, true);
            collectLootTables(structure.suspiciousBlocks, true);
            collectLootTables(structure.manualLootBindings, true);
        }
        version = CURRENT_VERSION;
    }

    public boolean prepareRuntimeLootTables() {
        if (version != 12 && version != CURRENT_VERSION) {
            return false;
        }
        version = CURRENT_VERSION;
        if (lootTableDetails == null) {
            lootTableDetails = new LinkedHashMap<>();
        }
        for (LootTableDetail detail : lootTableDetails.values()) {
            removeDuplicateLootEntries(detail);
        }
        for (StructureEntry structure : structures) {
            if (structure == null) {
                continue;
            }
            collectLootTables(structure.containers, false);
            collectLootTables(structure.suspiciousBlocks, false);
            collectLootTables(structure.manualLootBindings, false);
        }
        for (StructureEntry structure : structures) {
            if (structure == null) {
                continue;
            }
            resolveLootTables(structure.containers);
            resolveLootTables(structure.suspiciousBlocks);
            resolveLootTables(structure.manualLootBindings);
        }
        return true;
    }

    private void collectLootTables(List<LootBinding> bindings, boolean compact) {
        if (bindings == null) {
            return;
        }
        for (LootBinding binding : bindings) {
            if (binding == null) {
                continue;
            }
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            if (binding.lootTableIds != null) {
                ids.addAll(binding.lootTableIds);
            }
            if (binding.lootTableId != null && !binding.lootTableId.isBlank()) {
                ids.add(binding.lootTableId);
            }
            if (binding.lootTables != null) {
                for (LootTableDetail detail : binding.lootTables) {
                    if (detail == null || detail.lootTableId == null || detail.lootTableId.isBlank()) {
                        continue;
                    }
                    removeDuplicateLootEntries(detail);
                    LootTableDetail existing = lootTableDetails.get(detail.lootTableId);
                    if (existing == null || lootTableDetailScore(detail) > lootTableDetailScore(existing)) {
                        lootTableDetails.put(detail.lootTableId, detail);
                    }
                    ids.add(detail.lootTableId);
                }
            }
            ids.removeIf(id -> id == null || id.isBlank());
            binding.lootTableIds = new ArrayList<>(ids);
            if (compact) {
                binding.lootTables = new ArrayList<>();
            }
        }
    }

    private void resolveLootTables(List<LootBinding> bindings) {
        if (bindings == null) {
            return;
        }
        for (LootBinding binding : bindings) {
            if (binding == null) {
                continue;
            }
            List<LootTableDetail> resolved = new ArrayList<>();
            for (String id : binding.lootTableIds) {
                LootTableDetail detail = lootTableDetails.get(id);
                if (detail != null) {
                    resolved.add(detail);
                }
            }
            binding.lootTables = resolved;
        }
    }

    private static int lootTableDetailScore(LootTableDetail detail) {
        if (detail.entries == null) {
            return 0;
        }
        int score = detail.entries.size() * 100;
        for (LootItemEntry entry : detail.entries) {
            if (entry != null) {
                score += entry.chanceNotes != null ? entry.chanceNotes.size() : 0;
                score += entry.countNotes != null ? entry.countNotes.size() : 0;
                score += entry.chanceText != null && !entry.chanceText.isBlank() ? 1 : 0;
                score += entry.countText != null && !entry.countText.isBlank() ? 1 : 0;
            }
        }
        return score;
    }

    public static void removeDuplicateLootEntries(LootTableDetail detail) {
        if (detail == null || detail.entries == null || detail.entries.size() < 2) {
            return;
        }
        LinkedHashMap<String, LootItemEntry> unique = new LinkedHashMap<>();
        for (LootItemEntry entry : detail.entries) {
            if (entry != null) {
                unique.putIfAbsent(GSON.toJson(entry), entry);
            }
        }
        if (unique.size() != detail.entries.size()) {
            detail.entries = new ArrayList<>(unique.values());
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
        public List<String> lootTableIds = new ArrayList<>();
        public List<String> storedItemIds = new ArrayList<>();
        public List<ItemStackEntry> storedItemStacks = new ArrayList<>();
        public List<String> itemIds = new ArrayList<>();
        public List<LootTableDetail> lootTables = new ArrayList<>();
    }

    public static final class ItemStackEntry implements Serializable {

        private static final long serialVersionUID = 1L;
        public String itemId = "";
        public String tagSnbt = "";
    }

    public static List<ItemStackEntry> copyItemStacks(List<ItemStackEntry> entries) {
        List<ItemStackEntry> result = new ArrayList<>();
        if (entries != null) {
            for (ItemStackEntry entry : entries) {
                if (entry != null && entry.itemId != null && !entry.itemId.isBlank()) {
                    ItemStackEntry copy = new ItemStackEntry();
                    copy.itemId = entry.itemId;
                    copy.tagSnbt = entry.tagSnbt != null ? entry.tagSnbt : "";
                    result.add(copy);
                }
            }
        }
        return result;
    }

    public static void mergeItemStacks(List<ItemStackEntry> target, List<ItemStackEntry> incoming) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (ItemStackEntry entry : target) {
            keys.add(entry.itemId + "\u0000" + entry.tagSnbt);
        }
        for (ItemStackEntry entry : copyItemStacks(incoming)) {
            if (keys.add(entry.itemId + "\u0000" + entry.tagSnbt)) {
                target.add(entry);
            }
        }
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
        public String tagSnbt = "";
        public List<String> itemFunctions = new ArrayList<>();
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
}
