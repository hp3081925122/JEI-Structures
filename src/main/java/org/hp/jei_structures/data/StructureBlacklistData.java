package org.hp.jei_structures.data;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class StructureBlacklistData {

    private final Rule globalRule = new Rule();
    private final Map<String, Rule> structureRules = new LinkedHashMap<>();

    public void addGlobalRule(Set<String> lootTables, Set<String> blocks, Set<String> containers, Set<String> entities) {
        addToRule(globalRule, lootTables, blocks, containers, entities);
    }

    public void addStructureRule(String structureId, Set<String> lootTables, Set<String> blocks, Set<String> containers, Set<String> entities) {
        if (structureId == null || structureId.isBlank()) {
            return;
        }
        addToRule(structureRules.computeIfAbsent(structureId, key -> new Rule()), lootTables, blocks, containers, entities);
    }

    public boolean isLootTableBlocked(String structureId, String lootTableId) {
        return isBlocked(structureId, lootTableId, rule -> rule.lootTables);
    }

    public boolean isBlockBlocked(String structureId, String blockId) {
        return isBlocked(structureId, blockId, rule -> rule.blocks);
    }

    public boolean isContainerBlocked(String structureId, String blockId) {
        return isBlocked(structureId, blockId, rule -> rule.containers);
    }

    public boolean isEntityBlocked(String structureId, String entityId) {
        return isBlocked(structureId, entityId, rule -> rule.entities);
    }

    private static void addToRule(Rule rule, Set<String> lootTables, Set<String> blocks, Set<String> containers, Set<String> entities) {
        if (rule == null) {
            return;
        }
        addAll(rule.lootTables, lootTables);
        addAll(rule.blocks, blocks);
        addAll(rule.containers, containers);
        addAll(rule.entities, entities);
    }

    private static void addAll(Set<String> target, Set<String> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (String value : source) {
            if (value != null && !value.isBlank()) {
                target.add(value);
            }
        }
    }

    private boolean isBlocked(String structureId, String value, java.util.function.Function<Rule, Set<String>> valuesGetter) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (valuesGetter.apply(globalRule).contains(value)) {
            return true;
        }
        Rule structureRule = structureRules.get(structureId);
        return structureRule != null && valuesGetter.apply(structureRule).contains(value);
    }

    private static final class Rule {
        private final Set<String> lootTables = new LinkedHashSet<>();
        private final Set<String> blocks = new LinkedHashSet<>();
        private final Set<String> containers = new LinkedHashSet<>();
        private final Set<String> entities = new LinkedHashSet<>();
    }
}
