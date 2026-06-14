package org.hp.jei_structures.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class StoredItemNbtReader {

    private StoredItemNbtReader() {
    }

    public static LinkedHashSet<String> readStoredItems(CompoundTag blockEntity) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (StructureIndexCache.ItemStackSnapshot snapshot : readStoredItemSnapshots(blockEntity, null)) {
            String itemId = ItemStackSnapshotHelper.snapshotItemId(snapshot);
            if (itemId != null && !itemId.isBlank() && !"minecraft:air".equals(itemId)) {
                items.add(itemId);
            }
        }
        return items;
    }

    public static List<StructureIndexCache.ItemStackSnapshot> readStoredItemSnapshots(CompoundTag blockEntity, HolderLookup.Provider provider) {
        List<StructureIndexCache.ItemStackSnapshot> items = new ArrayList<>();
        if (blockEntity == null) {
            return items;
        }
        collectStoredItemSnapshotsFromList(blockEntity, "Items", items, provider);
        collectStoredItemSnapshotsFromList(blockEntity, "items", items, provider);
        collectStoredItemSnapshotFromCompound(blockEntity, "Item", items, provider);
        collectStoredItemSnapshotFromCompound(blockEntity, "item", items, provider);
        return items;
    }

    public static StructureIndexCache.ItemStackSnapshot readItemSnapshot(CompoundTag itemTag, HolderLookup.Provider provider) {
        if (itemTag == null) {
            return null;
        }
        StructureIndexCache.ItemStackSnapshot snapshot = null;
        if (provider != null) {
            try {
                ItemStack stack = ItemStack.parseOptional(provider, itemTag.copy());
                snapshot = ItemStackSnapshotHelper.createSnapshot(stack, provider);
            } catch (Exception ignored) {
            }
        }
        if (snapshot == null) {
            snapshot = new StructureIndexCache.ItemStackSnapshot();
            if (itemTag.contains("id", Tag.TAG_STRING)) {
                snapshot.itemId = itemTag.getString("id");
            }
            snapshot.stackTag = itemTag.toString();
        }
        return ItemStackSnapshotHelper.isEmptySnapshot(snapshot) ? null : snapshot;
    }

    private static void collectStoredItemSnapshotsFromList(CompoundTag blockEntity, String key, List<StructureIndexCache.ItemStackSnapshot> items, HolderLookup.Provider provider) {
        if (!blockEntity.contains(key, Tag.TAG_LIST)) {
            return;
        }
        ListTag listTag = blockEntity.getList(key, Tag.TAG_COMPOUND);
        for (int index = 0; index < listTag.size(); index++) {
            if (listTag.get(index) instanceof CompoundTag itemTag) {
                addStoredItemSnapshot(itemTag, items, provider);
            }
        }
    }

    private static void collectStoredItemSnapshotFromCompound(CompoundTag blockEntity, String key, List<StructureIndexCache.ItemStackSnapshot> items, HolderLookup.Provider provider) {
        if (!blockEntity.contains(key, Tag.TAG_COMPOUND)) {
            return;
        }
        addStoredItemSnapshot(blockEntity.getCompound(key), items, provider);
    }

    private static void addStoredItemSnapshot(CompoundTag itemTag, List<StructureIndexCache.ItemStackSnapshot> items, HolderLookup.Provider provider) {
        StructureIndexCache.ItemStackSnapshot snapshot = readItemSnapshot(itemTag, provider);
        if (!ItemStackSnapshotHelper.isEmptySnapshot(snapshot)) {
            items.add(snapshot);
        }
    }
}
