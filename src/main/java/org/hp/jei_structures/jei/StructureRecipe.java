package org.hp.jei_structures.jei;

import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.hp.jei_structures.JeiStructures;
import org.hp.jei_structures.data.StructureIndexCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class StructureRecipe {

    private static final int TITLE_HEIGHT = 15;
    private static final int TEXT_LINE_HEIGHT = 10;
    private static final int SECTION_SPACING = 8;
    private static final int COMPACT_SECTION_SPACING = 9;
    private static final int SUMMARY_SECTION_SPACING = 11;
    private static final int LEAD_DETAIL_SECTION_SPACING = 12;
    private static final int TAIL_SECTION_SPACING = 11;
    private static final int MERGED_ITEM_SECTION_SPACING = 4;
    private static final int SECTION_DIVIDER_SPACING = 2;
    private static final int TITLE_CONTENT_GAP = 3;
    private static final int LEAD_DETAIL_TITLE_CONTENT_GAP = 4;
    private static final int TAIL_TITLE_CONTENT_GAP = 5;
    private static final int MERGED_ITEM_TITLE_CONTENT_GAP = 1;
    private static final int SECTION_GRID_TOP_PADDING = 6;
    private static final int EMPHASIZED_GRID_TOP_PADDING = 7;
    private static final int LEAD_DETAIL_GRID_TOP_PADDING = 7;
    private static final int MERGED_ITEM_GRID_TOP_PADDING = 2;
    private static final int SECTION_BOTTOM_PADDING = 7;
    private static final int CARD_PADDING_TOP = 6;
    private static final int CARD_PADDING_BOTTOM = 7;
    private static final int LEAD_DETAIL_CARD_PADDING_TOP = 8;
    private static final int LEAD_DETAIL_CARD_PADDING_BOTTOM = 10;
    private static final int COMPACT_CARD_PADDING_TOP = 3;
    private static final int COMPACT_CARD_PADDING_BOTTOM = 3;
    private static final int SUMMARY_CARD_PADDING_TOP = 6;
    private static final int SUMMARY_CARD_PADDING_BOTTOM = 6;
    private static final int TAIL_CARD_PADDING_TOP = 7;
    private static final int TAIL_CARD_PADDING_BOTTOM = 9;
    private static final int TOTAL_CONTENT_BOTTOM_GAP = 8;
    private static final int SLOT_SPACING = 18;
    private static final int GRID_COLUMNS = 6;

    private final ResourceLocation id;
    private final StructureIndexCache.StructureEntry entry;
    private final Component displayName;
    private final List<ContentBlock> contentBlocks;
    private final Map<String, List<Component>> slotTooltips;
    private final ItemStack iconStack;
    private final List<ItemStack> lookupInputs;
    private final List<ItemStack> lookupOutputs;

    public StructureRecipe(StructureIndexCache.StructureEntry entry) {
        this.entry = copyEntry(entry);
        this.id = new ResourceLocation(JeiStructures.MODID, sanitize(this.entry.structureId));
        this.displayName = StructureTextHelper.getStructureComponent(this.entry.structureId);
        LinkedHashMap<String, List<Component>> tooltipMap = new LinkedHashMap<>();
        this.contentBlocks = List.copyOf(buildContentBlocks(tooltipMap));
        this.slotTooltips = Map.copyOf(tooltipMap);
        this.iconStack = resolveIconStack();
        this.lookupInputs = buildLookupInputs();
        this.lookupOutputs = List.copyOf(lookupInputs);
    }

    public ResourceLocation getId() {
        return id;
    }

    public StructureIndexCache.StructureEntry getEntry() {
        return copyEntry(entry);
    }

    public Component getDisplayName() {
        return displayName;
    }

    public ItemStack getIconStack() {
        return iconStack.copy();
    }

    public List<ItemStack> getLookupInputs() {
        return copyStacks(lookupInputs);
    }

    public List<ItemStack> getLookupOutputs() {
        return copyStacks(lookupOutputs);
    }

    public List<ContentBlock> getContentBlocks() {
        return List.copyOf(contentBlocks);
    }

    public List<Component> getSlotTooltips(String slotName) {
        if (slotName == null || slotName.isBlank()) {
            return List.of();
        }
        return slotTooltips.getOrDefault(slotName, List.of());
    }

    public int getTitleHeight() {
        return TITLE_HEIGHT;
    }

    public int getWrapWidth() {
        return StructureRecipeCategory.getTextWrapPixelWidth();
    }

    public int getTextLineHeight() {
        return TEXT_LINE_HEIGHT;
    }

    public int getSectionSpacing() {
        return SECTION_SPACING;
    }

    public int getCompactSectionSpacing() {
        return COMPACT_SECTION_SPACING;
    }

    public int getSummarySectionSpacing() {
        return SUMMARY_SECTION_SPACING;
    }

    public int getLeadDetailSectionSpacing() {
        return LEAD_DETAIL_SECTION_SPACING;
    }

    public int getTailSectionSpacing() {
        return TAIL_SECTION_SPACING;
    }

    public int getMergedItemSectionSpacing() {
        return MERGED_ITEM_SECTION_SPACING;
    }

    public int getSectionGridTopPadding() {
        return SECTION_GRID_TOP_PADDING;
    }

    public int getEmphasizedGridTopPadding() {
        return EMPHASIZED_GRID_TOP_PADDING;
    }

    public int getLeadDetailGridTopPadding() {
        return LEAD_DETAIL_GRID_TOP_PADDING;
    }

    public int getMergedItemGridTopPadding() {
        return MERGED_ITEM_GRID_TOP_PADDING;
    }

    public int getSectionDividerSpacing() {
        return SECTION_DIVIDER_SPACING;
    }

    public int getTitleContentGap() {
        return TITLE_CONTENT_GAP;
    }

    public int getLeadDetailTitleContentGap() {
        return LEAD_DETAIL_TITLE_CONTENT_GAP;
    }

    public int getTailTitleContentGap() {
        return TAIL_TITLE_CONTENT_GAP;
    }

    public int getMergedItemTitleContentGap() {
        return MERGED_ITEM_TITLE_CONTENT_GAP;
    }

    public int getSectionBottomPadding() {
        return SECTION_BOTTOM_PADDING;
    }

    public int getCardPaddingTop() {
        return CARD_PADDING_TOP;
    }

    public int getCardPaddingBottom() {
        return CARD_PADDING_BOTTOM;
    }

    public int getLeadDetailCardPaddingTop() {
        return LEAD_DETAIL_CARD_PADDING_TOP;
    }

    public int getLeadDetailCardPaddingBottom() {
        return LEAD_DETAIL_CARD_PADDING_BOTTOM;
    }

    public int getCompactCardPaddingTop() {
        return COMPACT_CARD_PADDING_TOP;
    }

    public int getCompactCardPaddingBottom() {
        return COMPACT_CARD_PADDING_BOTTOM;
    }

    public int getSummaryCardPaddingTop() {
        return SUMMARY_CARD_PADDING_TOP;
    }

    public int getSummaryCardPaddingBottom() {
        return SUMMARY_CARD_PADDING_BOTTOM;
    }

    public int getTailCardPaddingTop() {
        return TAIL_CARD_PADDING_TOP;
    }

    public int getTailCardPaddingBottom() {
        return TAIL_CARD_PADDING_BOTTOM;
    }

    public int getGridColumns() {
        return GRID_COLUMNS;
    }

    public int getSlotSpacing() {
        return SLOT_SPACING;
    }

    public int getTotalContentHeight() {
        return StructureRecipeCategory.getHeaderContentOffset(this) + getMergedContentHeight() + TOTAL_CONTENT_BOTTOM_GAP;
    }

    public int getMergedContentHeight() {
        if (contentBlocks.isEmpty()) {
            return 0;
        }
        int height = 0;
        for (ContentBlock block : contentBlocks) {
            height += block.getHeight(this);
        }
        return Math.max(height - contentBlocks.get(contentBlocks.size() - 1).getTrailingSpacing(this), 0);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return List.of();
        }
        List<ItemStack> copies = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (stack == null) {
                continue;
            }
            copies.add(stack.copy());
        }
        return List.copyOf(copies);
    }

    private static List<Component> copyComponents(List<Component> components) {
        if (components == null || components.isEmpty()) {
            return List.of();
        }
        List<Component> copies = new ArrayList<>(components.size());
        for (Component component : components) {
            copies.add(component != null ? component : Component.empty());
        }
        return List.copyOf(copies);
    }

    private static Map<Integer, Integer> copyGapMap(Map<Integer, Integer> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Integer> copies = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : gaps.entrySet()) {
            Integer lineIndex = entry.getKey();
            Integer gap = entry.getValue();
            if (lineIndex == null || gap == null) {
                continue;
            }
            copies.put(lineIndex, gap);
        }
        return Map.copyOf(copies);
    }

    private static StructureIndexCache.StructureEntry copyEntry(StructureIndexCache.StructureEntry entry) {
        if (entry == null) {
            StructureIndexCache.StructureEntry copy = new StructureIndexCache.StructureEntry();
            copy.structureId = "unknown";
            return copy;
        }
        StructureIndexCache.StructureEntry copy = new StructureIndexCache.StructureEntry();
        copy.structureId = valueOrEmpty(entry.structureId);
        copy.structureType = valueOrEmpty(entry.structureType);
        copy.generationStep = valueOrEmpty(entry.generationStep);
        copy.generationBiomes = copyStrings(entry.generationBiomes);
        copy.resolvedGenerationBiomes = copyStrings(entry.resolvedGenerationBiomes);
        copy.generationBiomeDimensions = copyStringListMap(entry.generationBiomeDimensions);
        copy.templateIds = copyStrings(entry.templateIds);
        copy.spawnOverridesEntities = copyStrings(entry.spawnOverridesEntities);
        copy.templateEntities = copyStrings(entry.templateEntities);
        copy.spawnedEntities = copyStrings(entry.spawnedEntities);
        copy.allMobEntityIds = copyStrings(entry.allMobEntityIds);
        copy.allMobEggItemIds = copyStrings(entry.allMobEggItemIds);
        copy.entityLootItems = copyStrings(entry.entityLootItems);
        copy.containerLootItems = copyStrings(entry.containerLootItems);
        copy.suspiciousLootItems = copyStrings(entry.suspiciousLootItems);
        copy.allEntityLootItemIds = copyStrings(entry.allEntityLootItemIds);
        copy.allContainerLootItemIds = copyStrings(entry.allContainerLootItemIds);
        copy.allSuspiciousLootItemIds = copyStrings(entry.allSuspiciousLootItemIds);
        copy.allLootItemIds = copyStrings(entry.allLootItemIds);
        copy.specialDisplayBlocks = copyStrings(entry.specialDisplayBlocks);
        copy.specialInfos = copySpecialInfos(entry.specialInfos);
        copy.spawners = copySpawners(entry.spawners);
        copy.containers = copyLootBindings(entry.containers);
        copy.suspiciousBlocks = copyLootBindings(entry.suspiciousBlocks);
        copy.manualLootBindings = copyLootBindings(entry.manualLootBindings);
        return copy;
    }

    private static List<StructureIndexCache.SpecialInfoEntry> copySpecialInfos(List<StructureIndexCache.SpecialInfoEntry> specialInfos) {
        if (specialInfos == null || specialInfos.isEmpty()) {
            return new ArrayList<>();
        }
        List<StructureIndexCache.SpecialInfoEntry> copies = new ArrayList<>(specialInfos.size());
        for (StructureIndexCache.SpecialInfoEntry specialInfo : specialInfos) {
            if (specialInfo == null) {
                continue;
            }
            StructureIndexCache.SpecialInfoEntry copy = new StructureIndexCache.SpecialInfoEntry();
            copy.targetType = valueOrEmpty(specialInfo.targetType);
            copy.targetId = valueOrEmpty(specialInfo.targetId);
            copy.translationKey = valueOrEmpty(specialInfo.translationKey);
            copies.add(copy);
        }
        return copies;
    }

    private static List<StructureIndexCache.SpawnerEntry> copySpawners(List<StructureIndexCache.SpawnerEntry> spawners) {
        if (spawners == null || spawners.isEmpty()) {
            return new ArrayList<>();
        }
        List<StructureIndexCache.SpawnerEntry> copies = new ArrayList<>(spawners.size());
        for (StructureIndexCache.SpawnerEntry spawner : spawners) {
            if (spawner == null) {
                continue;
            }
            StructureIndexCache.SpawnerEntry copy = new StructureIndexCache.SpawnerEntry();
            copy.templateId = valueOrEmpty(spawner.templateId);
            copy.entityId = valueOrEmpty(spawner.entityId);
            copies.add(copy);
        }
        return copies;
    }

    private static List<StructureIndexCache.LootBinding> copyLootBindings(List<StructureIndexCache.LootBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return new ArrayList<>();
        }
        List<StructureIndexCache.LootBinding> copies = new ArrayList<>(bindings.size());
        for (StructureIndexCache.LootBinding binding : bindings) {
            if (binding == null) {
                continue;
            }
            StructureIndexCache.LootBinding copy = new StructureIndexCache.LootBinding();
            copy.templateId = valueOrEmpty(binding.templateId);
            copy.blockId = valueOrEmpty(binding.blockId);
            copy.lootTableId = valueOrEmpty(binding.lootTableId);
            copy.storedItemIds = copyStrings(binding.storedItemIds);
            copy.itemIds = copyStrings(binding.itemIds);
            copy.lootTables = copyLootTableDetails(binding.lootTables);
            copies.add(copy);
        }
        return copies;
    }

    private static List<StructureIndexCache.LootTableDetail> copyLootTableDetails(List<StructureIndexCache.LootTableDetail> details) {
        if (details == null || details.isEmpty()) {
            return new ArrayList<>();
        }
        List<StructureIndexCache.LootTableDetail> copies = new ArrayList<>(details.size());
        for (StructureIndexCache.LootTableDetail detail : details) {
            if (detail == null) {
                continue;
            }
            StructureIndexCache.LootTableDetail copy = new StructureIndexCache.LootTableDetail();
            copy.lootTableId = valueOrEmpty(detail.lootTableId);
            copy.entries = copyLootItemEntries(detail.entries);
            copies.add(copy);
        }
        return copies;
    }

    private static List<StructureIndexCache.LootItemEntry> copyLootItemEntries(List<StructureIndexCache.LootItemEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }
        List<StructureIndexCache.LootItemEntry> copies = new ArrayList<>(entries.size());
        for (StructureIndexCache.LootItemEntry source : entries) {
            if (source == null) {
                continue;
            }
            StructureIndexCache.LootItemEntry copy = new StructureIndexCache.LootItemEntry();
            copy.itemId = valueOrEmpty(source.itemId);
            copy.weight = source.weight;
            copy.quality = source.quality;
            copy.rollsText = valueOrEmpty(source.rollsText);
            copy.bonusRollsText = valueOrEmpty(source.bonusRollsText);
            copy.chanceText = valueOrEmpty(source.chanceText);
            copy.countText = valueOrEmpty(source.countText);
            copy.chanceNotes = copyLootTextEntries(source.chanceNotes);
            copy.countNotes = copyLootTextEntries(source.countNotes);
            copies.add(copy);
        }
        return copies;
    }

    private static List<StructureIndexCache.LootTextEntry> copyLootTextEntries(List<StructureIndexCache.LootTextEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }
        List<StructureIndexCache.LootTextEntry> copies = new ArrayList<>(entries.size());
        for (StructureIndexCache.LootTextEntry source : entries) {
            if (source == null) {
                continue;
            }
            StructureIndexCache.LootTextEntry copy = new StructureIndexCache.LootTextEntry();
            copy.translationKey = valueOrEmpty(source.translationKey);
            copy.args = copyStrings(source.args);
            copies.add(copy);
        }
        return copies;
    }

    private static List<String> copyStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> copies = new ArrayList<>(values.size());
        for (String value : values) {
            copies.add(valueOrEmpty(value));
        }
        return copies;
    }

    private static Map<String, List<String>> copyStringListMap(Map<String, List<String>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copies = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank()) {
                copies.put(entry.getKey(), copyStrings(entry.getValue()));
            }
        }
        return Map.copyOf(copies);
    }

    private List<ContentBlock> buildContentBlocks(Map<String, List<Component>> tooltipMap) {
        List<ContentBlock> blocks = new ArrayList<>();
        int[] slotCounter = new int[]{0};

        addSpecialInfoBlock(blocks, tooltipMap, slotCounter);
        addSpecialBlocksBlock(blocks, slotCounter);
        addStructureEntitiesBlock(blocks, slotCounter);
        addEntityLootBlock(blocks, slotCounter);
        addStoredItemsBlocks(blocks, tooltipMap, slotCounter);
        addBlockLootBlocks(blocks, tooltipMap, slotCounter);

        if (blocks.isEmpty()) {
            blocks.add(ContentBlock.textOnly(
                    StructureTextHelper.component("jei_structures.section.no_content").withStyle(ChatFormatting.DARK_GRAY)
            ));
        }
        return blocks;
    }

    private void addSpecialInfoBlock(List<ContentBlock> blocks, Map<String, List<Component>> tooltipMap, int[] slotCounter) {
        List<SpecialInfoDisplay> displays = buildSpecialInfoDisplays();
        if (displays.isEmpty()) {
            return;
        }
        List<SlotDisplay> slots = new ArrayList<>();
        for (SpecialInfoDisplay display : displays) {
            String slotName = nextSlotName("special_info", slotCounter);
            slots.add(SlotDisplay.item(RecipeIngredientRole.INPUT, display.stack(), slotName));
            tooltipMap.put(slotName, buildSpecialInfoTooltip(display));
        }
        blocks.add(ContentBlock.leadCombinedGroup(
                StructureTextHelper.component("jei_structures.section.special_info"),
                List.of(Component.translatable("jei_structures.special_info.hint").withStyle(ChatFormatting.DARK_GRAY)),
                slots
        ));
    }

    private void addSpecialBlocksBlock(List<ContentBlock> blocks, int[] slotCounter) {
        List<ItemStack> stacks = toItems(sortAndUnique(entry.specialDisplayBlocks, StructureTextHelper::getBlockName));
        if (stacks.isEmpty()) {
            return;
        }
        blocks.add(ContentBlock.combinedGroup(
                StructureTextHelper.component("jei_structures.section.special_blocks"),
                List.of(),
                toPlainSlots(stacks, RecipeIngredientRole.INPUT, "special_block", slotCounter)
        ));
    }

    private void addStructureEntitiesBlock(List<ContentBlock> blocks, int[] slotCounter) {
        List<ItemStack> entityStacks = toEntityItems(sortAndUnique(entry.allMobEntityIds, StructureTextHelper::getEntityName));
        if (entityStacks.isEmpty()) {
            return;
        }
        blocks.add(ContentBlock.combinedGroup(
                StructureTextHelper.component("jei_structures.section.structure_entities"),
                List.of(),
                toPlainSlots(entityStacks, RecipeIngredientRole.INPUT, "structure_entity", slotCounter)
        ));
    }

    private void addEntityLootBlock(List<ContentBlock> blocks, int[] slotCounter) {
        List<ItemStack> lootStacks = toItems(sortAndUnique(entry.allEntityLootItemIds, StructureTextHelper::getItemName));
        if (lootStacks.isEmpty()) {
            return;
        }
        blocks.add(ContentBlock.combinedGroup(
                StructureTextHelper.component("jei_structures.section.entity_loot"),
                List.of(),
                toPlainSlots(lootStacks, RecipeIngredientRole.OUTPUT, "entity_loot", slotCounter)
        ));
    }

    private void addStoredItemsBlocks(List<ContentBlock> blocks, Map<String, List<Component>> tooltipMap, int[] slotCounter) {
        List<StoredItemsGroup> groups = buildStoredItemGroups();
        boolean first = true;
        for (StoredItemsGroup group : groups) {
            List<SlotDisplay> slots = new ArrayList<>();
            for (String itemId : group.itemIds()) {
                ItemStack stack = toItem(itemId);
                if (stack.isEmpty()) {
                    continue;
                }
                String slotName = nextSlotName("stored_item", slotCounter);
                slots.add(SlotDisplay.item(RecipeIngredientRole.OUTPUT, stack, slotName));
                tooltipMap.put(slotName, List.of(
                        Component.translatable("jei_structures.tooltip.block_id", group.blockId()).withStyle(ChatFormatting.DARK_GRAY)
                ));
            }
            if (slots.isEmpty()) {
                continue;
            }
            List<Component> lines = List.of(Component.translatable(
                    "jei_structures.block_loot.block_label",
                    StructureTextHelper.getBlockName(group.blockId()),
                    group.blockId()
            ));
            blocks.add(ContentBlock.combinedGroup(
                    first ? StructureTextHelper.component("jei_structures.section.stored_items") : Component.empty(),
                    lines,
                    slots
            ));
            first = false;
        }
    }

    private void addBlockLootBlocks(List<ContentBlock> blocks, Map<String, List<Component>> tooltipMap, int[] slotCounter) {
        List<BlockLootGroup> groups = buildBlockLootGroups();
        boolean first = true;
        for (BlockLootGroup group : groups) {
            for (StructureIndexCache.LootTableDetail detail : group.lootTables()) {
                if (detail == null || detail.entries == null || detail.entries.isEmpty()) {
                    continue;
                }
                List<SlotDisplay> slots = new ArrayList<>();
                for (StructureIndexCache.LootItemEntry entry : detail.entries) {
                    ItemStack stack = toItem(entry.itemId);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    String slotName = nextSlotName("block_loot", slotCounter);
                    slots.add(SlotDisplay.item(RecipeIngredientRole.OUTPUT, stack, slotName));
                    tooltipMap.put(slotName, buildLootTooltip(group.blockId(), detail, entry));
                }
                if (slots.isEmpty()) {
                    continue;
                }
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable(
                        "jei_structures.block_loot.block_label",
                        StructureTextHelper.getBlockName(group.blockId()),
                        group.blockId()
                ));
                lines.add(Component.translatable("jei_structures.block_loot.loot_table", detail.lootTableId));
                blocks.add(ContentBlock.combinedGroup(
                        first ? StructureTextHelper.component("jei_structures.section.block_loot_tables") : Component.empty(),
                        lines,
                        slots
                ));
                first = false;
            }
        }
    }

    private List<SpecialInfoDisplay> buildSpecialInfoDisplays() {
        if (entry.specialInfos == null || entry.specialInfos.isEmpty()) {
            return List.of();
        }
        List<SpecialInfoDisplay> displays = new ArrayList<>();
        for (StructureIndexCache.SpecialInfoEntry info : entry.specialInfos) {
            if (info == null || info.targetId == null || info.targetId.isBlank()) {
                continue;
            }
            ItemStack stack = "block".equals(info.targetType) ? toBlockItem(info.targetId) : toEntityItem(info.targetId);
            if (stack.isEmpty()) {
                continue;
            }
            displays.add(new SpecialInfoDisplay(
                    valueOrEmpty(info.targetType),
                    valueOrEmpty(info.targetId),
                    valueOrEmpty(info.translationKey),
                    stack
            ));
        }
        displays.sort(Comparator
                .comparingInt((SpecialInfoDisplay display) -> "block".equals(display.targetType()) ? 0 : 1)
                .thenComparing(display -> display.stack().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SpecialInfoDisplay::targetId, String.CASE_INSENSITIVE_ORDER));
        return displays;
    }

    private List<StoredItemsGroup> buildStoredItemGroups() {
        Map<String, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        addStoredItems(grouped, entry.containers);
        addStoredItems(grouped, entry.suspiciousBlocks);
        addStoredItems(grouped, entry.manualLootBindings);
        List<StoredItemsGroup> groups = new ArrayList<>();
        for (Map.Entry<String, LinkedHashSet<String>> group : grouped.entrySet()) {
            if (group.getValue().isEmpty()) {
                continue;
            }
            groups.add(new StoredItemsGroup(group.getKey(), new ArrayList<>(group.getValue())));
        }
        groups.sort(Comparator
                .comparing((StoredItemsGroup group) -> StructureTextHelper.getBlockName(group.blockId()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(StoredItemsGroup::blockId, String.CASE_INSENSITIVE_ORDER));
        return groups;
    }

    private List<BlockLootGroup> buildBlockLootGroups() {
        LinkedHashMap<String, LinkedHashMap<String, StructureIndexCache.LootTableDetail>> grouped = new LinkedHashMap<>();
        addLootGroups(grouped, entry.containers);
        addLootGroups(grouped, entry.suspiciousBlocks);
        addLootGroups(grouped, entry.manualLootBindings);
        List<BlockLootGroup> groups = new ArrayList<>();
        for (Map.Entry<String, LinkedHashMap<String, StructureIndexCache.LootTableDetail>> blockEntry : grouped.entrySet()) {
            List<StructureIndexCache.LootTableDetail> details = new ArrayList<>(blockEntry.getValue().values());
            details.sort(Comparator.comparing(detail -> detail.lootTableId, String.CASE_INSENSITIVE_ORDER));
            groups.add(new BlockLootGroup(blockEntry.getKey(), details));
        }
        groups.sort(Comparator
                .comparing((BlockLootGroup group) -> StructureTextHelper.getBlockName(group.blockId()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(BlockLootGroup::blockId, String.CASE_INSENSITIVE_ORDER));
        return groups;
    }

    private static void addStoredItems(Map<String, LinkedHashSet<String>> grouped, List<StructureIndexCache.LootBinding> bindings) {
        if (bindings == null) {
            return;
        }
        for (StructureIndexCache.LootBinding binding : bindings) {
            if (binding == null || binding.storedItemIds == null || binding.storedItemIds.isEmpty()) {
                continue;
            }
            String blockId = resolveBlockId(binding);
            LinkedHashSet<String> items = grouped.computeIfAbsent(blockId, key -> new LinkedHashSet<>());
            for (String itemId : binding.storedItemIds) {
                if (itemId != null && !itemId.isBlank()) {
                    items.add(itemId);
                }
            }
        }
    }

    private static void addLootGroups(Map<String, LinkedHashMap<String, StructureIndexCache.LootTableDetail>> grouped, List<StructureIndexCache.LootBinding> bindings) {
        if (bindings == null) {
            return;
        }
        for (StructureIndexCache.LootBinding binding : bindings) {
            if (binding == null || binding.lootTables == null || binding.lootTables.isEmpty()) {
                continue;
            }
            String blockId = resolveBlockId(binding);
            LinkedHashMap<String, StructureIndexCache.LootTableDetail> tables = grouped.computeIfAbsent(blockId, key -> new LinkedHashMap<>());
            for (StructureIndexCache.LootTableDetail detail : binding.lootTables) {
                if (detail == null || detail.lootTableId == null || detail.lootTableId.isBlank()) {
                    continue;
                }
                tables.putIfAbsent(detail.lootTableId, copyLootTableDetail(detail));
            }
        }
    }

    private static StructureIndexCache.LootTableDetail copyLootTableDetail(StructureIndexCache.LootTableDetail detail) {
        StructureIndexCache.LootTableDetail copy = new StructureIndexCache.LootTableDetail();
        copy.lootTableId = valueOrEmpty(detail.lootTableId);
        copy.entries = copyLootItemEntries(detail.entries);
        return copy;
    }

    private List<Component> buildSpecialInfoTooltip(SpecialInfoDisplay display) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("jei_structures.tooltip.special_info").withStyle(ChatFormatting.GOLD));
        if (display.translationKey() != null && !display.translationKey().isBlank()) {
            lines.add(Component.translatable(display.translationKey()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("jei_structures.tooltip.special_info_target", display.targetId()).withStyle(ChatFormatting.DARK_GRAY));
        return List.copyOf(lines);
    }

    private List<Component> buildLootTooltip(String blockId, StructureIndexCache.LootTableDetail detail, StructureIndexCache.LootItemEntry entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("jei_structures.tooltip.block_id", blockId).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("jei_structures.tooltip.loot_table", detail.lootTableId).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("jei_structures.tooltip.loot_weight", entry.weight).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("jei_structures.tooltip.loot_quality", entry.quality).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("jei_structures.tooltip.loot_rolls", valueOrUnknown(entry.rollsText)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("jei_structures.tooltip.loot_bonus_rolls", valueOrUnknown(entry.bonusRollsText)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("jei_structures.tooltip.loot_chance", notesText(entry.chanceNotes, entry.chanceText)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("jei_structures.tooltip.loot_count", notesText(entry.countNotes, entry.countText)).withStyle(ChatFormatting.GRAY));
        return List.copyOf(lines);
    }

    private static Component notesComponent(List<StructureIndexCache.LootTextEntry> notes, String fallbackText) {
        if (notes == null || notes.isEmpty()) {
            return Component.literal(valueOrUnknown(fallbackText));
        }
        Component result = Component.empty();
        boolean first = true;
        for (StructureIndexCache.LootTextEntry note : notes) {
            Component component = lootNoteComponent(note);
            if (component.getString().isBlank()) {
                continue;
            }
            if (!first) {
                result = result.copy().append(Component.translatable("jei_structures.common.separator"));
            }
            result = result.copy().append(component);
            first = false;
        }
        return first ? Component.literal(valueOrUnknown(fallbackText)) : result;
    }

    private static String notesText(List<StructureIndexCache.LootTextEntry> notes, String fallbackText) {
        Component component = notesComponent(notes, fallbackText);
        return component.getString();
    }

    private static Component lootNoteComponent(StructureIndexCache.LootTextEntry note) {
        if (note == null || note.translationKey == null || note.translationKey.isBlank()) {
            return Component.empty();
        }
        List<String> args = note.args != null ? note.args : List.of();
        return switch (args.size()) {
            case 0 -> Component.translatable(note.translationKey);
            case 1 -> Component.translatable(note.translationKey, args.get(0));
            case 2 -> Component.translatable(note.translationKey, args.get(0), args.get(1));
            case 3 -> Component.translatable(note.translationKey, args.get(0), args.get(1), args.get(2));
            case 4 -> Component.translatable(note.translationKey, args.get(0), args.get(1), args.get(2), args.get(3));
            default -> Component.translatable(note.translationKey, args.toArray());
        };
    }

    private ItemStack resolveIconStack() {
        for (ContentBlock block : contentBlocks) {
            for (SlotDisplay slot : block.slots()) {
                if (slot.kind() == SlotKind.ITEM && slot.itemStack() != null && !slot.itemStack().isEmpty()) {
                    return slot.itemStack().copy();
                }
            }
        }
        return StructureRecipeCategory.createStructureBlockStack();
    }

    private List<ItemStack> buildLookupInputs() {
        LinkedHashMap<Item, ItemStack> lookup = new LinkedHashMap<>();
        for (ContentBlock block : contentBlocks) {
            for (SlotDisplay slot : block.slots()) {
                if (slot.kind() != SlotKind.ITEM || slot.itemStack() == null || slot.itemStack().isEmpty()) {
                    continue;
                }
                lookup.putIfAbsent(slot.itemStack().getItem(), slot.itemStack().copy());
            }
        }
        return List.copyOf(lookup.values());
    }

    private static List<SlotDisplay> toPlainSlots(List<ItemStack> stacks, RecipeIngredientRole role, String slotPrefix, int[] slotCounter) {
        List<SlotDisplay> slots = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            slots.add(SlotDisplay.item(role, stack, nextSlotName(slotPrefix, slotCounter)));
        }
        return slots;
    }

    private static String nextSlotName(String prefix, int[] slotCounter) {
        String value = prefix + "_" + slotCounter[0];
        slotCounter[0]++;
        return value;
    }

    private static ItemStack toItem(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    private static ItemStack toBlockItem(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        var block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
        if (block == null) {
            return ItemStack.EMPTY;
        }
        Item item = block.asItem();
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    private static ItemStack toEntityItem(String entityId) {
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        var entityType = id == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (entityType == null) {
            return ItemStack.EMPTY;
        }
        return findEgg(entityType);
    }

    private static List<ItemStack> toItems(List<String> ids) {
        Set<String> unique = new LinkedHashSet<>(ids);
        List<ItemStack> stacks = new ArrayList<>();
        for (String rawId : unique) {
            ItemStack stack = toItem(rawId);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        stacks.sort(Comparator
                .comparing((ItemStack stack) -> stack.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(stack -> String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())), String.CASE_INSENSITIVE_ORDER));
        return stacks;
    }

    private static List<ItemStack> toEntityItems(List<String> ids) {
        Set<String> unique = new LinkedHashSet<>(ids);
        List<ItemStack> stacks = new ArrayList<>();
        for (String rawId : unique) {
            ItemStack stack = toEntityItem(rawId);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        stacks.sort(Comparator
                .comparing((ItemStack stack) -> stack.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(stack -> String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())), String.CASE_INSENSITIVE_ORDER));
        return stacks;
    }

    private static ItemStack findEgg(net.minecraft.world.entity.EntityType<?> entityType) {
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (item instanceof net.minecraft.world.item.SpawnEggItem spawnEggItem && spawnEggItem.getType(null) == entityType) {
                return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String sanitized = value.toLowerCase(Locale.ROOT).replace(':', '_').replace('/', '_').trim();
        return sanitized.isEmpty() ? "unknown" : sanitized;
    }

    private static List<String> sortAndUnique(List<String> values, java.util.function.Function<String, String> nameResolver) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted(Comparator.comparing(nameResolver, String.CASE_INSENSITIVE_ORDER).thenComparing(String::compareToIgnoreCase))
                .toList();
    }

    private static String resolveBlockId(StructureIndexCache.LootBinding binding) {
        if (binding == null) {
            return "jei_structures:manual_binding";
        }
        if (binding.blockId != null && !binding.blockId.isBlank()) {
            return binding.blockId;
        }
        return "jei_structures:manual_binding";
    }

    private static String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? StructureTextHelper.format("jei_structures.common.unknown") : value;
    }

    public record ContentBlock(Component title, List<Component> lines, List<SlotDisplay> slots, Map<Integer, Integer> extraGapAfterLineIndexes, BlockType blockType) {

        public ContentBlock {
            title = title != null ? title : Component.empty();
            lines = copyComponents(lines);
            slots = slots != null ? List.copyOf(slots) : List.of();
            extraGapAfterLineIndexes = copyGapMap(extraGapAfterLineIndexes);
        }

        public static ContentBlock textOnly(Component... lines) {
            return new ContentBlock(Component.empty(), List.of(lines), List.of(), Map.of(), BlockType.DEFAULT);
        }

        public static ContentBlock combinedGroup(Component title, List<Component> lines, List<SlotDisplay> slots) {
            return new ContentBlock(title, List.copyOf(lines), List.copyOf(slots), Map.of(), BlockType.DEFAULT);
        }

        public static ContentBlock leadCombinedGroup(Component title, List<Component> lines, List<SlotDisplay> slots) {
            return new ContentBlock(title, List.copyOf(lines), List.copyOf(slots), Map.of(), BlockType.LEAD_DETAIL);
        }

        public boolean hasTitle() {
            return title != null && !title.getString().isBlank();
        }

        public List<Component> getWrappedTitle(StructureRecipe recipe) {
            return wrapLines(title, recipe.getWrapWidth());
        }

        public int getWrappedTitleLineCount(StructureRecipe recipe) {
            return getWrappedTitle(recipe).size();
        }

        public int getWrappedLineCount(StructureRecipe recipe) {
            return getWrappedLines(recipe).size();
        }

        public List<Component> getWrappedLines(StructureRecipe recipe) {
            List<Component> wrappedLines = new ArrayList<>();
            for (List<Component> wrappedGroup : getWrappedLineGroups(recipe)) {
                wrappedLines.addAll(wrappedGroup);
            }
            return wrappedLines;
        }

        public List<List<Component>> getWrappedLineGroups(StructureRecipe recipe) {
            List<List<Component>> wrappedGroups = new ArrayList<>();
            for (Component line : lines) {
                wrappedGroups.add(wrapLines(line, recipe.getWrapWidth()));
            }
            return wrappedGroups;
        }

        public int getTextStartY(StructureRecipe recipe) {
            int y = getCardPaddingTop(recipe);
            if (hasTitle()) {
                y += getWrappedTitleLineCount(recipe) * recipe.getTextLineHeight();
                y += recipe.getSectionDividerSpacing();
                if (isMergedItemOnlyBlock()) {
                    y += recipe.getMergedItemTitleContentGap();
                } else if (isLeadDetailBlock()) {
                    y += recipe.getLeadDetailTitleContentGap();
                } else if (isTailBlock()) {
                    y += recipe.getTailTitleContentGap();
                } else {
                    y += recipe.getTitleContentGap();
                }
            }
            return y;
        }

        public int getItemStartY(StructureRecipe recipe) {
            int y = getTextStartY(recipe);
            y += getWrappedLineCount(recipe) * recipe.getTextLineHeight();
            y += getExtraLineGapTotal(recipe);
            if (hasTitle()) {
                if (isMergedItemOnlyBlock()) {
                    y += recipe.getMergedItemGridTopPadding();
                } else if (isLeadDetailBlock()) {
                    y += recipe.getLeadDetailGridTopPadding();
                } else if (isEmphasizedDetailBlock()) {
                    y += recipe.getEmphasizedGridTopPadding();
                } else {
                    y += recipe.getSectionGridTopPadding();
                }
            }
            return y;
        }

        public int getHeight(StructureRecipe recipe) {
            int height = getTextStartY(recipe);
            height += getWrappedLineCount(recipe) * recipe.getTextLineHeight();
            height += getExtraLineGapTotal(recipe);
            int ingredientCount = slots.size();
            if (ingredientCount > 0) {
                int rows = Math.max(1, (ingredientCount + recipe.getGridColumns() - 1) / recipe.getGridColumns());
                height = getItemStartY(recipe);
                height += rows * recipe.getSlotSpacing() + recipe.getSectionBottomPadding();
            }
            height += getCardPaddingBottom(recipe);
            return height + getTrailingSpacing(recipe);
        }

        public int getTitleStartY(StructureRecipe recipe) {
            return getCardPaddingTop(recipe);
        }

        public int getTitleDividerY(StructureRecipe recipe) {
            return getTitleStartY(recipe) + getWrappedTitleLineCount(recipe) * recipe.getTextLineHeight();
        }

        private boolean isCompactTextBlock() {
            return !hasTitle() && slots.isEmpty();
        }

        private int getCardPaddingTop(StructureRecipe recipe) {
            if (isSummaryBlock()) {
                return recipe.getSummaryCardPaddingTop();
            }
            if (isLeadDetailBlock()) {
                return recipe.getLeadDetailCardPaddingTop();
            }
            if (isCompactTextBlock()) {
                return recipe.getCompactCardPaddingTop();
            }
            if (isTailBlock()) {
                return recipe.getTailCardPaddingTop();
            }
            return recipe.getCardPaddingTop();
        }

        private int getCardPaddingBottom(StructureRecipe recipe) {
            if (isSummaryBlock()) {
                return recipe.getSummaryCardPaddingBottom();
            }
            if (isLeadDetailBlock()) {
                return recipe.getLeadDetailCardPaddingBottom();
            }
            if (isCompactTextBlock()) {
                return recipe.getCompactCardPaddingBottom();
            }
            if (isTailBlock()) {
                return recipe.getTailCardPaddingBottom();
            }
            return recipe.getCardPaddingBottom();
        }

        public int getTrailingSpacing(StructureRecipe recipe) {
            if (isSummaryBlock()) {
                return recipe.getSummarySectionSpacing();
            }
            if (isMergedItemOnlyBlock()) {
                return recipe.getMergedItemSectionSpacing();
            }
            if (isLeadDetailBlock()) {
                return recipe.getLeadDetailSectionSpacing();
            }
            if (isTailBlock()) {
                return recipe.getTailSectionSpacing();
            }
            if (isCompactTextBlock()) {
                return recipe.getCompactSectionSpacing();
            }
            return recipe.getSectionSpacing();
        }

        public boolean isEmphasizedDetailBlock() {
            return hasTitle() && !slots.isEmpty();
        }

        public boolean isLeadDetailBlock() {
            return isEmphasizedDetailBlock() && blockType == BlockType.LEAD_DETAIL;
        }

        public boolean isMergedItemOnlyBlock() {
            return hasTitle() && lines.isEmpty() && !slots.isEmpty();
        }

        public boolean isTailBlock() {
            return blockType == BlockType.TAIL;
        }

        public boolean isSummaryBlock() {
            return blockType == BlockType.SUMMARY;
        }

        public int getExtraLineGapAfter(int lineIndex) {
            Integer configuredGap = extraGapAfterLineIndexes.get(lineIndex);
            if (configuredGap != null) {
                return configuredGap;
            }
            if (isCompactTextBlock() && lineIndex == 1) {
                return 1;
            }
            if (isLeadDetailBlock() && lineIndex == 0) {
                return 1;
            }
            return 0;
        }

        public int getExtraLineGapTotal(StructureRecipe recipe) {
            int groupCount = lines.size();
            if (groupCount <= 0) {
                return 0;
            }
            int total = 0;
            for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
                total += getExtraLineGapAfter(groupIndex);
            }
            return total;
        }
    }

    public record SlotDisplay(RecipeIngredientRole role, SlotKind kind, ItemStack itemStack, StructureBiomeIcon biome, String slotName) {

        public static SlotDisplay item(RecipeIngredientRole role, ItemStack itemStack, String slotName) {
            return new SlotDisplay(role, SlotKind.ITEM, itemStack.copy(), null, slotName);
        }

        public static SlotDisplay biome(StructureBiomeIcon biome, String slotName) {
            return new SlotDisplay(RecipeIngredientRole.RENDER_ONLY, SlotKind.BIOME, ItemStack.EMPTY, biome, slotName);
        }
    }

    public enum SlotKind {
        ITEM,
        BIOME
    }

    public enum BlockType {
        DEFAULT,
        SUMMARY,
        LEAD_DETAIL,
        TAIL
    }

    private record SpecialInfoDisplay(String targetType, String targetId, String translationKey, ItemStack stack) {
    }

    private record StoredItemsGroup(String blockId, List<String> itemIds) {
    }

    private record BlockLootGroup(String blockId, List<StructureIndexCache.LootTableDetail> lootTables) {
    }

    private static List<Component> wrapLines(Component component, int wrapWidth) {
        if (component == null) {
            return List.of();
        }
        String text = component.getString();
        List<Component> result = new ArrayList<>();
        if (text.isBlank()) {
            return result;
        }
        String remaining = text.trim();
        while (!remaining.isBlank()) {
            int breakIndex = findPixelBreakIndex(remaining, wrapWidth);
            String part = remaining.substring(0, breakIndex).trim();
            if (!part.isBlank()) {
                result.add(Component.literal(part).withStyle(component.getStyle()));
            }
            if (breakIndex >= remaining.length()) {
                break;
            }
            remaining = remaining.substring(breakIndex).trim();
        }
        return result;
    }

    private static int findPixelBreakIndex(String text, int wrapWidth) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        if (font.width(text) <= wrapWidth) {
            return text.length();
        }
        int bestBreak = Math.min(text.length(), 1);
        for (int index = 1; index <= text.length(); index++) {
            String part = text.substring(0, index);
            if (font.width(part) > wrapWidth) {
                break;
            }
            bestBreak = index;
            char current = text.charAt(index - 1);
            if (Character.isWhitespace(current) || current == '：' || current == '，' || current == '、' || current == '|' || current == '）') {
                bestBreak = index;
            }
        }
        return Math.max(bestBreak, 1);
    }
}
