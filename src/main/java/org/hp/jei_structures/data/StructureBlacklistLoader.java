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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class StructureBlacklistLoader {

    private static final String ROOT = "structure_blacklists";

    private StructureBlacklistLoader() {
    }

    public static StructureBlacklistData loadAll(ResourceManager resourceManager) {
        StructureBlacklistData data = new StructureBlacklistData();
        try {
            Map<Identifier, Resource> resources = resourceManager.listResources(ROOT, location -> location.getPath().endsWith(".json"));
            resources.keySet().stream()
                    .sorted()
                    .forEach(location -> readResource(resourceManager, location, data));
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to read structure blacklist directory: {}", ROOT, exception);
        }
        return data;
    }

    private static void readResource(ResourceManager resourceManager, Identifier location, StructureBlacklistData data) {
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

    private static void readEntry(JsonObject json, Identifier location, StructureBlacklistData data) {
        Set<String> lootTables = readIdentifierSet(json.get("loot_tables"));
        Set<String> blocks = readIdentifierSet(json.get("blocks"));
        Set<String> containers = readIdentifierSet(json.get("containers"));
        Set<String> entities = readIdentifierSet(json.get("entities"));
        if (lootTables.isEmpty() && blocks.isEmpty() && containers.isEmpty() && entities.isEmpty()) {
            JeiStructures.LOGGER.warn("Skipped structure blacklist entry because no blacklist values were provided: {} in {}", json, location);
            return;
        }
        boolean added = false;
        if (getBoolean(json, "all_structures")) {
            data.addGlobalRule(lootTables, blocks, containers, entities);
            added = true;
        }
        String structureId = getString(json, "structure_id");
        if (!structureId.isBlank()) {
            if (Identifier.tryParse(structureId) != null) {
                data.addStructureRule(structureId, lootTables, blocks, containers, entities);
                added = true;
            } else {
                JeiStructures.LOGGER.warn("Skipped invalid structure_id in blacklist entry: {} in {}", structureId, location);
            }
        }
        for (String id : readIdentifierSet(json.get("structure_ids"))) {
            data.addStructureRule(id, lootTables, blocks, containers, entities);
            added = true;
        }
        if (!added) {
            JeiStructures.LOGGER.warn("Skipped structure blacklist entry because no valid scope was provided: {} in {}", json, location);
        }
    }

    private static Set<String> readIdentifierSet(JsonElement element) {
        Set<String> result = new LinkedHashSet<>();
        if (element == null || element.isJsonNull()) {
            return result;
        }
        if (element.isJsonPrimitive()) {
            addIdentifier(result, element.getAsString());
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                if (child != null && child.isJsonPrimitive()) {
                    addIdentifier(result, child.getAsString());
                }
            }
        }
        return result;
    }

    private static void addIdentifier(Set<String> result, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Identifier id = Identifier.tryParse(value);
        if (id != null) {
            result.add(id.toString());
        }
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static boolean getBoolean(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
    }
}
