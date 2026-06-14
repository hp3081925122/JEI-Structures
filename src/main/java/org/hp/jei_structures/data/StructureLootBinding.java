package org.hp.jei_structures.data;

import java.util.ArrayList;
import java.util.List;

public final class StructureLootBinding {

    public String structureId = "";
    public String blockId = "";
    public List<String> lootTables = new ArrayList<>();
    public List<String> items = new ArrayList<>();
    public List<StructureIndexCache.ItemStackSnapshot> itemStacks = new ArrayList<>();

    public StructureLootBinding copy() {
        StructureLootBinding copy = new StructureLootBinding();
        copy.structureId = structureId != null ? structureId : "";
        copy.blockId = blockId != null ? blockId : "";
        copy.lootTables = lootTables != null ? new ArrayList<>(lootTables) : new ArrayList<>();
        copy.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        if (itemStacks != null) {
            for (StructureIndexCache.ItemStackSnapshot itemStack : itemStacks) {
                if (itemStack == null) {
                    continue;
                }
                StructureIndexCache.ItemStackSnapshot stackCopy = new StructureIndexCache.ItemStackSnapshot();
                stackCopy.itemId = itemStack.itemId != null ? itemStack.itemId : "";
                stackCopy.stackTag = itemStack.stackTag != null ? itemStack.stackTag : "";
                copy.itemStacks.add(stackCopy);
            }
        }
        return copy;
    }
}
