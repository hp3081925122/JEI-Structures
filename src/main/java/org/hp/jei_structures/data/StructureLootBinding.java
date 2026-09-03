package org.hp.jei_structures.data;

import java.util.ArrayList;
import java.util.List;

public final class StructureLootBinding {

    public String structureId = "";
    public String blockId = "";
    public List<String> lootTables = new ArrayList<>();
    public List<String> items = new ArrayList<>();
    public List<String> storedItems = new ArrayList<>();
    public List<StructureIndexCache.LootTableDetail> lootTableDetails = new ArrayList<>();
    public boolean captured;

    public StructureLootBinding copy() {
        StructureLootBinding copy = new StructureLootBinding();
        copy.structureId = structureId != null ? structureId : "";
        copy.blockId = blockId != null ? blockId : "";
        copy.lootTables = lootTables != null ? new ArrayList<>(lootTables) : new ArrayList<>();
        copy.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        copy.storedItems = storedItems != null ? new ArrayList<>(storedItems) : new ArrayList<>();
        copy.captured = captured;
        if (lootTableDetails != null) {
            for (StructureIndexCache.LootTableDetail detail : lootTableDetails) {
                copy.lootTableDetails.add(copyLootTableDetail(detail));
            }
        }
        return copy;
    }

    private static StructureIndexCache.LootTableDetail copyLootTableDetail(StructureIndexCache.LootTableDetail source) {
        StructureIndexCache.LootTableDetail copy = new StructureIndexCache.LootTableDetail();
        if (source == null) {
            return copy;
        }
        copy.lootTableId = source.lootTableId != null ? source.lootTableId : "";
        if (source.entries != null) {
            for (StructureIndexCache.LootItemEntry sourceEntry : source.entries) {
                if (sourceEntry == null) {
                    continue;
                }
                StructureIndexCache.LootItemEntry entry = new StructureIndexCache.LootItemEntry();
                entry.itemId = sourceEntry.itemId != null ? sourceEntry.itemId : "";
                entry.weight = sourceEntry.weight;
                entry.quality = sourceEntry.quality;
                entry.rollsText = sourceEntry.rollsText != null ? sourceEntry.rollsText : "";
                entry.bonusRollsText = sourceEntry.bonusRollsText != null ? sourceEntry.bonusRollsText : "";
                entry.chanceText = sourceEntry.chanceText != null ? sourceEntry.chanceText : "";
                entry.countText = sourceEntry.countText != null ? sourceEntry.countText : "";
                entry.chanceNotes = copyTextEntries(sourceEntry.chanceNotes);
                entry.countNotes = copyTextEntries(sourceEntry.countNotes);
                copy.entries.add(entry);
            }
        }
        return copy;
    }

    private static List<StructureIndexCache.LootTextEntry> copyTextEntries(List<StructureIndexCache.LootTextEntry> source) {
        List<StructureIndexCache.LootTextEntry> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (StructureIndexCache.LootTextEntry sourceEntry : source) {
            if (sourceEntry == null) {
                continue;
            }
            StructureIndexCache.LootTextEntry entry = new StructureIndexCache.LootTextEntry();
            entry.translationKey = sourceEntry.translationKey != null ? sourceEntry.translationKey : "";
            entry.args = sourceEntry.args != null ? new ArrayList<>(sourceEntry.args) : new ArrayList<>();
            copy.add(entry);
        }
        return copy;
    }
}
