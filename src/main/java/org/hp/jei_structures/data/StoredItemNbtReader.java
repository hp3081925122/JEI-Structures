package org.hp.jei_structures.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class StoredItemNbtReader {

    private StoredItemNbtReader() {
    }

    public static LinkedHashSet<String> readStoredItems(CompoundTag blockEntity) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (StructureIndexCache.ItemStackSnapshot snapshot : readStoredItemSnapshots(blockEntity)) {
            String itemId = ItemStackSnapshotHelper.snapshotItemId(snapshot);
            if (itemId != null && !itemId.isBlank() && !"minecraft:air".equals(itemId)) {
                items.add(itemId);
            }
        }
        return items;
    }

    public static List<StructureIndexCache.ItemStackSnapshot> readStoredItemSnapshots(CompoundTag blockEntity) {
        List<StructureIndexCache.ItemStackSnapshot> items = new ArrayList<>();
        if (blockEntity == null) {
            return items;
        }
        collectStoredItemSnapshotsFromList(blockEntity, "Items", items);
        collectStoredItemSnapshotsFromList(blockEntity, "items", items);
        collectStoredItemSnapshotFromCompound(blockEntity, "Item", items);
        collectStoredItemSnapshotFromCompound(blockEntity, "item", items);
        return items;
    }

    public static StructureIndexCache.ItemStackSnapshot readItemSnapshot(CompoundTag itemTag) {
        if (itemTag == null) {
            return null;
        }
        StructureIndexCache.ItemStackSnapshot snapshot = new StructureIndexCache.ItemStackSnapshot();
        if (itemTag.contains("id")) {
            snapshot.itemId = itemTag.getStringOr("id", "");
        }
        snapshot.stackTag = itemTag.toString();
        return ItemStackSnapshotHelper.isEmptySnapshot(snapshot) ? null : snapshot;
    }

    private static void collectStoredItemSnapshotsFromList(CompoundTag blockEntity, String key, List<StructureIndexCache.ItemStackSnapshot> items) {
        if (!blockEntity.contains(key)) {
            return;
        }
        Optional<ListTag> optionalListTag = blockEntity.getList(key);
        if (optionalListTag.isEmpty()) {
            return;
        }
        ListTag listTag = optionalListTag.get();
        for (int index = 0; index < listTag.size(); index++) {
            if (listTag.get(index) instanceof CompoundTag itemTag) {
                addStoredItemSnapshot(itemTag, items);
            }
        }
    }

    private static void collectStoredItemSnapshotFromCompound(CompoundTag blockEntity, String key, List<StructureIndexCache.ItemStackSnapshot> items) {
        if (!blockEntity.contains(key)) {
            return;
        }
        blockEntity.getCompound(key).ifPresent(itemTag -> addStoredItemSnapshot(itemTag, items));
    }

    private static void addStoredItemSnapshot(CompoundTag itemTag, List<StructureIndexCache.ItemStackSnapshot> items) {
        StructureIndexCache.ItemStackSnapshot snapshot = readItemSnapshot(itemTag);
        if (!ItemStackSnapshotHelper.isEmptySnapshot(snapshot)) {
            items.add(snapshot);
        }
    }
}
