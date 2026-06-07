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
import java.util.Map;
import java.util.Optional;

public final class StructureSpecialInfoLoader {

    private static final String ENTITIES_ROOT = "structure_special_entities";
    private static final String BLOCKS_ROOT = "structure_special_blocks";

    private StructureSpecialInfoLoader() {
    }

    public static StructureSpecialInfoData loadAll(ResourceManager resourceManager) {
        StructureSpecialInfoData data = new StructureSpecialInfoData();
        loadDirectory(resourceManager, ENTITIES_ROOT, true, data);
        loadDirectory(resourceManager, BLOCKS_ROOT, false, data);
        return data;
    }

    private static void loadDirectory(ResourceManager resourceManager, String root, boolean entity, StructureSpecialInfoData data) {
        try {
            Map<ResourceLocation, Resource> resources = resourceManager.listResources(root, location -> location.getPath().endsWith(".json"));
            resources.keySet().stream()
                    .sorted()
                    .forEach(location -> readResource(resourceManager, location, entity, data));
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to read special info directory: {}", root, exception);
        }
    }

    private static void readResource(ResourceManager resourceManager, ResourceLocation location, boolean entity, StructureSpecialInfoData data) {
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
                    readEntry(element.getAsJsonObject(), entity, data);
                    return;
                }
                if (element.isJsonArray()) {
                    JsonArray array = element.getAsJsonArray();
                    for (JsonElement child : array) {
                        if (child != null && child.isJsonObject()) {
                            readEntry(child.getAsJsonObject(), entity, data);
                        }
                    }
                }
            }
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to parse special info file: {}", location, exception);
        }
    }

    private static void readEntry(JsonObject json, boolean entity, StructureSpecialInfoData data) {
        if (json == null) {
            return;
        }
        String translationKey = getString(json, "translation_key");
        if (translationKey.isBlank()) {
            JeiStructures.LOGGER.warn("Skipped special info entry because translation_key is missing: {}", json);
            return;
        }
        if (entity) {
            String entityId = getString(json, "entity_id");
            if (entityId.isBlank()) {
                JeiStructures.LOGGER.warn("Skipped special entity info entry because entity_id is missing: {}", json);
                return;
            }
            data.addEntityTranslation(entityId, translationKey);
        } else {
            String blockId = getString(json, "block_id");
            if (blockId.isBlank()) {
                JeiStructures.LOGGER.warn("Skipped special block info entry because block_id is missing: {}", json);
                return;
            }
            data.addBlockTranslation(blockId, translationKey);
        }
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return "";
        }
        return object.get(key).getAsString();
    }
}
