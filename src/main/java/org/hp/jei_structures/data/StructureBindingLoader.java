package org.hp.jei_structures.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
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
        loadLocalReports(data);
        return data;
    }

    private static void loadConfigBindings(StructureBindingData data) {
        loadStructureToMobsDir(StructureBindingPaths.getStructureToMobsDir(), data);
        loadMobToStructuresDir(StructureBindingPaths.getMobToStructuresDir(), data);
        loadStructureLootBindingsDir(StructureBindingPaths.getStructureLootBindingsDir(), data);
        loadLootToStructuresDir(StructureBindingPaths.getLootToStructuresDir(), data);
    }

    private static void loadLocalReports(StructureBindingData data) {
        loadLocalReportStructureToMobsDir(StructureBindingPaths.getReportsRoot(), data);
        loadLocalReportStructureLootBindingsDir(StructureBindingPaths.getReportsRoot(), data);
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

    private static void loadLocalReportStructureToMobsDir(Path reportsRoot, StructureBindingData data) {
        for (JsonObject json : readLocalReportJsonObjects(reportsRoot, "structure_to_mobs")) {
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

    private static void loadLocalReportStructureLootBindingsDir(Path reportsRoot, StructureBindingData data) {
        for (JsonObject json : readLocalReportJsonObjects(reportsRoot, "structure_loot_bindings")) {
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
        if (binding.structureId.isBlank()) {
            return null;
        }
        if (binding.lootTables.isEmpty() && binding.items.isEmpty()) {
            return null;
        }
        return binding;
    }

    private static List<JsonObject> readJsonObjects(ResourceManager resourceManager, String dirName) {
        List<JsonObject> result = new ArrayList<>();
        ResourceLocation root = ResourceLocation.fromNamespaceAndPath(JeiStructures.MODID, BINDINGS_ROOT + "/" + dirName);
        try {
            Map<ResourceLocation, Resource> resources = resourceManager.listResources(BINDINGS_ROOT + "/" + dirName, path -> path.getPath().endsWith(".json"));
            resources.keySet().stream()
                    .sorted()
                    .forEach(location -> readJsonResource(resourceManager, location, result));
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("读取数据包结构绑定目录失败：{}", root, exception);
        }
        return result;
    }

    private static List<JsonObject> readReportJsonObjects(ResourceManager resourceManager, String dirName) {
        List<JsonObject> result = new ArrayList<>();
        try {
            Map<ResourceLocation, Resource> resources = resourceManager.listResources("reports", path -> isReportBindingPath(path, dirName));
            resources.keySet().stream()
                    .sorted()
                    .forEach(location -> readJsonResource(resourceManager, location, result));
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("读取数据包报告结构绑定目录失败：reports/{}", dirName, exception);
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
            JeiStructures.LOGGER.warn("读取本地结构绑定目录失败：{}", dir, exception);
        }
        return result;
    }

    private static List<JsonObject> readLocalReportJsonObjects(Path reportsRoot, String dirName) {
        List<JsonObject> result = new ArrayList<>();
        if (reportsRoot == null || !Files.isDirectory(reportsRoot)) {
            return result;
        }
        try (var paths = Files.walk(reportsRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> isLocalReportBindingPath(reportsRoot, path, dirName))
                    .sorted()
                    .forEach(path -> readJsonFile(path, result));
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("读取本地结构报告目录失败：{}/{}", reportsRoot, dirName, exception);
        }
        return result;
    }

    private static boolean isReportBindingPath(ResourceLocation location, String dirName) {
        if (location == null || location.getPath() == null || !location.getPath().endsWith(".json")) {
            return false;
        }
        String[] segments = location.getPath().split("/");
        return segments.length >= 5 && "reports".equals(segments[0]) && dirName.equals(segments[2]);
    }

    private static boolean isLocalReportBindingPath(Path reportsRoot, Path path, String dirName) {
        if (reportsRoot == null || path == null || dirName == null || dirName.isBlank()) {
            return false;
        }
        Path relativePath;
        try {
            relativePath = reportsRoot.relativize(path);
        } catch (Exception exception) {
            return false;
        }
        return relativePath.getNameCount() >= 4 && dirName.equals(relativePath.getName(1).toString());
    }

    private static void readJsonResource(ResourceManager resourceManager, ResourceLocation location, List<JsonObject> result) {
        try {
            Optional<Resource> resource = resourceManager.getResource(location);
            if (resource.isEmpty()) {
                return;
            }
            try (Reader reader = resource.get().openAsReader()) {
                addJsonElements(JsonParser.parseReader(reader), result);
            }
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("解析数据包结构绑定文件失败：{}", location, exception);
        }
    }

    private static void readJsonFile(Path path, List<JsonObject> result) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            addJsonElements(JsonParser.parseReader(reader), result);
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("解析本地结构绑定文件失败：{}", path, exception);
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

    private static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return "";
        }
        return object.get(key).getAsString();
    }
}
