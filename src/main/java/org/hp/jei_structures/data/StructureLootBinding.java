package org.hp.jei_structures.data;

import java.util.ArrayList;
import java.util.List;

public final class StructureLootBinding {

    public String structureId = "";
    public String blockId = "";
    public List<String> lootTables = new ArrayList<>();
    public List<String> items = new ArrayList<>();

    public StructureLootBinding copy() {
        StructureLootBinding copy = new StructureLootBinding();
        copy.structureId = structureId != null ? structureId : "";
        copy.blockId = blockId != null ? blockId : "";
        copy.lootTables = lootTables != null ? new ArrayList<>(lootTables) : new ArrayList<>();
        copy.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        return copy;
    }
}
