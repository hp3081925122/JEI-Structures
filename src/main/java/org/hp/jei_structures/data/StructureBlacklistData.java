package org.hp.jei_structures.data;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class StructureBlacklistData {

    private final Rule globalRule = new Rule();
    private final Map<String, Rule> structureRules = new LinkedHashMap<>();

    public void addGlobalRule(Rule rule) {
        globalRule.merge(rule);
    }

    public void addStructureRule(String structureId, Rule rule) {
        if (structureId == null || structureId.isBlank() || rule == null) {
            return;
        }
        structureRules.computeIfAbsent(structureId, key -> new Rule()).merge(rule);
    }

    public boolean isLootTableBlocked(String structureId, String lootTableId) {
        return isBlocked(structureId, lootTableId, Category.LOOT_TABLE);
    }

    public boolean isBlockBlocked(String structureId, String blockId) {
        return isBlocked(structureId, blockId, Category.BLOCK);
    }

    public boolean isContainerBlocked(String structureId, String blockId) {
        return isBlocked(structureId, blockId, Category.CONTAINER);
    }

    public boolean isEntityBlocked(String structureId, String entityId) {
        return isBlocked(structureId, entityId, Category.ENTITY);
    }

    private boolean isBlocked(String structureId, String value, Category category) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (globalRule.contains(category, value)) {
            return true;
        }
        Rule rule = structureRules.get(structureId);
        return rule != null && rule.contains(category, value);
    }

    private enum Category {
        LOOT_TABLE,
        BLOCK,
        CONTAINER,
        ENTITY
    }

    public static final class Rule {
        private final Set<String> lootTables = new LinkedHashSet<>();
        private final Set<String> blocks = new LinkedHashSet<>();
        private final Set<String> containers = new LinkedHashSet<>();
        private final Set<String> entities = new LinkedHashSet<>();

        public void addLootTable(String value) {
            addValue(lootTables, value);
        }

        public void addBlock(String value) {
            addValue(blocks, value);
        }

        public void addContainer(String value) {
            addValue(containers, value);
        }

        public void addEntity(String value) {
            addValue(entities, value);
        }

        public boolean isEmpty() {
            return lootTables.isEmpty() && blocks.isEmpty() && containers.isEmpty() && entities.isEmpty();
        }

        private void merge(Rule other) {
            if (other == null) {
                return;
            }
            lootTables.addAll(other.lootTables);
            blocks.addAll(other.blocks);
            containers.addAll(other.containers);
            entities.addAll(other.entities);
        }

        private boolean contains(Category category, String value) {
            return switch (category) {
                case LOOT_TABLE -> lootTables.contains(value);
                case BLOCK -> blocks.contains(value);
                case CONTAINER -> containers.contains(value);
                case ENTITY -> entities.contains(value);
            };
        }

        private static void addValue(Set<String> values, String value) {
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
    }
}
