package org.hp.jei_structures.jei;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record StructureBiomeIcon(ResourceLocation biomeId, List<String> dimensionIds, List<String> sourceSelectors) {

    public StructureBiomeIcon {
        dimensionIds = dimensionIds != null ? List.copyOf(dimensionIds) : List.of();
        sourceSelectors = sourceSelectors != null ? List.copyOf(sourceSelectors) : List.of();
    }

    public StructureBiomeIcon(ResourceLocation biomeId, List<String> dimensionIds) {
        this(biomeId, dimensionIds, List.of());
    }
}
