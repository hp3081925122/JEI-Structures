package org.hp.jei_structures.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.hp.jei_structures.JeiStructures;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StructureBindingLoader {

    private static final String BINDINGS_ROOT = "bindings";

    private StructureBindingLoader() {
    }

    public static StructureBindingData loadAll(ResourceManager resourceManager) {
        StructureBindingData data = new StructureBindingData();
        loadStructureToMobsDir(resourceManager, "structure_to_mobs", data);
        loadReportStructureToMobsDir(resourceManager, "structure_to_mobs", data);
        loadMobToStructuresDir(resourceManager, "mob_to_structures", data);
        loadStructureLootBindingsDir(resourceManager, "structure_loot_bindings", data);
        loadReportStructureLootBindingsDir(resourceManager, "structure_loot_bindings", data);
        loadLootToStructuresDir(resourceManager, "loot_to_structures", data);
        loadConfigBindings(data);
        loadExportBindings(data);
        return data;
    }

    private static void loadExportBindings(StructureBindingData data) {
        Path root = StructureBindingPaths.getExportsRoot();
        loadExportStructureToMobsDir(root, data);
        loadExportStructureLootBindingsDir(root, data);
    }

    private static void loadExportStructureToMobsDir(Path root, StructureBindingData data) {
        for (JsonObject json : readExportJsonObjects(root, "structure_to_mobs")) {
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                List<String> entityIds = readStringArray(entry.getValue());
                if (entry.getKey().isBlank() || entityIds.isEmpty()) {
                    JeiStructures.LOGGER.warn("Skipped export mob binding with an empty structure id or entity list");
                    continue;
                }
                data.addExportMobBinding(entry.getKey(), entityIds);
            }
        }
    }

    private static void loadExportStructureLootBindingsDir(Path root, StructureBindingData data) {
        for (JsonObject json : readExportJsonObjects(root, "structure_loot_bindings")) {
            StructureLootBinding binding = parseStructureLootBinding(json);
            if (binding == null) {
                JeiStructures.LOGGER.warn("Skipped invalid export loot binding object");
                continue;
            }
            binding.captured = true;
            data.addExportLootBinding(binding.structureId, binding);
        }
    }

    private static void loadConfigBindings(StructureBindingData data) {
        loadStructureToMobsDir(StructureBindingPaths.getStructureToMobsDir(), data);
        loadMobToStructuresDir(StructureBindingPaths.getMobToStructuresDir(), data);
        loadStructureLootBindingsDir(StructureBindingPaths.getStructureLootBindingsDir(), data);
        loadLootToStructuresDir(StructureBindingPaths.getLootToStructuresDir(), data);
    }

    private static void loadStructureToMobsDir(ResourceManager resourceManager, String dirName, StructureBindingData data) {
        for (JsonObject json : readJsonObjects(resourceManager, dirName)) {
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                data.addMobBinding(entry.getKey(), readStringArray(entry.getValue()));
            }
        }
    }

    private static void loadStructureToMobsDir(Path dir, StructureBindingData data) {
        for (JsonObject json : readJsonObjects(dir)) {
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                data.addMobBinding(entry.getKey(), readStringArray(entry.getValue()));
            }
        }
    }

    private static void loadMobToStructuresDir(ResourceManager resourceManager, String dirName, StructureBindingData data) {
        for (JsonObject json : readJsonObjects(resourceManager, dirName)) {
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String entityId = entry.getKey();
                for (String structureId : readStringArray(entry.getValue())) {
                    data.addMobBinding(structureId, List.of(entityId));
                }
            }
        }
    }

    private static void loadMobToStructuresDir(Path dir, StructureBindingData data) {
        for (JsonObject json : readJsonObjects(dir)) {
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String entityId = entry.getKey();
                for (String structureId : readStringArray(entry.getValue())) {
                    data.addMobBinding(structureId, List.of(entityId));
                }
            }
        }
    }

    private static void loadReportStructureToMobsDir(ResourceManager resourceManager, String dirName, StructureBindingData data) {
        for (JsonObject json : readReportJsonObjects(resourceManager, dirName)) {
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                data.addMobBinding(entry.getKey(), readStringArray(entry.getValue()));
            }
        }
    }

    private static void loadStructureLootBindingsDir(ResourceManager resourceManager, String dirName, StructureBindingData data) {
        for (JsonObject json : readJsonObjects(resourceManager, dirName)) {
            StructureLootBinding binding = parseStructureLootBinding(json);
            if (binding == null) {
                continue;
            }
            data.addLootBinding(binding.structureId, binding);
        }
    }

    private static void loadStructureLootBindingsDir(Path dir, StructureBindingData data) {
        for (JsonObject json : readJsonObjects(dir)) {
            StructureLootBinding binding = parseStructureLootBinding(json);
            if (binding == null) {
                continue;
            }
            data.addLootBinding(binding.structureId, binding);
        }
    }

    private static void loadReportStructureLootBindingsDir(ResourceManager resourceManager, String dirName, StructureBindingData data) {
        for (JsonObject json : readReportJsonObjects(resourceManager, dirName)) {
            StructureLootBinding binding = parseStructureLootBinding(json);
            if (binding == null) {
                continue;
            }
            data.addLootBinding(binding.structureId, binding);
        }
    }

    private static void loadLootToStructuresDir(ResourceManager resourceManager, String dirName, StructureBindingData data) {
        for (JsonObject json : readJsonObjects(resourceManager, dirName)) {
            StructureLootBinding binding = parseStructureLootBinding(json);
            if (binding == null) {
                continue;
            }
            List<String> structureIds = readStringArray(json.get("structure_ids"));
            for (String structureId : structureIds) {
                StructureLootBinding copy = binding.copy();
                copy.structureId = structureId;
                data.addLootBinding(structureId, copy);
            }
        }
    }

    private static void loadLootToStructuresDir(Path dir, StructureBindingData data) {
        for (JsonObject json : readJsonObjects(dir)) {
            StructureLootBinding binding = parseStructureLootBinding(json);
            if (binding == null) {
                continue;
            }
            List<String> structureIds = readStringArray(json.get("structure_ids"));
            for (String structureId : structureIds) {
                StructureLootBinding copy = binding.copy();
                copy.structureId = structureId;
                data.addLootBinding(structureId, copy);
            }
        }
    }

    private static StructureLootBinding parseStructureLootBinding(JsonObject json) {
        if (json == null) {
            return null;
        }
        StructureLootBinding binding = new StructureLootBinding();
        binding.structureId = getString(json, "structure_id");
        binding.blockId = getString(json, "block_id");
        binding.lootTables = readStringArray(json.get("loot_tables"));
        binding.items = readStringArray(json.get("items"));
        binding.storedItems = readStringArray(json.get("stored_items"));
        binding.lootTableDetails = readLootTableDetails(json.get("loot_table_details"));
        binding.itemStacks = readItemStackSnapshots(json.get("item_stacks"));
        LinkedHashSet<String> items = new LinkedHashSet<>(binding.items);
        items.addAll(binding.storedItems);
        for (StructureIndexCache.ItemStackSnapshot snapshot : binding.itemStacks) {
            String itemId = ItemStackSnapshotHelper.snapshotItemId(snapshot);
            if (itemId != null && !itemId.isBlank()) {
                items.add(itemId);
            }
        }
        binding.items = new ArrayList<>(items);
        if (binding.structureId.isBlank()) {
            return null;
        }
        for (StructureIndexCache.LootTableDetail detail : binding.lootTableDetails) {
            if (detail != null && !detail.lootTableId.isBlank() && !binding.lootTables.contains(detail.lootTableId)) {
                binding.lootTables.add(detail.lootTableId);
            }
        }
        if (binding.lootTables.isEmpty() && binding.items.isEmpty() && binding.storedItems.isEmpty() && binding.itemStacks.isEmpty() && binding.lootTableDetails.isEmpty()) {
            return null;
        }
        return binding;
    }

    private static List<StructureIndexCache.LootTableDetail> readLootTableDetails(JsonElement element) {
        List<StructureIndexCache.LootTableDetail> result = new ArrayList<>();
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return result;
        }
        for (JsonElement child : element.getAsJsonArray()) {
            if (child == null || !child.isJsonObject()) {
                continue;
            }
            JsonObject object = child.getAsJsonObject();
            String lootTableId = getString(object, "loot_table_id");
            if (lootTableId.isBlank()) {
                continue;
            }
            StructureIndexCache.LootTableDetail detail = new StructureIndexCache.LootTableDetail();
            detail.lootTableId = lootTableId;
            detail.entries = readLootItemEntries(object.get("entries"));
            result.add(detail);
        }
        return result;
    }

    private static List<StructureIndexCache.LootItemEntry> readLootItemEntries(JsonElement element) {
        List<StructureIndexCache.LootItemEntry> result = new ArrayList<>();
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return result;
        }
        for (JsonElement child : element.getAsJsonArray()) {
            if (child == null || !child.isJsonObject()) {
                continue;
            }
            JsonObject object = child.getAsJsonObject();
            StructureIndexCache.LootItemEntry entry = new StructureIndexCache.LootItemEntry();
            entry.itemId = getString(object, "item_id");
            entry.itemStackTag = getString(object, "item_stack_tag");
            entry.weight = getInt(object, "weight", 0);
            entry.quality = getInt(object, "quality", 0);
            entry.rollsText = getString(object, "rolls_text");
            entry.bonusRollsText = getString(object, "bonus_rolls_text");
            entry.chanceText = getString(object, "chance_text");
            entry.countText = getString(object, "count_text");
            entry.chanceNotes = readLootTextEntries(object.get("chance_notes"));
            entry.countNotes = readLootTextEntries(object.get("count_notes"));
            if (!entry.itemId.isBlank()) {
                result.add(entry);
            }
        }
        return result;
    }

    private static List<StructureIndexCache.LootTextEntry> readLootTextEntries(JsonElement element) {
        List<StructureIndexCache.LootTextEntry> result = new ArrayList<>();
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return result;
        }
        for (JsonElement child : element.getAsJsonArray()) {
            if (child == null || !child.isJsonObject()) {
                continue;
            }
            JsonObject object = child.getAsJsonObject();
            String translationKey = getString(object, "translation_key");
            if (translationKey.isBlank()) {
                continue;
            }
            StructureIndexCache.LootTextEntry entry = new StructureIndexCache.LootTextEntry();
            entry.translationKey = translationKey;
            entry.args = new ArrayList<>(readStringArray(object.get("args")));
            result.add(entry);
        }
        return result;
    }

    private static List<JsonObject> readJsonObjects(ResourceManager resourceManager, String dirName) {
        List<JsonObject> result = new ArrayList<>();
        Identifier root = Identifier.fromNamespaceAndPath(JeiStructures.MODID, BINDINGS_ROOT + "/" + dirName);
        try {
            Map<Identifier, Resource> resources = resourceManager.listResources(BINDINGS_ROOT + "/" + dirName, path -> path.getPath().endsWith(".json"));
            resources.keySet().stream()
                    .sorted()
                    .forEach(location -> readJsonResource(resourceManager, location, result));
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to read datapack structure binding directory: {}", root, exception);
        }
        return result;
    }

    private static List<JsonObject> readReportJsonObjects(ResourceManager resourceManager, String dirName) {
        List<JsonObject> result = new ArrayList<>();
        try {
            Map<Identifier, Resource> resources = resourceManager.listResources("reports", path -> isReportBindingPath(path, dirName));
            resources.keySet().stream()
                    .sorted()
                    .forEach(location -> readJsonResource(resourceManager, location, result));
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to read datapack report structure binding directory: reports/{}", dirName, exception);
        }
        return result;
    }

    private static List<JsonObject> readJsonObjects(Path dir) {
        List<JsonObject> result = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return result;
        }
        try (var paths = Files.walk(dir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> readJsonFile(path, result));
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to read local structure binding directory: {}", dir, exception);
        }
        return result;
    }

    private static List<JsonObject> readExportJsonObjects(Path root, String dirName) {
        List<JsonObject> result = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) {
            return result;
        }
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> containsDirectory(root, path, dirName))
                    .sorted()
                    .forEach(path -> readJsonFile(path, result));
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to read local structure export directory: {}", root, exception);
        }
        return result;
    }

    private static boolean containsDirectory(Path root, Path path, String directoryName) {
        Path relative = root.relativize(path);
        for (int index = 0; index < relative.getNameCount() - 1; index++) {
            if (directoryName.equals(relative.getName(index).toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReportBindingPath(Identifier location, String dirName) {
        if (location == null || location.getPath() == null || !location.getPath().endsWith(".json")) {
            return false;
        }
        String[] segments = location.getPath().split("/");
        return segments.length >= 5 && "reports".equals(segments[0]) && dirName.equals(segments[2]);
    }

    private static void readJsonResource(ResourceManager resourceManager, Identifier location, List<JsonObject> result) {
        try {
            Optional<Resource> resource = resourceManager.getResource(location);
            if (resource.isEmpty()) {
                return;
            }
            try (Reader reader = resource.get().openAsReader()) {
                addJsonElements(JsonParser.parseReader(reader), result);
            }
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to parse datapack structure binding file: {}", location, exception);
        }
    }

    private static void readJsonFile(Path path, List<JsonObject> result) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            addJsonElements(JsonParser.parseReader(reader), result);
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to parse local structure binding file: {}", path, exception);
        }
    }

    private static void addJsonElements(JsonElement element, List<JsonObject> result) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            result.add(element.getAsJsonObject());
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                if (child != null && child.isJsonObject()) {
                    result.add(child.getAsJsonObject());
                }
            }
        }
    }

    private static List<String> readStringArray(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (child != null && child.isJsonPrimitive()) {
                String value = child.getAsString();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    private static List<StructureIndexCache.ItemStackSnapshot> readItemStackSnapshots(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return List.of();
        }
        List<StructureIndexCache.ItemStackSnapshot> result = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (child == null || !child.isJsonObject()) {
                continue;
            }
            JsonObject json = child.getAsJsonObject();
            StructureIndexCache.ItemStackSnapshot snapshot = new StructureIndexCache.ItemStackSnapshot();
            snapshot.itemId = getString(json, "item_id");
            snapshot.stackTag = getString(json, "stack_tag");
            if (!ItemStackSnapshotHelper.isEmptySnapshot(snapshot)) {
                result.add(snapshot);
            }
        }
        return List.copyOf(result);
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception exception) {
            return fallback;
        }
    }
}
