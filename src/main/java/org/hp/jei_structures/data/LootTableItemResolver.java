package org.hp.jei_structures.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
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
    private final HolderLookup.Provider lookupProvider;
    private final ServerLevel lootContextLevel;
    private final RegistryOps<JsonElement> registryOps;
    private final Map<String, Set<String>> itemCache = new HashMap<>();
    private final Map<String, StructureIndexCache.LootTableDetail> detailCache = new HashMap<>();
    private final Set<String> resolvingItems = new HashSet<>();
    private final Set<String> resolvingDetails = new HashSet<>();

    public LootTableItemResolver(ResourceManager resourceManager, Registry<Item> itemRegistry, HolderLookup.Provider lookupProvider, ServerLevel lootContextLevel) {
        this.resourceManager = resourceManager;
        this.itemRegistry = itemRegistry;
        this.lookupProvider = lookupProvider;
        this.lootContextLevel = lootContextLevel;
        this.registryOps = RegistryOps.create(JsonOps.INSTANCE, lookupProvider);
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
            JeiStructures.LOGGER.debug("Recursive loot table reference detected while resolving items: {}", lootTableId);
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
            JeiStructures.LOGGER.debug("Recursive loot table reference detected while resolving details: {}", lootTableId);
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
            json = readJson(toLegacyLootTableLocation(lootTableId));
        }
        if (json == null) {
            JeiStructures.LOGGER.debug("Loot table resource was not found: {}", lootTableId);
            return detail;
        }
        JsonArray pools = getArray(json, "pools");
        if (pools == null) {
            return detail;
        }
        List<JsonElement> tableFunctions = getElements(json, "functions");
        for (int poolIndex = 0; poolIndex < pools.size(); poolIndex++) {
            JsonElement poolElement = pools.get(poolIndex);
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
            List<StructureIndexCache.LootTextEntry> poolConditions = describeConditions(pool.get("conditions"));
            List<JsonElement> poolFunctions = mergeFunctionElements(tableFunctions, pool.get("functions"));
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                JsonElement entryElement = entries.get(entryIndex);
                appendEntries(
                        detail.entries,
                        lootTableId,
                        entryElement,
                        totalWeight,
                        rollsText,
                        bonusRollsText,
                        poolConditions,
                        List.of(),
                        poolFunctions,
                        "pool[" + poolIndex + "].entry[" + entryIndex + "]"
                );
            }
        }
        detail.entries.sort(Comparator
                .comparing((StructureIndexCache.LootItemEntry entry) -> StructureText(entry.itemId), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.itemId, String.CASE_INSENSITIVE_ORDER));
        return detail;
    }

    private void appendEntries(
            List<StructureIndexCache.LootItemEntry> output,
            ResourceLocation rootLootTableId,
            JsonElement element,
            int totalWeight,
            String rollsText,
            String bonusRollsText,
            List<StructureIndexCache.LootTextEntry> inheritedChanceNotes,
            List<StructureIndexCache.LootTextEntry> inheritedCountNotes,
            List<JsonElement> inheritedFunctions,
            String entryPath
    ) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        String type = getString(object, "type");
        List<StructureIndexCache.LootTextEntry> mergedChanceNotes = mergeNotes(inheritedChanceNotes, describeConditions(object.get("conditions")));
        List<StructureIndexCache.LootTextEntry> mergedCountNotes = mergeNotes(inheritedCountNotes, describeFunctions(object.get("functions")));
        List<JsonElement> mergedFunctions = mergeFunctionElements(inheritedFunctions, object.get("functions"));
        int weight = Math.max(getInt(object, "weight", 1), 1);
        int quality = getInt(object, "quality", 0);
        List<StructureIndexCache.LootTextEntry> chanceNotes = mergeNotes(List.of(buildRelativeWeightNote(weight, totalWeight)), mergedChanceNotes);

        if ("minecraft:item".equals(type)) {
            ResourceLocation itemId = getResourceLocation(object, "name");
            if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                net.minecraft.world.item.ItemStack stack = applyFunctions(createBaseStack(itemId), rootLootTableId, mergedFunctions, entryPath + "/item:" + itemId);
                output.add(createLootItemEntry(stack, weight, quality, rollsText, bonusRollsText, chanceNotes, finalizeCountNotes(mergedCountNotes)));
            }
            return;
        }

        if ("minecraft:tag".equals(type)) {
            ResourceLocation tagId = getResourceLocation(object, "name");
            if (tagId == null) {
                return;
            }
            List<String> tagItems = expandTag(tagId);
            List<StructureIndexCache.LootTextEntry> tagChanceNotes = mergeNotes(chanceNotes, List.of(note("jei_structures.loot_note.tag_expansion", tagId.toString())));
            for (String itemId : tagItems) {
                net.minecraft.world.item.ItemStack stack = applyFunctions(ItemStackSnapshotHelper.createFallbackStack(itemId), rootLootTableId, mergedFunctions, entryPath + "/tag:" + itemId);
                output.add(createLootItemEntry(stack, weight, quality, rollsText, bonusRollsText, tagChanceNotes, finalizeCountNotes(mergedCountNotes)));
            }
            return;
        }

        if ("minecraft:loot_table".equals(type)) {
            ResourceLocation childLootTable = getResourceLocation(object, "name");
            if (childLootTable == null) {
                return;
            }
            List<StructureIndexCache.LootTextEntry> childChanceNotes = mergeNotes(chanceNotes, List.of(note("jei_structures.loot_note.nested_loot_table", childLootTable.toString())));
            StructureIndexCache.LootTableDetail childDetail = resolveLootTableDetail(childLootTable);
            if (childDetail != null && childDetail.entries != null && !childDetail.entries.isEmpty()) {
                for (StructureIndexCache.LootItemEntry childEntry : childDetail.entries) {
                    if (childEntry == null || childEntry.itemId == null || childEntry.itemId.isBlank()) {
                        continue;
                    }
                    net.minecraft.world.item.ItemStack stack = applyFunctions(
                            restoreStack(childEntry),
                            rootLootTableId,
                            mergedFunctions,
                            entryPath + "/loot_table:" + childLootTable + "/child:" + childEntry.itemId
                    );
                    output.add(createLootItemEntry(
                            stack,
                            weight,
                            quality,
                            rollsText,
                            bonusRollsText,
                            childChanceNotes,
                            finalizeCountNotes(mergedCountNotes)
                    ));
                }
            } else {
                for (String itemId : resolveLootItems(childLootTable)) {
                    output.add(createLootItemEntry(ItemStackSnapshotHelper.createFallbackStack(itemId), weight, quality, rollsText, bonusRollsText, childChanceNotes, finalizeCountNotes(mergedCountNotes)));
                }
            }
            return;
        }

        if ("minecraft:alternatives".equals(type) || "minecraft:sequence".equals(type) || "minecraft:group".equals(type)) {
            JsonArray children = getArray(object, "children");
            if (children == null) {
                return;
            }
            for (int childIndex = 0; childIndex < children.size(); childIndex++) {
                JsonElement child = children.get(childIndex);
                appendEntries(output, rootLootTableId, child, totalWeight, rollsText, bonusRollsText, mergedChanceNotes, mergedCountNotes, mergedFunctions, entryPath + "/child[" + childIndex + "]");
            }
            return;
        }

        JsonArray children = getArray(object, "children");
        if (children != null) {
            for (int childIndex = 0; childIndex < children.size(); childIndex++) {
                JsonElement child = children.get(childIndex);
                appendEntries(output, rootLootTableId, child, totalWeight, rollsText, bonusRollsText, mergedChanceNotes, mergedCountNotes, mergedFunctions, entryPath + "/child[" + childIndex + "]");
            }
        }
        JsonArray nestedEntries = getArray(object, "entries");
        if (nestedEntries != null) {
            for (int childIndex = 0; childIndex < nestedEntries.size(); childIndex++) {
                JsonElement child = nestedEntries.get(childIndex);
                appendEntries(output, rootLootTableId, child, totalWeight, rollsText, bonusRollsText, mergedChanceNotes, mergedCountNotes, mergedFunctions, entryPath + "/entry[" + childIndex + "]");
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

    private StructureIndexCache.LootItemEntry createLootItemEntry(net.minecraft.world.item.ItemStack stack, int weight, int quality, String rollsText, String bonusRollsText, List<StructureIndexCache.LootTextEntry> chanceNotes, List<StructureIndexCache.LootTextEntry> countNotes) {
        StructureIndexCache.LootItemEntry entry = new StructureIndexCache.LootItemEntry();
        StructureIndexCache.ItemStackSnapshot snapshot = ItemStackSnapshotHelper.createSnapshot(stack, lookupProvider);
        entry.itemId = snapshot != null ? ItemStackSnapshotHelper.snapshotItemId(snapshot) : "";
        entry.itemStackTag = snapshot != null && snapshot.stackTag != null ? snapshot.stackTag : "";
        entry.weight = weight;
        entry.quality = quality;
        entry.rollsText = fallbackText(rollsText, "1");
        entry.bonusRollsText = fallbackText(bonusRollsText, "0");
        entry.chanceNotes = copyNotes(chanceNotes);
        entry.countNotes = copyNotes(countNotes);
        entry.chanceText = notesToEnglish(entry.chanceNotes, "Unknown relative weight");
        entry.countText = notesToEnglish(entry.countNotes, "Default count");
        return entry;
    }

    private net.minecraft.world.item.ItemStack createBaseStack(ResourceLocation itemId) {
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        return new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(itemId));
    }

    private net.minecraft.world.item.ItemStack restoreStack(StructureIndexCache.LootItemEntry entry) {
        if (entry == null) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        StructureIndexCache.ItemStackSnapshot snapshot = new StructureIndexCache.ItemStackSnapshot();
        snapshot.itemId = entry.itemId != null ? entry.itemId : "";
        snapshot.stackTag = entry.itemStackTag != null ? entry.itemStackTag : "";
        return ItemStackSnapshotHelper.parseSnapshot(snapshot, lookupProvider);
    }

    private net.minecraft.world.item.ItemStack applyFunctions(net.minecraft.world.item.ItemStack originalStack, ResourceLocation lootTableId, List<JsonElement> functionElements, String entryPath) {
        if (originalStack == null || originalStack.isEmpty() || functionElements == null || functionElements.isEmpty() || lootContextLevel == null) {
            return originalStack;
        }
        net.minecraft.world.item.ItemStack stack = originalStack.copy();
        LootContext context = createFunctionContext(lootTableId, entryPath);
        for (LootItemFunction function : decodeFunctions(functionElements)) {
            try {
                net.minecraft.world.item.ItemStack modified = function.apply(stack, context);
                if (modified != null) {
                    stack = modified;
                }
            } catch (Exception exception) {
                JeiStructures.LOGGER.debug("Failed to apply loot function while resolving {} at {}", lootTableId, entryPath, exception);
            }
        }
        return stack;
    }

    private LootContext createFunctionContext(ResourceLocation lootTableId, String entryPath) {
        LootParams params = new LootParams.Builder(lootContextLevel)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        long seed = computeSeed(lootTableId, entryPath);
        return new LootContext.Builder(params)
                .withOptionalRandomSeed(seed)
                .withQueriedLootTableId(lootTableId)
                .create(Optional.empty());
    }

    private List<LootItemFunction> decodeFunctions(List<JsonElement> functionElements) {
        if (functionElements == null || functionElements.isEmpty()) {
            return List.of();
        }
        List<LootItemFunction> functions = new ArrayList<>(functionElements.size());
        for (JsonElement functionElement : functionElements) {
            if (functionElement == null || functionElement.isJsonNull()) {
                continue;
            }
            try {
                LootItemFunctions.ROOT_CODEC.parse(registryOps, functionElement).result().ifPresent(functions::add);
            } catch (Exception exception) {
                JeiStructures.LOGGER.debug("Failed to decode loot function: {}", functionElement, exception);
            }
        }
        return List.copyOf(functions);
    }

    private static List<JsonElement> mergeFunctionElements(List<JsonElement> inheritedFunctions, JsonElement localFunctionsElement) {
        List<JsonElement> localFunctions = toFunctionElements(localFunctionsElement);
        if ((inheritedFunctions == null || inheritedFunctions.isEmpty()) && localFunctions.isEmpty()) {
            return List.of();
        }
        List<JsonElement> merged = new ArrayList<>();
        if (inheritedFunctions != null && !inheritedFunctions.isEmpty()) {
            merged.addAll(inheritedFunctions);
        }
        merged.addAll(localFunctions);
        return List.copyOf(merged);
    }

    private static List<JsonElement> getElements(JsonObject object, String key) {
        if (object == null) {
            return List.of();
        }
        return toFunctionElements(object.get(key));
    }

    private static List<JsonElement> toFunctionElements(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return List.of();
        }
        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) {
            return List.of();
        }
        List<JsonElement> elements = new ArrayList<>(array.size());
        for (JsonElement value : array) {
            if (value != null && !value.isJsonNull()) {
                elements.add(value);
            }
        }
        return elements.isEmpty() ? List.of() : List.copyOf(elements);
    }

    private static long computeSeed(ResourceLocation lootTableId, String entryPath) {
        long value = 0x9E3779B97F4A7C15L;
        if (lootTableId != null) {
            value ^= lootTableId.toString().hashCode();
        }
        if (entryPath != null && !entryPath.isBlank()) {
            value = value * 31L + entryPath.hashCode();
        }
        return value == 0L ? 1L : value;
    }

    private StructureIndexCache.LootTextEntry buildRelativeWeightNote(int weight, int totalWeight) {
        if (weight <= 0 || totalWeight <= 0) {
            return note("jei_structures.loot_note.relative_weight_unknown");
        }
        double percent = (double) weight * 100.0D / (double) totalWeight;
        return note("jei_structures.loot_note.relative_weight", String.valueOf(weight), String.valueOf(totalWeight), formatDecimal(percent));
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
            return "n=" + describeNumberProvider(object.get("n"), "?") + ", p=" + describeNumberProvider(object.get("p"), "?");
        }
        if ("minecraft:score".equals(type)) {
            return "scoreboard";
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

    private List<StructureIndexCache.LootTextEntry> describeConditions(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return List.of();
        }
        List<StructureIndexCache.LootTextEntry> descriptions = new ArrayList<>();
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
                descriptions.add(note("jei_structures.loot_note.condition_random_chance", describeNumberProvider(condition.get("chance"), "?")));
                continue;
            }
            if ("minecraft:random_chance_with_looting".equals(type)) {
                descriptions.add(note("jei_structures.loot_note.condition_random_chance_with_looting", describeNumberProvider(condition.get("chance"), "?"), describeNumberProvider(condition.get("looting_multiplier"), "?")));
                continue;
            }
            descriptions.add(note("jei_structures.loot_note.condition_type", simplifyKey(type)));
        }
        return List.copyOf(descriptions);
    }

    private List<StructureIndexCache.LootTextEntry> describeFunctions(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return List.of();
        }
        List<StructureIndexCache.LootTextEntry> descriptions = new ArrayList<>();
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
                descriptions.add(note("jei_structures.loot_note.count_set", describeNumberProvider(function.get("count"), "?")));
                continue;
            }
            if ("minecraft:limit_count".equals(type)) {
                descriptions.add(note("jei_structures.loot_note.count_limit", buildRangeText(function.get("min"), function.get("max"))));
                continue;
            }
            descriptions.add(note("jei_structures.loot_note.function_type", simplifyKey(type)));
        }
        return List.copyOf(descriptions);
    }

    private List<StructureIndexCache.LootTextEntry> finalizeCountNotes(List<StructureIndexCache.LootTextEntry> countNotes) {
        if (countNotes == null || countNotes.isEmpty()) {
            return List.of(note("jei_structures.loot_note.count_default"));
        }
        return copyNotes(countNotes);
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
            JeiStructures.LOGGER.warn("Failed to read loot table: {}", location, exception);
            return null;
        }
    }

    private ResourceLocation toLootTableLocation(ResourceLocation lootTableId) {
        return ResourceLocation.fromNamespaceAndPath(lootTableId.getNamespace(), "loot_table/" + lootTableId.getPath() + ".json");
    }

    private ResourceLocation toLegacyLootTableLocation(ResourceLocation lootTableId) {
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
            entry.itemStackTag = sourceEntry.itemStackTag != null ? sourceEntry.itemStackTag : "";
            entry.weight = sourceEntry.weight;
            entry.quality = sourceEntry.quality;
            entry.rollsText = sourceEntry.rollsText != null ? sourceEntry.rollsText : "";
            entry.bonusRollsText = sourceEntry.bonusRollsText != null ? sourceEntry.bonusRollsText : "";
            entry.chanceText = sourceEntry.chanceText != null ? sourceEntry.chanceText : "";
            entry.countText = sourceEntry.countText != null ? sourceEntry.countText : "";
            entry.chanceNotes = copyNotes(sourceEntry.chanceNotes);
            entry.countNotes = copyNotes(sourceEntry.countNotes);
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

    private static List<StructureIndexCache.LootTextEntry> mergeNotes(List<StructureIndexCache.LootTextEntry> first, List<StructureIndexCache.LootTextEntry> second) {
        List<StructureIndexCache.LootTextEntry> result = new ArrayList<>();
        result.addAll(copyNotes(first));
        result.addAll(copyNotes(second));
        return List.copyOf(result);
    }

    private static List<StructureIndexCache.LootTextEntry> copyNotes(List<StructureIndexCache.LootTextEntry> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<StructureIndexCache.LootTextEntry> result = new ArrayList<>(source.size());
        for (StructureIndexCache.LootTextEntry sourceEntry : source) {
            if (sourceEntry == null || sourceEntry.translationKey == null || sourceEntry.translationKey.isBlank()) {
                continue;
            }
            StructureIndexCache.LootTextEntry entry = new StructureIndexCache.LootTextEntry();
            entry.translationKey = sourceEntry.translationKey;
            entry.args = sourceEntry.args != null ? new ArrayList<>(sourceEntry.args) : new ArrayList<>();
            result.add(entry);
        }
        return result;
    }

    private static StructureIndexCache.LootTextEntry note(String translationKey, String... args) {
        StructureIndexCache.LootTextEntry entry = new StructureIndexCache.LootTextEntry();
        entry.translationKey = translationKey != null ? translationKey : "";
        if (args != null) {
            for (String arg : args) {
                entry.args.add(arg != null ? arg : "");
            }
        }
        return entry;
    }

    private static String notesToEnglish(List<StructureIndexCache.LootTextEntry> notes, String fallback) {
        if (notes == null || notes.isEmpty()) {
            return fallback;
        }
        List<String> parts = new ArrayList<>();
        for (StructureIndexCache.LootTextEntry note : notes) {
            String text = noteToEnglish(note);
            if (!text.isBlank()) {
                parts.add(text);
            }
        }
        return parts.isEmpty() ? fallback : String.join("; ", parts);
    }

    private static String noteToEnglish(StructureIndexCache.LootTextEntry note) {
        if (note == null || note.translationKey == null) {
            return "";
        }
        List<String> args = note.args != null ? note.args : List.of();
        return switch (note.translationKey) {
            case "jei_structures.loot_note.relative_weight_unknown" -> "Unknown relative weight";
            case "jei_structures.loot_note.relative_weight" -> "Relative weight " + arg(args, 0) + "/" + arg(args, 1) + " (~" + arg(args, 2) + "%)";
            case "jei_structures.loot_note.tag_expansion" -> "Tag expansion: " + arg(args, 0);
            case "jei_structures.loot_note.nested_loot_table" -> "Nested loot table: " + arg(args, 0);
            case "jei_structures.loot_note.condition_random_chance" -> "Random chance " + arg(args, 0) + "%";
            case "jei_structures.loot_note.condition_random_chance_with_looting" -> "Random chance " + arg(args, 0) + ", looting bonus " + arg(args, 1);
            case "jei_structures.loot_note.condition_type" -> "Condition: " + arg(args, 0);
            case "jei_structures.loot_note.count_default" -> "Default count";
            case "jei_structures.loot_note.count_set" -> "Count " + arg(args, 0);
            case "jei_structures.loot_note.count_limit" -> "Limit count " + arg(args, 0);
            case "jei_structures.loot_note.function_type" -> "Function: " + arg(args, 0);
            default -> "";
        };
    }

    private static String arg(List<String> args, int index) {
        if (args == null || index < 0 || index >= args.size()) {
            return "";
        }
        return args.get(index);
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
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null) {
            return itemId;
        }
        return item.getDescription().getString();
    }
}
