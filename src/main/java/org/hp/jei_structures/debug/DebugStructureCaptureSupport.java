package org.hp.jei_structures.debug;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class DebugStructureCaptureSupport {

    public static final int MAX_SPEED_MULTIPLIER = 32;

    private DebugStructureCaptureSupport() {
    }

    public static Component getStructureDisplayComponent(String structureId) {
        ResourceLocation id = structureId == null || structureId.isBlank() ? null : ResourceLocation.tryParse(structureId);
        if (id == null) {
            return structureId == null || structureId.isBlank()
                    ? Component.translatable("jei_structures.common.unknown")
                    : Component.literal(structureId);
        }
        String translationKey = "structure." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return Component.translatable(translationKey);
    }

    public static Component getStructureDisplayComponent(ResourceLocation structureId) {
        return getStructureDisplayComponent(structureId != null ? structureId.toString() : "");
    }
}
