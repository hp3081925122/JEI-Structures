package org.hp.jei_structures.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.hp.jei_structures.data.StructureIndexCache;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class DebugStructureCaptureExport {

    private DebugStructureCaptureExport() {
    }

    public static void writeCurrentStructureFiles(Path structureToMobsRoot, Path structureLootBindingsRoot, ResourceLocation structureId, String aggregateStructureId, Set<String> mobIds, Collection<JsonObject> lootBindingObjects) throws Exception {
        if (structureId == null || aggregateStructureId == null || structureToMobsRoot == null || structureLootBindingsRoot == null) {
            return;
        }
        Path mobFile = buildStructureFilePath(structureToMobsRoot, structureId);
        Files.createDirectories(mobFile.getParent());
        JsonObject mobRoot = new JsonObject();
        JsonArray mobArray = new JsonArray();
        mobIds.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(mobArray::add);
        mobRoot.add(aggregateStructureId, mobArray);
        try (Writer writer = Files.newBufferedWriter(mobFile, StandardCharsets.UTF_8)) {
            StructureIndexCache.GSON.toJson(mobRoot, writer);
        }

        Path lootFile = buildStructureFilePath(structureLootBindingsRoot, structureId);
        Files.createDirectories(lootFile.getParent());
        JsonArray lootArray = new JsonArray();
        lootBindingObjects.forEach(lootArray::add);
        try (Writer writer = Files.newBufferedWriter(lootFile, StandardCharsets.UTF_8)) {
            StructureIndexCache.GSON.toJson(lootArray, writer);
        }
    }

    public static void writeFailureFile(Path outputRoot, String fileName, List<FailureEntryData> failures) throws Exception {
        if (outputRoot == null || fileName == null || fileName.isBlank()) {
            return;
        }
        Path failureFile = outputRoot.resolve(fileName);
        JsonArray array = new JsonArray();
        failures.stream()
                .sorted(Comparator
                        .comparing(FailureEntryData::structureId, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(FailureEntryData::attempt)
                        .thenComparing(FailureEntryData::phase, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(FailureEntryData::reason, String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> array.add(entry.toJson()));
        try (Writer writer = Files.newBufferedWriter(failureFile, StandardCharsets.UTF_8)) {
            StructureIndexCache.GSON.toJson(array, writer);
        }
    }

    public static FailureEntryData createFailureEntry(String structureId, String structureType, int attempt, ResourceKey<Level> levelKey, String levelId, BlockPos origin, String phaseName, String reason) {
        String safeReason = reason != null && !reason.isBlank() ? reason : "unknown_reason";
        String safeDimension = levelId != null && !levelId.isBlank()
                ? levelId
                : levelKey != null ? levelKey.location().toString() : "";
        BlockPos safeOrigin = origin != null ? origin : BlockPos.ZERO;
        return new FailureEntryData(
                structureId != null ? structureId : "",
                structureType != null ? structureType : "",
                safeDimension,
                safeOrigin.getX() + "," + safeOrigin.getY() + "," + safeOrigin.getZ(),
                phaseName != null ? phaseName : "",
                safeReason,
                attempt
        );
    }

    public static BlockPos parseOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return BlockPos.ZERO;
        }
        String[] parts = origin.split(",", 3);
        if (parts.length != 3) {
            return BlockPos.ZERO;
        }
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            );
        } catch (NumberFormatException exception) {
            return BlockPos.ZERO;
        }
    }

    public static Path buildStructureFilePath(Path root, ResourceLocation structureId) {
        return root
                .resolve(structureId.getNamespace())
                .resolve(structureId.getPath().replace("/", "__") + ".json");
    }

    public record FailureEntryData(String structureId, String structureType, String dimension, String origin, String phase, String reason, int attempt) {

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("structure_id", structureId);
            json.addProperty("structure_type", structureType);
            json.addProperty("dimension", dimension);
            json.addProperty("origin", origin);
            json.addProperty("phase", phase);
            json.addProperty("reason", reason);
            json.addProperty("attempt", attempt);
            return json;
        }
    }
}
