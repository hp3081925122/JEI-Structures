package org.hp.jei_structures.jei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

public record StructureBiomeIcon(Identifier biomeId, List<String> dimensionIds, List<String> sourceSelectors) {

    public static final Codec<StructureBiomeIcon> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("biome_id").forGetter(StructureBiomeIcon::biomeId),
            Codec.STRING.listOf().optionalFieldOf("dimension_ids", List.of()).forGetter(StructureBiomeIcon::dimensionIds),
            Codec.STRING.listOf().optionalFieldOf("source_selectors", List.of()).forGetter(StructureBiomeIcon::sourceSelectors)
    ).apply(instance, StructureBiomeIcon::new));

    public StructureBiomeIcon {
        dimensionIds = dimensionIds != null ? List.copyOf(dimensionIds) : List.of();
        sourceSelectors = sourceSelectors != null ? List.copyOf(sourceSelectors) : List.of();
    }

    public StructureBiomeIcon(Identifier biomeId, List<String> dimensionIds) {
        this(biomeId, dimensionIds, List.of());
    }
}
