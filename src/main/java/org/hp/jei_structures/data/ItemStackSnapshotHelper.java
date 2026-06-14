package org.hp.jei_structures.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ItemStackSnapshotHelper {

    private ItemStackSnapshotHelper() {
    }

    public static StructureIndexCache.ItemStackSnapshot createSnapshot(ItemStack stack, HolderLookup.Provider provider) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        StructureIndexCache.ItemStackSnapshot snapshot = new StructureIndexCache.ItemStackSnapshot();
        ResourceLocation itemId = ResourceLocation.tryParse(String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())));
        snapshot.itemId = itemId != null ? itemId.toString() : "";
        if (provider != null) {
            try {
                Tag saved = stack.saveOptional(provider);
                if (saved instanceof CompoundTag compoundTag && !compoundTag.isEmpty()) {
                    snapshot.stackTag = compoundTag.toString();
                }
            } catch (Exception ignored) {
            }
        }
        if ((snapshot.itemId == null || snapshot.itemId.isBlank()) && !snapshot.stackTag.isBlank()) {
            ItemStack restored = parseSnapshot(snapshot, provider);
            if (!restored.isEmpty()) {
                ResourceLocation restoredId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(restored.getItem());
                snapshot.itemId = restoredId != null ? restoredId.toString() : "";
            }
        }
        return isEmptySnapshot(snapshot) ? null : snapshot;
    }

    public static ItemStack parseSnapshot(StructureIndexCache.ItemStackSnapshot snapshot, HolderLookup.Provider provider) {
        if (snapshot == null) {
            return ItemStack.EMPTY;
        }
        if (provider != null && snapshot.stackTag != null && !snapshot.stackTag.isBlank()) {
            try {
                CompoundTag compoundTag = TagParser.parseTag(snapshot.stackTag);
                return ItemStack.parseOptional(provider, compoundTag);
            } catch (Exception ignored) {
            }
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
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }
}
