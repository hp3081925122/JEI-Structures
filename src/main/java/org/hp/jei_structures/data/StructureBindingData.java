package org.hp.jei_structures.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StructureBindingData {

    private final Map<String, List<String>> structureToMobs = new LinkedHashMap<>();
    private final Map<String, List<StructureLootBinding>> structureToLootBindings = new LinkedHashMap<>();

    public Map<String, List<String>> getStructureToMobs() {
        return structureToMobs;
    }

    public Map<String, List<StructureLootBinding>> getStructureToLootBindings() {
        return structureToLootBindings;
    }

    public void addMobBinding(String structureId, List<String> entityIds) {
        if (structureId == null || structureId.isBlank() || entityIds == null || entityIds.isEmpty()) {
            return;
        }
        structureToMobs.computeIfAbsent(structureId, key -> new ArrayList<>()).addAll(entityIds);
    }

    public void addLootBinding(String structureId, StructureLootBinding binding) {
        if (structureId == null || structureId.isBlank() || binding == null) {
            return;
        }
        structureToLootBindings.computeIfAbsent(structureId, key -> new ArrayList<>()).add(binding.copy());
    }

    public void merge(StructureBindingData other) {
        if (other == null) {
            return;
        }
        other.structureToMobs.forEach(this::addMobBinding);
        other.structureToLootBindings.forEach((structureId, bindings) -> {
            for (StructureLootBinding binding : bindings) {
                addLootBinding(structureId, binding);
            }
        });
    }
}
