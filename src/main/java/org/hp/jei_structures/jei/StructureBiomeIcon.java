package org.hp.jei_structures.jei;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record StructureBiomeIcon(ResourceLocation biomeId, List<String> dimensionIds) {

    public StructureBiomeIcon {
        dimensionIds = dimensionIds != null ? List.copyOf(dimensionIds) : List.of();
    }
}
