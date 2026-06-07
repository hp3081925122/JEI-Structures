package org.hp.jei_structures.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.hp.jei_structures.JeiStructures;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LootTableItemResolver {

    private final ResourceManager resourceManager;
    private final Registry<Item> itemRegistry;
    private final Map<String, Set<String>> itemCache = new HashMap<>();
    private final Map<String, StructureIndexCache.LootTableDetail> detailCache = new HashMap<>();
    private final Set<String> resolvingItems = new HashSet<>();
    private final Set<String> resolvingDetails = new HashSet<>();

    public LootTableItemResolver(ResourceManager resourceManager, Registry<Item> itemRegistry) {
        this.resourceManager = resourceManager;
        this.itemRegistry = itemRegistry;
    }

    public Set<String> resolveLootItems(ResourceLocation lootTableId) {
        if (lootTableId == null) {
            return Set.of();
        }
        String key = lootTableId.toString();
        Set<String> cached = itemCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (!resolvingItems.add(key)) {
            JeiStructures.LOGGER.debug("检测到战利品表递归引用，跳过本轮物品展开：{}", lootTableId);
            return Set.of();
        }
        try {
            StructureIndexCache.LootTableDetail detail = resolveLootTableDetail(lootTableId);
            LinkedHashSet<String> itemIds = new LinkedHashSet<>();
            if (detail != null) {
                for (StructureIndexCache.LootItemEntry entry : detail.entries) {
                    if (entry != null && entry.itemId != null && !entry.itemId.isBlank()) {
                        itemIds.add(entry.itemId);
                    }
                }
            }
            Set<String> result = Set.copyOf(itemIds);
            itemCache.put(key, result);
            return result;
        } finally {
            resolvingItems.remove(key);
        }
    }

    public StructureIndexCache.LootTableDetail resolveLootTableDetail(ResourceLocation lootTableId) {
        if (lootTableId == null) {
            return null;
        }
        String key = lootTableId.toString();
        StructureIndexCache.LootTableDetail cached = detailCache.get(key);
        if (cached != null) {
            return copyDetail(cached);
        }
        if (!resolvingDetails.add(key)) {
            JeiStructures.LOGGER.debug("检测到战利品表递归引用，跳过本轮明细展开：{}", lootTableId);
            StructureIndexCache.LootTableDetail recursive = new StructureIndexCache.LootTableDetail();
            recursive.lootTableId = key;
            return recursive;
        }
        try {
            StructureIndexCache.LootTableDetail detail = buildLootTableDetail(lootTableId);
            detailCache.put(key, copyDetail(detail));
            return copyDetail(detail);
        } finally {
            resolvingDetails.remove(key);
        }
    }

    private StructureIndexCache.LootTableDetail buildLootTableDetail(ResourceLocation lootTableId) {
        StructureIndexCache.LootTableDetail detail = new StructureIndexCache.LootTableDetail();
        detail.lootTableId = lootTableId.toString();
        JsonObject json = readJson(toLootTableLocation(lootTableId));
        if (json == null) {
            JeiStructures.LOGGER.debug("未找到战利品表资源：{}", lootTableId);
            return detail;
        }
        JsonArray pools = getArray(json, "pools");
        if (pools == null) {
            return detail;
        }
        for (JsonElement poolElement : pools) {
            if (poolElement == null || !poolElement.isJsonObject()) {
                continue;
            }
            JsonObject pool = poolElement.getAsJsonObject();
            JsonArray entries = getArray(pool, "entries");
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            int totalWeight = estimateTotalWeight(entries);
            String rollsText = describeNumberProvider(pool.get("rolls"), "1");
            String bonusRollsText = describeNumberProvider(pool.get("bonus_rolls"), "0");
            String poolConditionsText = describeConditions(pool.get("conditions"));
            for (JsonElement entryElement : entries) {
                appendEntries(detail.entries, lootTableId, entryElement, totalWeight, rollsText, bonusRollsText, poolConditionsText, "");
            }
        }
        detail.entries.sort(Comparator
                .comparing((StructureIndexCache.LootItemEntry entry) -> StructureText(entry.itemId), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.itemId, String.CASE_INSENSITIVE_ORDER));
        return detail;
    }

    private void appendEntries(List<StructureIndexCache.LootItemEntry> output, ResourceLocation rootLootTableId, JsonElement element, int totalWeight, String rollsText, String bonusRollsText, String inheritedConditions, String inheritedCountText) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        String type = getString(object, "type");
        String mergedConditions = mergeSummary(inheritedConditions, describeConditions(object.get("conditions")));
        String mergedCountText = mergeSummary(inheritedCountText, describeFunctions(object.get("functions")));
        int weight = Math.max(getInt(object, "weight", 1), 1);
        int quality = getInt(object, "quality", 0);
        String chanceText = mergeSummary(buildRelativeWeightText(weight, totalWeight), mergedConditions);

        if ("minecraft:item".equals(type)) {
            ResourceLocation itemId = getResourceLocation(object, "name");
            if (itemId != null && ForgeRegistries.ITEMS.containsKey(itemId)) {
                output.add(createLootItemEntry(itemId.toString(), weight, quality, rollsText, bonusRollsText, chanceText, finalizeCountText(mergedCountText)));
            }
            return;
        }

        if ("minecraft:tag".equals(type)) {
            ResourceLocation tagId = getResourceLocation(object, "name");
            if (tagId == null) {
                return;
            }
            List<String> tagItems = expandTag(tagId);
            String tagChanceText = mergeSummary(chanceText, "标签展开：" + tagId);
            for (String itemId : tagItems) {
                output.add(createLootItemEntry(itemId, weight, quality, rollsText, bonusRollsText, tagChanceText, finalizeCountText(mergedCountText)));
            }
            return;
        }

        if ("minecraft:loot_table".equals(type)) {
            ResourceLocation childLootTable = getResourceLocation(object, "name");
            if (childLootTable == null) {
                return;
            }
            String childChanceText = mergeSummary(chanceText, "引用子表：" + childLootTable);
            for (String itemId : resolveLootItems(childLootTable)) {
                output.add(createLootItemEntry(itemId, weight, quality, rollsText, bonusRollsText, childChanceText, finalizeCountText(mergedCountText)));
            }
            return;
        }

        if ("minecraft:alternatives".equals(type) || "minecraft:sequence".equals(type) || "minecraft:group".equals(type)) {
            JsonArray children = getArray(object, "children");
            if (children == null) {
                return;
            }
            for (JsonElement child : children) {
                appendEntries(output, rootLootTableId, child, totalWeight, rollsText, bonusRollsText, mergedConditions, mergedCountText);
            }
            return;
        }

        JsonArray children = getArray(object, "children");
        if (children != null) {
            for (JsonElement child : children) {
                appendEntries(output, rootLootTableId, child, totalWeight, rollsText, bonusRollsText, mergedConditions, mergedCountText);
            }
        }
        JsonArray nestedEntries = getArray(object, "entries");
        if (nestedEntries != null) {
            for (JsonElement child : nestedEntries) {
                appendEntries(output, rootLootTableId, child, totalWeight, rollsText, bonusRollsText, mergedConditions, mergedCountText);
            }
        }
    }

    private int estimateTotalWeight(JsonArray entries) {
        int totalWeight = 0;
        for (JsonElement element : entries) {
            totalWeight += estimateWeight(element);
        }
        return Math.max(totalWeight, 0);
    }

    private int estimateWeight(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return 0;
        }
        JsonObject object = element.getAsJsonObject();
        String type = getString(object, "type");
        if ("minecraft:alternatives".equals(type) || "minecraft:sequence".equals(type) || "minecraft:group".equals(type)) {
            JsonArray children = getArray(object, "children");
            if (children == null) {
                return 0;
            }
            int total = 0;
            for (JsonElement child : children) {
                total += estimateWeight(child);
            }
            return total;
        }
        if ("minecraft:empty".equals(type)) {
            return 0;
        }
        return Math.max(getInt(object, "weight", 1), 1);
    }

    private StructureIndexCache.LootItemEntry createLootItemEntry(String itemId, int weight, int quality, String rollsText, String bonusRollsText, String chanceText, String countText) {
        StructureIndexCache.LootItemEntry entry = new StructureIndexCache.LootItemEntry();
        entry.itemId = itemId;
        entry.weight = weight;
        entry.quality = quality;
        entry.rollsText = fallbackText(rollsText, "1");
        entry.bonusRollsText = fallbackText(bonusRollsText, "0");
        entry.chanceText = fallbackText(chanceText, "相对权重未知");
        entry.countText = fallbackText(countText, "默认数量");
        return entry;
    }

    private String buildRelativeWeightText(int weight, int totalWeight) {
        if (weight <= 0 || totalWeight <= 0) {
            return "相对权重未知";
        }
        double percent = (double) weight * 100.0D / (double) totalWeight;
        return "相对权重 " + weight + "/" + totalWeight + "（约" + formatDecimal(percent) + "%）";
    }

    private String describeNumberProvider(JsonElement element, String fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsJsonPrimitive().getAsString();
        }
        if (!element.isJsonObject()) {
            return fallback;
        }
        JsonObject object = element.getAsJsonObject();
        String type = getString(object, "type");
        if (object.has("value")) {
            return describeNumberProvider(object.get("value"), fallback);
        }
        if ("minecraft:uniform".equals(type)) {
            return buildRangeText(object.get("min"), object.get("max"));
        }
        if ("minecraft:binomial".equals(type)) {
            return "n=" + describeNumberProvider(object.get("n"), "?") + "，p=" + describeNumberProvider(object.get("p"), "?");
        }
        if ("minecraft:score".equals(type)) {
            return "记分板";
        }
        if (object.has("min") || object.has("max")) {
            return buildRangeText(object.get("min"), object.get("max"));
        }
        return type.isBlank() ? fallback : simplifyKey(type);
    }

    private String buildRangeText(JsonElement minElement, JsonElement maxElement) {
        String minText = describeNumberProvider(minElement, "?");
        String maxText = describeNumberProvider(maxElement, "?");
        if (minText.equals(maxText)) {
            return minText;
        }
        return minText + "~" + maxText;
    }

    private String describeConditions(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return "";
        }
        List<String> descriptions = new ArrayList<>();
        for (JsonElement conditionElement : element.getAsJsonArray()) {
            if (conditionElement == null || !conditionElement.isJsonObject()) {
                continue;
            }
            JsonObject condition = conditionElement.getAsJsonObject();
            String type = getString(condition, "condition");
            if (type.isBlank()) {
                continue;
            }
            if ("minecraft:random_chance".equals(type)) {
                descriptions.add("随机概率 " + describeNumberProvider(condition.get("chance"), "?") + "%");
                continue;
            }
            if ("minecraft:random_chance_with_looting".equals(type)) {
                descriptions.add("随机概率 " + describeNumberProvider(condition.get("chance"), "?") + "，抢夺加成 " + describeNumberProvider(condition.get("looting_multiplier"), "?"));
                continue;
            }
            descriptions.add(simplifyKey(type));
        }
        if (descriptions.isEmpty()) {
            return "";
        }
        return "条件：" + String.join("、", descriptions);
    }

    private String describeFunctions(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return "";
        }
        List<String> descriptions = new ArrayList<>();
        for (JsonElement functionElement : element.getAsJsonArray()) {
            if (functionElement == null || !functionElement.isJsonObject()) {
                continue;
            }
            JsonObject function = functionElement.getAsJsonObject();
            String type = getString(function, "function");
            if (type.isBlank()) {
                continue;
            }
            if ("minecraft:set_count".equals(type)) {
                descriptions.add("数量 " + describeNumberProvider(function.get("count"), "?"));
                continue;
            }
            if ("minecraft:limit_count".equals(type)) {
                descriptions.add("限制数量 " + buildRangeText(function.get("min"), function.get("max")));
                continue;
            }
            descriptions.add("函数：" + simplifyKey(type));
        }
        return String.join("；", descriptions);
    }

    private String finalizeCountText(String countText) {
        return countText.isBlank() ? "默认数量" : countText;
    }

    private List<String> expandTag(ResourceLocation tagId) {
        LinkedHashSet<String> itemIds = new LinkedHashSet<>();
        for (Holder<Item> holder : itemRegistry.getTagOrEmpty(TagKey.create(Registries.ITEM, tagId))) {
            ResourceLocation itemId = itemRegistry.getKey(holder.value());
            if (itemId != null) {
                itemIds.add(itemId.toString());
            }
        }
        return itemIds.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private JsonObject readJson(ResourceLocation location) {
        try {
            Optional<Resource> resource = resourceManager.getResource(location);
            if (resource.isEmpty()) {
                return null;
            }
            try (Reader reader = resource.get().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                return json != null && json.isJsonObject() ? json.getAsJsonObject() : null;
            }
        } catch (Exception exception) {
            JeiStructures.LOGGER.warn("读取战利品表失败：{}", location, exception);
            return null;
        }
    }

    private ResourceLocation toLootTableLocation(ResourceLocation lootTableId) {
        return ResourceLocation.fromNamespaceAndPath(lootTableId.getNamespace(), "loot_tables/" + lootTableId.getPath() + ".json");
    }

    private static StructureIndexCache.LootTableDetail copyDetail(StructureIndexCache.LootTableDetail source) {
        StructureIndexCache.LootTableDetail copy = new StructureIndexCache.LootTableDetail();
        if (source == null) {
            return copy;
        }
        copy.lootTableId = source.lootTableId != null ? source.lootTableId : "";
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
            copy.entries.add(entry);
        }
        return copy;
    }

    private static JsonArray getArray(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return null;
        }
        return object.getAsJsonArray(key);
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static ResourceLocation getResourceLocation(JsonObject object, String key) {
        String value = getString(object, key);
        return value.isBlank() ? null : ResourceLocation.tryParse(value);
    }

    private static String fallbackText(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }

    private static String mergeSummary(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.isBlank()) {
            return right;
        }
        if (right.isBlank()) {
            return left;
        }
        return left + "；" + right;
    }

    private static String simplifyKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        ResourceLocation id = ResourceLocation.tryParse(key);
        String raw = id != null ? id.getPath() : key;
        return raw.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private static String formatDecimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.00001D) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String StructureText(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "";
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return itemId;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            return itemId;
        }
        return item.getDescription().getString();
    }
}
