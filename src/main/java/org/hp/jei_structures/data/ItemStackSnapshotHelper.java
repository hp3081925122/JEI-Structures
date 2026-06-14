package org.hp.jei_structures.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public final class ItemStackSnapshotHelper {

    private ItemStackSnapshotHelper() {
    }

    public static StructureIndexCache.ItemStackSnapshot createSnapshot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        StructureIndexCache.ItemStackSnapshot snapshot = new StructureIndexCache.ItemStackSnapshot();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        snapshot.itemId = itemId != null ? itemId.toString() : "";
        try {
            CompoundTag tag = stack.save(new CompoundTag());
            if (tag != null && !tag.isEmpty()) {
                snapshot.stackTag = tag.toString();
            }
        } catch (Exception ignored) {
        }
        return isEmptySnapshot(snapshot) ? null : snapshot;
    }

    public static ItemStack parseSnapshot(StructureIndexCache.ItemStackSnapshot snapshot) {
        if (snapshot == null) {
            return ItemStack.EMPTY;
        }
        if (snapshot.stackTag != null && !snapshot.stackTag.isBlank()) {
            try {
                return ItemStack.of(net.minecraft.nbt.TagParser.parseTag(snapshot.stackTag));
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
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }
}
