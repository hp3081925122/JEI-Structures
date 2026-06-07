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
            JeiStructures.LOGGER.warn("读取特殊信息目录失败：{}", root, exception);
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
            JeiStructures.LOGGER.warn("解析特殊信息文件失败：{}", location, exception);
        }
    }

    private static void readEntry(JsonObject json, boolean entity, StructureSpecialInfoData data) {
        if (json == null) {
            return;
        }
        String translationKey = getString(json, "translation_key");
        if (translationKey.isBlank()) {
            JeiStructures.LOGGER.warn("跳过特殊信息条目，缺少 translation_key：{}", json);
            return;
        }
        if (entity) {
            String entityId = getString(json, "entity_id");
            if (entityId.isBlank()) {
                JeiStructures.LOGGER.warn("跳过特殊实体条目，缺少 entity_id：{}", json);
                return;
            }
            data.addEntityTranslation(entityId, translationKey);
        } else {
            String blockId = getString(json, "block_id");
            if (blockId.isBlank()) {
                JeiStructures.LOGGER.warn("跳过特殊方块条目，缺少 block_id：{}", json);
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
