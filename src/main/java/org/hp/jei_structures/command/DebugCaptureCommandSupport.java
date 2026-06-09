package org.hp.jei_structures.command;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.hp.jei_structures.JeiStructures;
import org.hp.jei_structures.data.StructureIndexCache;
import org.hp.jei_structures.data.StructureIndexCacheLoader;
import org.hp.jei_structures.debug.DebugStructureCaptureTypes;

import java.util.LinkedHashSet;
import java.util.Set;

final class DebugCaptureCommandSupport {

    static final SuggestionProvider<CommandSourceStack> DIMENSION_ID_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(
                    context.getSource().getServer().levelKeys().stream().map(key -> key.location()),
                    builder
            );

    static final SuggestionProvider<CommandSourceStack> STRUCTURE_ID_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(collectSuggestedStructureIds(context.getSource()), builder);

    static final SuggestionProvider<CommandSourceStack> MOD_ID_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(collectSuggestedModIds(context.getSource()), builder);

    static final String ARG_EXCLUDE_MOD_ID = "exclude_mod_id";
    static final String ARG_EXCLUDE_DIMENSION_ID = "exclude_dimension_id";

    private DebugCaptureCommandSupport() {
    }

    static ServerPlayer requirePlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        source.sendFailure(Component.translatable("jei_structures.command.debug_capture.player_only"));
        return null;
    }

    static int handleDebugCaptureStart(CommandSourceStack source, DebugStructureCaptureTypes.StartResult result) {
        return switch (result.state()) {
            case STARTED -> {
                source.sendSuccess(Component.translatable("jei_structures.command.debug_capture.start", result.structureCount(), result.speedMultiplier(), result.outputRoot()), true);
                yield 1;
            }
            case BUSY -> {
                source.sendFailure(Component.translatable("jei_structures.command.debug_capture.busy"));
                yield 0;
            }
            case EMPTY -> {
                source.sendFailure(Component.translatable("jei_structures.command.debug_capture.empty"));
                yield 0;
            }
            case MISSING -> {
                source.sendFailure(Component.translatable("jei_structures.command.debug_capture.missing", result.missingId()));
                yield 0;
            }
        };
    }

    static int handleDebugCaptureException(CommandSourceStack source, Exception exception) {
        String detail = buildExceptionDetail(exception);
        JeiStructures.LOGGER.error("JEI Structures debug capture failed", exception);
        source.sendFailure(Component.translatable("jei_structures.command.debug_capture.failure", detail));
        return 0;
    }

    static String buildExceptionDetail(Exception exception) {
        String detail = exception.getClass().getSimpleName();
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            detail += " - " + exception.getMessage();
        }
        return detail;
    }

    static String formatDuration(long totalSeconds) {
        long safeSeconds = Math.max(totalSeconds, 0L);
        long hours = safeSeconds / 3600L;
        long minutes = (safeSeconds % 3600L) / 60L;
        long seconds = safeSeconds % 60L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    static Iterable<ResourceLocation> collectSuggestedStructureIds(CommandSourceStack source) {
        Set<ResourceLocation> structureIds = new LinkedHashSet<>();
        StructureIndexCache cache = StructureIndexCacheLoader.load();
        if (cache.structures != null) {
            for (StructureIndexCache.StructureEntry entry : cache.structures) {
                if (entry == null || entry.structureId == null || entry.structureId.isBlank()) {
                    continue;
                }
                ResourceLocation structureId = ResourceLocation.tryParse(entry.structureId);
                if (structureId != null) {
                    structureIds.add(structureId);
                }
            }
        }
        if (structureIds.isEmpty()) {
            var registry = source.getServer().registryAccess().registry(Registry.STRUCTURE_REGISTRY);
            if (registry.isPresent()) {
                for (Structure structure : registry.get()) {
                    ResourceLocation structureId = registry.get().getKey(structure);
                    if (structureId != null) {
                        structureIds.add(structureId);
                    }
                }
            }
        }
        return structureIds;
    }

    static Iterable<String> collectSuggestedModIds(CommandSourceStack source) {
        Set<String> modIds = new LinkedHashSet<>();
        for (ResourceLocation structureId : collectSuggestedStructureIds(source)) {
            if (structureId != null) {
                modIds.add(structureId.getNamespace());
            }
        }
        return modIds;
    }
}
