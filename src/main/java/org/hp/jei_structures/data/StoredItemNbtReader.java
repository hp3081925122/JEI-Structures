package org.hp.jei_structures.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;

public final class StoredItemNbtReader {

    private StoredItemNbtReader() {
    }

    public static LinkedHashSet<String> readStoredItems(CompoundTag blockEntity) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        if (blockEntity == null) {
            return items;
        }
        collectStoredItemsFromList(blockEntity, "Items", items);
        collectStoredItemsFromList(blockEntity, "items", items);
        collectStoredItemFromCompound(blockEntity, "Item", items);
        collectStoredItemFromCompound(blockEntity, "item", items);
        return items;
    }

    private static void collectStoredItemsFromList(CompoundTag blockEntity, String key, Set<String> items) {
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

    private static void collectStoredItemFromCompound(CompoundTag blockEntity, String key, Set<String> items) {
        if (!blockEntity.contains(key, Tag.TAG_COMPOUND)) {
            return;
        }
        addStoredItem(blockEntity.getCompound(key), items);
    }

    private static void addStoredItem(CompoundTag itemTag, Set<String> items) {
        if (!itemTag.contains("id", Tag.TAG_STRING)) {
            return;
        }
        String itemId = itemTag.getString("id");
        if (itemId == null || itemId.isBlank() || "minecraft:air".equals(itemId)) {
            return;
        }
        items.add(itemId);
    }
}
