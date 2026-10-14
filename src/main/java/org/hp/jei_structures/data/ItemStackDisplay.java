package org.hp.jei_structures.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.ForgeRegistries;
import org.hp.jei_structures.JeiStructures;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ItemStackDisplay {

    private ItemStackDisplay() {
    }

    public static ItemStack create(String itemId, String tagSnbt, List<String> functions) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        try {
            if (tagSnbt != null && !tagSnbt.isBlank()) {
                stack.setTag(TagParser.parseTag(tagSnbt));
            }
            if (functions != null) {
                for (String raw : functions) {
                    JsonObject function = JsonParser.parseString(raw).getAsJsonObject();
                    if (function.has("conditions") && !function.getAsJsonArray("conditions").isEmpty()) {
                        continue;
                    }
                    String type = function.get("function").getAsString();
                    switch (type) {
                        case "minecraft:set_nbt" -> stack.getOrCreateTag().merge(TagParser.parseTag(function.get("tag").getAsString()));
                        case "minecraft:set_potion" -> stack.getOrCreateTag().putString("Potion", function.get("id").getAsString());
                        case "minecraft:enchant_randomly", "minecraft:enchant_with_levels" -> {
                            if (stack.is(Items.BOOK)) {
                                stack = new ItemStack(Items.ENCHANTED_BOOK);
                            }
                        }
                        case "minecraft:set_enchantments" -> stack = setEnchantments(stack, function);
                        default -> {
                        }
                    }
                }
            }
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("Failed to restore cached item display data: item={}", itemId, exception);
        }
        return stack;
    }

    private static ItemStack setEnchantments(ItemStack stack, JsonObject function) {
        JsonObject levels = function.getAsJsonObject("enchantments");
        boolean book = stack.is(Items.BOOK);
        if (book) {
            stack = new ItemStack(Items.ENCHANTED_BOOK);
        }
        Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(stack);
        boolean add = function.has("add") && function.get("add").getAsBoolean();
        for (Map.Entry<String, JsonElement> value : levels.entrySet()) {
            Integer level = fixedLevel(value.getValue());
            ResourceLocation id = ResourceLocation.tryParse(value.getKey());
            Enchantment enchantment = id == null ? null : ForgeRegistries.ENCHANTMENTS.getValue(id);
            if (level == null || enchantment == null) {
                continue;
            }
            if (book) {
                EnchantedBookItem.addEnchantment(stack, new EnchantmentInstance(enchantment, level));
            } else {
                int result = add ? existing.getOrDefault(enchantment, 0) + level : level;
                if (result <= 0) {
                    existing.remove(enchantment);
                } else {
                    existing.put(enchantment, result);
                }
            }
        }
        if (!book) {
            EnchantmentHelper.setEnchantments(existing, stack);
        }
        return stack;
    }

    private static Integer fixedLevel(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }
        if (element.isJsonObject()) {
            JsonObject provider = element.getAsJsonObject();
            if (provider.has("type") && "minecraft:constant".equals(provider.get("type").getAsString()) && provider.has("value")) {
                return fixedLevel(provider.get("value"));
            }
        }
        return null;
    }

    public static List<Component> functionNotes(List<String> functions) {
        List<Component> result = new ArrayList<>();
        if (functions != null) {
            for (String raw : functions) {
                try {
                    JsonObject function = JsonParser.parseString(raw).getAsJsonObject();
                    String type = function.get("function").getAsString();
                    if (type.equals("minecraft:enchant_randomly") || type.equals("minecraft:enchant_with_levels")) {
                        result.add(Component.translatable("jei_structures.tooltip.random_enchantment"));
                    }
                    if (function.has("conditions") && !function.getAsJsonArray("conditions").isEmpty()) {
                        result.add(Component.translatable("jei_structures.tooltip.conditional_item_data"));
                    }
                    if (type.equals("minecraft:set_enchantments")) {
                        for (Map.Entry<String, JsonElement> level : function.getAsJsonObject("enchantments").entrySet()) {
                            if (fixedLevel(level.getValue()) == null) {
                                result.add(Component.translatable("jei_structures.tooltip.random_enchantment"));
                                break;
                            }
                        }
                    }
                } catch (Exception exception) {
                    JeiStructures.LOGGER.warn("Failed to describe cached loot item function: {}", raw, exception);
                }
            }
        }
        return result;
    }

    public static boolean requiresVariantData(String itemId) {
        return "minecraft:enchanted_book".equals(itemId) || "minecraft:potion".equals(itemId)
                || "minecraft:splash_potion".equals(itemId) || "minecraft:lingering_potion".equals(itemId)
                || "minecraft:tipped_arrow".equals(itemId);
    }
}
