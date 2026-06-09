package org.hp.jei_structures.jei;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import org.hp.jei_structures.data.StructureIndexCache;

import java.util.List;
import java.util.stream.Collectors;

public final class StructureTextHelper {

    private StructureTextHelper() {
    }

    public static MutableComponent getStructureComponent(String structureId) {
        ResourceLocation id = ResourceLocation.tryParse(structureId);
        if (id == null) {
            return Component.literal(getFallbackName(structureId));
        }
        String translationKey = buildStructureTranslationKey(id);
        if (hasTranslation(translationKey)) {
            return Component.translatable(translationKey);
        }
        String structureTypeTranslationKey = buildStructureTypeTranslationKey(id);
        if (hasTranslation(structureTypeTranslationKey)) {
            return Component.translatable(structureTypeTranslationKey);
        }
        return Component.literal(getFallbackName(structureId));
    }

    public static String getStructureName(String structureId) {
        if (structureId == null || structureId.isBlank()) {
            return getFallbackName(structureId);
        }
        ResourceLocation id = ResourceLocation.tryParse(structureId);
        if (id == null) {
            return getFallbackName(structureId);
        }
        String translatedName = translate(buildStructureTranslationKey(id));
        if (!translatedName.isBlank()) {
            return translatedName;
        }
        translatedName = translate(buildStructureTypeTranslationKey(id));
        if (!translatedName.isBlank()) {
            return translatedName;
        }
        return getFallbackName(structureId);
    }

    public static String getStructureTypeName(String structureType) {
        if (structureType == null || structureType.isBlank()) {
            return getFallbackName(structureType);
        }
        ResourceLocation id = ResourceLocation.tryParse(structureType);
        if (id == null) {
            return getFallbackName(structureType);
        }
        String translatedName = translate(buildStructureTypeTranslationKey(id));
        if (!translatedName.isBlank()) {
            return translatedName;
        }
        translatedName = translate(buildStructureTranslationKey(id));
        if (!translatedName.isBlank()) {
            return translatedName;
        }
        return getFallbackName(structureType);
    }

    public static String getGenerationStepName(String generationStep) {
        if (generationStep == null || generationStep.isBlank()) {
            return getFallbackName(generationStep);
        }
        String translatedName = translate("jei_structures.generation_step." + generationStep);
        if (!translatedName.isBlank()) {
            return translatedName;
        }
        return getFallbackName(generationStep);
    }

    public static String getTerrainAdjustmentName(String terrainAdjustment) {
        if (terrainAdjustment == null || terrainAdjustment.isBlank()) {
            return translateOrFallback("jei_structures.terrain_adjustment.none", "none");
        }
        String translatedName = translate("jei_structures.terrain_adjustment." + terrainAdjustment);
        if (!translatedName.isBlank()) {
            return translatedName;
        }
        return getFallbackName(terrainAdjustment);
    }

    public static String getBiomeName(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return getFallbackName(biomeId);
        }
        ResourceLocation id = ResourceLocation.tryParse(biomeId);
        if (id != null) {
            String translatedName = translate("biome." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
            if (!translatedName.isBlank()) {
                return translatedName;
            }
        }
        return getFallbackName(biomeId);
    }

