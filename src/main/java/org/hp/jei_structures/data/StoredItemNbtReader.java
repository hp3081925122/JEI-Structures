package org.hp.jei_structures.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class StoredItemNbtReader {

    private StoredItemNbtReader() {
    }

    public static LinkedHashSet<String> readStoredItems(CompoundTag blockEntity) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (StructureIndexCache.ItemStackEntry stack : readStoredItemStacks(blockEntity)) {
            items.add(stack.itemId);
        }
        return items;
    }

    public static List<StructureIndexCache.ItemStackEntry> readStoredItemStacks(CompoundTag blockEntity) {
        Map<String, StructureIndexCache.ItemStackEntry> items = new LinkedHashMap<>();
        if (blockEntity != null) {
            collectStoredItemsFromList(blockEntity, "Items", items);
            collectStoredItemsFromList(blockEntity, "items", items);
            collectStoredItemFromCompound(blockEntity, "Item", items);
            collectStoredItemFromCompound(blockEntity, "item", items);
        }
        return new ArrayList<>(items.values());
    }

    private static void collectStoredItemsFromList(CompoundTag blockEntity, String key, Map<String, StructureIndexCache.ItemStackEntry> items) {
        if (!blockEntity.contains(key, Tag.TAG_LIST)) {
            return;
        }
        ListTag listTag = blockEntity.getList(key, Tag.TAG_COMPOUND);
        for (int index = 0; index < listTag.size(); index++) {
            if (listTag.get(index) instanceof CompoundTag itemTag) {
                addStoredItem(itemTag, items);
            }
        }
    }

    private static void collectStoredItemFromCompound(CompoundTag blockEntity, String key, Map<String, StructureIndexCache.ItemStackEntry> items) {
        if (!blockEntity.contains(key, Tag.TAG_COMPOUND)) {
            return;
        }
        addStoredItem(blockEntity.getCompound(key), items);
    }

    private static void addStoredItem(CompoundTag itemTag, Map<String, StructureIndexCache.ItemStackEntry> items) {
        if (!itemTag.contains("id", Tag.TAG_STRING)) {
            return;
        }
        String itemId = itemTag.getString("id");
        if (itemId == null || itemId.isBlank() || "minecraft:air".equals(itemId)) {
            return;
        }
        StructureIndexCache.ItemStackEntry entry = new StructureIndexCache.ItemStackEntry();
        entry.itemId = itemId;
        entry.tagSnbt = itemTag.contains("tag", Tag.TAG_COMPOUND) ? itemTag.getCompound("tag").toString() : "";
        items.putIfAbsent(itemId + "\u0000" + entry.tagSnbt, entry);
    }
}
