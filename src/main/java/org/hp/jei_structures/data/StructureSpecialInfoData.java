package org.hp.jei_structures.data;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StructureSpecialInfoData {

    private final Map<String, String> entityTranslations = new LinkedHashMap<>();
    private final Map<String, String> blockTranslations = new LinkedHashMap<>();

    public Map<String, String> getEntityTranslations() {
        return entityTranslations;
    }

    public Map<String, String> getBlockTranslations() {
        return blockTranslations;
    }

    public void addEntityTranslation(String entityId, String translationKey) {
        if (entityId == null || entityId.isBlank() || translationKey == null || translationKey.isBlank()) {
            return;
        }
        entityTranslations.put(entityId, translationKey);
    }

    public void addBlockTranslation(String blockId, String translationKey) {
        if (blockId == null || blockId.isBlank() || translationKey == null || translationKey.isBlank()) {
            return;
        }
        blockTranslations.put(blockId, translationKey);
    }

    public void merge(StructureSpecialInfoData other) {
        if (other == null) {
            return;
        }
        entityTranslations.putAll(other.entityTranslations);
        blockTranslations.putAll(other.blockTranslations);
    }
}
