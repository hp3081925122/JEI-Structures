package org.hp.jei_structures.data;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ItemStackSnapshotHelper {

    private ItemStackSnapshotHelper() {
    }

    public static StructureIndexCache.ItemStackSnapshot createSnapshot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        StructureIndexCache.ItemStackSnapshot snapshot = new StructureIndexCache.ItemStackSnapshot();
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        snapshot.itemId = itemId != null ? itemId.toString() : "";
        return isEmptySnapshot(snapshot) ? null : snapshot;
    }

    public static ItemStack parseSnapshot(StructureIndexCache.ItemStackSnapshot snapshot) {
        if (snapshot == null) {
            return ItemStack.EMPTY;
        }
        return createFallbackStack(snapshot.itemId);
    }

    public static String snapshotItemId(StructureIndexCache.ItemStackSnapshot snapshot) {
        return snapshot != null && snapshot.itemId != null ? snapshot.itemId : "";
    }

    public static boolean isEmptySnapshot(StructureIndexCache.ItemStackSnapshot snapshot) {
        return snapshot == null
                || ((snapshot.itemId == null || snapshot.itemId.isBlank())
                && (snapshot.stackTag == null || snapshot.stackTag.isBlank()));
    }

    public static ItemStack createFallbackStack(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.get(id).map(reference -> reference.value()).orElse(null);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }
}