    public static String getDimensionName(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return getFallbackName(dimensionId);
        }
        ResourceLocation id = ResourceLocation.tryParse(dimensionId);
        if (id != null) {
            String translatedName = translate(getDimensionTranslationKey(id));
            if (!translatedName.isBlank()) {
                return translatedName;
            }
        }
        return getFallbackName(dimensionId);
    }

    public static String joinDimensionNames(List<String> dimensionIds, int limit) {
        return joinTranslated(dimensionIds, limit, StructureTextHelper::getDimensionName);
    }

    public static String joinRawValues(List<String> values, int limit) {
        return joinTranslated(values, limit, value -> value);
    }

    public static MutableComponent getBiomeComponent(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return Component.literal(getFallbackName(biomeId));
        }
        ResourceLocation id = ResourceLocation.tryParse(biomeId);
        if (id == null) {
            return Component.literal(getFallbackName(biomeId));
        }
        String translationKey = "biome." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        if (hasTranslation(translationKey)) {
            return Component.translatable(translationKey);
        }
        return Component.literal(getFallbackName(biomeId));
    }

    public static String joinBiomeNames(List<String> biomeIds, int limit) {
        return joinTranslated(biomeIds, limit, StructureTextHelper::getBiomeName);
    }

    public static String joinStructureNames(List<String> structureIds, int limit) {
        return joinTranslated(structureIds, limit, StructureTextHelper::getStructureName);
    }

    public static String joinEntityNames(List<String> entityIds, int limit) {
        return joinTranslated(entityIds, limit, StructureTextHelper::getEntityName);
    }

    public static String joinBlockNames(List<StructureIndexCache.LootBinding> bindings, int limit) {
        if (bindings == null || bindings.isEmpty()) {
            return "";
        }
        int safeLimit = Math.max(limit, 0);
        if (safeLimit == 0) {
            return "";
        }
        return bindings.stream()
                .filter(binding -> binding != null)
                .limit(safeLimit)
                .map(binding -> getBlockName(binding.blockId))
                .collect(Collectors.joining("、"));
    }

    public static String getEntityName(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return getFallbackName(entityId);
        }
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (id != null) {
            var entityType = BuiltInRegistries.ENTITY_TYPE.get(id);
            if (entityType != null) {
                return entityType.getDescription().getString();
            }
        }
        return getFallbackName(entityId);
    }

    public static String getBlockName(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return getFallbackName(blockId);
        }
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id != null) {
            var block = BuiltInRegistries.BLOCK.get(id);
            if (block != null) {
                return block.getName().getString();
            }
        }
        return getFallbackName(blockId);
    }

    public static String getItemName(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return getFallbackName(itemId);
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id != null) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != null) {
                return item.getDescription().getString();
            }
        }
        return getFallbackName(itemId);
    }

    public static MutableComponent component(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static String format(String key, Object... args) {
        return translate(key, args);
    }

    private static String joinTranslated(List<String> values, int limit, java.util.function.Function<String, String> translator) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        int safeLimit = Math.max(limit, 0);
        if (safeLimit == 0) {
            return "";
        }
        List<String> translated = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(translator)
                .toList();
        if (translated.isEmpty()) {
            return "";
        }
        if (translated.size() <= safeLimit) {
            return String.join("、", translated);
        }
        return translateOrFallback("jei_structures.common.list_more", String.join("、", translated.subList(0, safeLimit)), String.join("、", translated.subList(0, safeLimit)), translated.size());
    }

    private static String getFallbackName(String raw) {
        if (raw == null || raw.isBlank()) {
            return translateOrFallback("jei_structures.common.unknown", "unknown");
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        String value = id != null ? id.getPath() : raw;
        value = value.replace('/', ' ').replace('_', ' ').trim();
        if (value.isEmpty()) {
            return translateOrFallback("jei_structures.common.unknown", "unknown");
        }
        return value;
    }

    private static String buildStructureTranslationKey(ResourceLocation id) {
        return "structure." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    private static String buildStructureTypeTranslationKey(ResourceLocation id) {
        return "jei_structures.structure_type." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    private static String getDimensionTranslationKey(ResourceLocation id) {
        if ("minecraft".equals(id.getNamespace())) {
            if ("overworld".equals(id.getPath())) {
                return "jei_structures.dimension.minecraft.overworld";
            }
            if ("the_nether".equals(id.getPath()) || "nether".equals(id.getPath())) {
                return "jei_structures.dimension.minecraft.the_nether";
            }
            if ("the_end".equals(id.getPath()) || "end".equals(id.getPath())) {
                return "jei_structures.dimension.minecraft.the_end";
            }
        }
        return "dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    private static boolean hasTranslation(String key) {
        return I18n.exists(key);
    }

    private static String translate(String key, Object... args) {
        if (!hasTranslation(key)) {
            return "";
        }
        return I18n.get(key, args).trim();
    }

    private static String translateOrFallback(String key, String fallback, Object... args) {
        String translated = translate(key, args);
        return translated.isBlank() ? fallback : translated;
    }
}
