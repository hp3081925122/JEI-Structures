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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StructureBlacklistLoader {

    private static final String ROOT = "structure_blacklists";

    private StructureBlacklistLoader() {
    }

    public static StructureBlacklistData loadAll(ResourceManager resourceManager) {
        StructureBlacklistData data = new StructureBlacklistData();
        try {
            Map<ResourceLocation, Resource> resources = resourceManager.listResources(ROOT, location -> location.getPath().endsWith(".json"));
            resources.keySet().stream()
                    .sorted()
                    .forEach(location -> readResource(resourceManager, location, data));
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to read structure blacklist directory: {}", ROOT, exception);
        }
        return data;
    }

    private static void readResource(ResourceManager resourceManager, ResourceLocation location, StructureBlacklistData data) {
        try {
            Optional<Resource> resource = resourceManager.getResource(location);
            if (resource.isEmpty()) {
                return;
            }
            try (Reader reader = resource.get().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element == null || element.isJsonNull()) {
                    return;
                }
                if (element.isJsonObject()) {
                    readEntry(element.getAsJsonObject(), location, data);
                    return;
                }
                if (element.isJsonArray()) {
                    JsonArray array = element.getAsJsonArray();
                    for (JsonElement child : array) {
                        if (child != null && child.isJsonObject()) {
                            readEntry(child.getAsJsonObject(), location, data);
                        }
                    }
                }
            }
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to parse structure blacklist file: {}", location, exception);
        }
    }

    private static void readEntry(JsonObject json, ResourceLocation location, StructureBlacklistData data) {
        StructureBlacklistData.Rule rule = new StructureBlacklistData.Rule();
        readIds(json, "loot_tables").forEach(rule::addLootTable);
        readIds(json, "blocks").forEach(rule::addBlock);
        readIds(json, "containers").forEach(rule::addContainer);
        readIds(json, "entities").forEach(rule::addEntity);
        if (rule.isEmpty()) {
            JeiStructures.LOGGER.warn("Skipped empty structure blacklist entry in {}: {}", location, json);
            return;
        }

        boolean allStructures = getBoolean(json, "all_structures");
        List<String> structureIds = new ArrayList<>();
        String structureId = getString(json, "structure_id");
        if (!structureId.isBlank()) {
            structureIds.add(structureId);
        }
        structureIds.addAll(readIds(json, "structure_ids"));

        if (allStructures) {
            data.addGlobalRule(rule);
        }
        for (String id : structureIds) {
            data.addStructureRule(id, rule);
        }
        if (!allStructures && structureIds.isEmpty()) {
            JeiStructures.LOGGER.warn("Skipped structure blacklist entry without scope in {}: {}", location, json);
        }
    }

    private static List<String> readIds(JsonObject json, String key) {
        List<String> result = new ArrayList<>();
        if (json == null || !json.has(key)) {
            return result;
        }
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return result;
        }
        if (element.isJsonPrimitive()) {
            addId(result, element.getAsString());
            return result;
        }
        if (!element.isJsonArray()) {
            return result;
        }
        for (JsonElement child : element.getAsJsonArray()) {
            if (child != null && child.isJsonPrimitive()) {
                addId(result, child.getAsString());
            }
        }
        return result;
    }

    private static void addId(List<String> result, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (ResourceLocation.tryParse(value) == null) {
            JeiStructures.LOGGER.warn("Skipped invalid structure blacklist id: {}", value);
            return;
        }
        result.add(value);
    }

    private static String getString(JsonObject json, String key) {
        if (json == null || !json.has(key) || !json.get(key).isJsonPrimitive()) {
            return "";
        }
        return json.get(key).getAsString();
    }

    private static boolean getBoolean(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsBoolean();
    }
}
