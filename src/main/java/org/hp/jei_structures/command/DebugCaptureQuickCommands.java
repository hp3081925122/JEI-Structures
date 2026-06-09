package org.hp.jei_structures.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.hp.jei_structures.debug.DebugStructureCaptureManager;
import org.hp.jei_structures.debug.DebugStructureCaptureSupport;
import org.hp.jei_structures.debug.DebugStructureCaptureTypes;

final class DebugCaptureQuickCommands {

    private DebugCaptureQuickCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        var debugQuick = Commands.literal("debug_capture_quick");
        debugQuick.then(Commands.literal("all")
                .executes(DebugCaptureQuickCommands::debugCaptureAll)
                .then(Commands.literal("speed")
                        .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, DebugStructureCaptureSupport.MAX_SPEED_MULTIPLIER))
                                .executes(DebugCaptureQuickCommands::debugCaptureAllSpeed)))
                .then(Commands.literal("exclude_mod")
                        .then(Commands.argument(DebugCaptureCommandSupport.ARG_EXCLUDE_MOD_ID, StringArgumentType.word())
                                .suggests(DebugCaptureCommandSupport.MOD_ID_SUGGESTIONS)
                                .executes(DebugCaptureQuickCommands::debugCaptureAllExcludeMod)
                                .then(Commands.literal("speed")
                                        .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, DebugStructureCaptureSupport.MAX_SPEED_MULTIPLIER))
                                                .executes(DebugCaptureQuickCommands::debugCaptureAllExcludeModSpeed)))))
                .then(Commands.literal("exclude_dimension")
                        .then(Commands.argument(DebugCaptureCommandSupport.ARG_EXCLUDE_DIMENSION_ID, ResourceLocationArgument.id())
                                .suggests(DebugCaptureCommandSupport.DIMENSION_ID_SUGGESTIONS)
                                .executes(DebugCaptureQuickCommands::debugCaptureAllExcludeDimension)
                                .then(Commands.literal("speed")
                                        .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, DebugStructureCaptureSupport.MAX_SPEED_MULTIPLIER))
                                                .executes(DebugCaptureQuickCommands::debugCaptureAllExcludeDimensionSpeed))))));
        debugQuick.then(Commands.literal("dimension")
                .then(Commands.argument("dimension_id", ResourceLocationArgument.id())
                        .suggests(DebugCaptureCommandSupport.DIMENSION_ID_SUGGESTIONS)
                        .executes(DebugCaptureQuickCommands::debugCaptureAllDimension)
                        .then(Commands.literal("speed")
                                .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, DebugStructureCaptureSupport.MAX_SPEED_MULTIPLIER))
                                        .executes(DebugCaptureQuickCommands::debugCaptureAllDimensionSpeed)))));
        debugQuick.then(Commands.literal("mod")
                .then(Commands.argument("mod_id", StringArgumentType.word())
                        .suggests(DebugCaptureCommandSupport.MOD_ID_SUGGESTIONS)
                        .executes(DebugCaptureQuickCommands::debugCaptureMod)
                        .then(Commands.literal("speed")
                                .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, DebugStructureCaptureSupport.MAX_SPEED_MULTIPLIER))
                                        .executes(DebugCaptureQuickCommands::debugCaptureModSpeed)))
                        .then(Commands.literal("dimension")
                                .then(Commands.argument("dimension_id", ResourceLocationArgument.id())
                                        .suggests(DebugCaptureCommandSupport.DIMENSION_ID_SUGGESTIONS)
                                        .executes(DebugCaptureQuickCommands::debugCaptureModDimension)
                                        .then(Commands.literal("speed")
                                                .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, DebugStructureCaptureSupport.MAX_SPEED_MULTIPLIER))
                                                        .executes(DebugCaptureQuickCommands::debugCaptureModDimensionSpeed)))))));
        debugQuick.then(Commands.literal("remaining")
                .executes(DebugCaptureQuickCommands::debugCaptureRemainingAll)
                .then(Commands.literal("speed")
                        .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, DebugStructureCaptureSupport.MAX_SPEED_MULTIPLIER))
                                .executes(DebugCaptureQuickCommands::debugCaptureRemainingAllSpeed)))
                .then(Commands.literal("exclude_mod")
                        .then(Commands.argument(DebugCaptureCommandSupport.ARG_EXCLUDE_MOD_ID, StringArgumentType.word())
                                .suggests(DebugCaptureCommandSupport.MOD_ID_SUGGESTIONS)
                                .executes(DebugCaptureQuickCommands::debugCaptureRemainingAllExcludeMod)
                                .then(Commands.literal("speed")
                                        .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, DebugStructureCaptureSupport.MAX_SPEED_MULTIPLIER))
                                                .executes(DebugCaptureQuickCommands::debugCaptureRemainingAllExcludeModSpeed)))))
                .then(Commands.literal("exclude_dimension")
                        .then(Commands.argument(DebugCaptureCommandSupport.ARG_EXCLUDE_DIMENSION_ID, ResourceLocationArgument.id())
                                .suggests(DebugCaptureCommandSupport.DIMENSION_ID_SUGGESTIONS)
                                .executes(DebugCaptureQuickCommands::debugCaptureRemainingAllExcludeDimension)
                                .then(Commands.literal("speed")
                                        .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, DebugStructureCaptureSupport.MAX_SPEED_MULTIPLIER))
                                                .executes(DebugCaptureQuickCommands::debugCaptureRemainingAllExcludeDimensionSpeed)))))
                .then(Commands.literal("dimension")
                        .then(Commands.argument("dimension_id", ResourceLocationArgument.id())
                                .suggests(DebugCaptureCommandSupport.DIMENSION_ID_SUGGESTIONS)
                                .executes(DebugCaptureQuickCommands::debugCaptureRemainingDimension)
                                .then(Commands.literal("speed")
                                        .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, DebugStructureCaptureSupport.MAX_SPEED_MULTIPLIER))
                                                .executes(DebugCaptureQuickCommands::debugCaptureRemainingDimensionSpeed)))))
                .then(Commands.literal("mod")
                        .then(Commands.argument("mod_id", StringArgumentType.word())
                                .suggests(DebugCaptureCommandSupport.MOD_ID_SUGGESTIONS)
                                .executes(DebugCaptureQuickCommands::debugCaptureRemainingMod)
                                .then(Commands.literal("speed")
                                        .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, DebugStructureCaptureSupport.MAX_SPEED_MULTIPLIER))
                                                .executes(DebugCaptureQuickCommands::debugCaptureRemainingModSpeed)))
                                .then(Commands.literal("dimension")
                                        .then(Commands.argument("dimension_id", ResourceLocationArgument.id())
                                                .suggests(DebugCaptureCommandSupport.DIMENSION_ID_SUGGESTIONS)
                                                .executes(DebugCaptureQuickCommands::debugCaptureRemainingModDimension)
                                                .then(Commands.literal("speed")
                                                        .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, DebugStructureCaptureSupport.MAX_SPEED_MULTIPLIER))
                                                                .executes(DebugCaptureQuickCommands::debugCaptureRemainingModDimensionSpeed))))))));
        debugQuick.then(Commands.literal("structure")
                .then(Commands.argument("structure_id", ResourceLocationArgument.id())
                        .suggests(DebugCaptureCommandSupport.STRUCTURE_ID_SUGGESTIONS)
                        .executes(DebugCaptureQuickCommands::debugCaptureStructure)
                        .then(Commands.literal("speed")
                                .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, DebugStructureCaptureSupport.MAX_SPEED_MULTIPLIER))
                                        .executes(DebugCaptureQuickCommands::debugCaptureStructureSpeed)))
                        .then(Commands.literal("dimension")
                                .then(Commands.argument("dimension_id", ResourceLocationArgument.id())
                                        .suggests(DebugCaptureCommandSupport.DIMENSION_ID_SUGGESTIONS)
                                        .executes(DebugCaptureQuickCommands::debugCaptureStructureDimension)
                                        .then(Commands.literal("speed")
                                                .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, DebugStructureCaptureSupport.MAX_SPEED_MULTIPLIER))
                                                        .executes(DebugCaptureQuickCommands::debugCaptureStructureDimensionSpeed)))))));
        debugQuick.then(Commands.literal("status")
                .executes(DebugCaptureQuickCommands::debugCaptureStatus));
        debugQuick.then(Commands.literal("stop")
                .executes(DebugCaptureQuickCommands::debugCaptureStop));
        return debugQuick;
    }

    private static int debugCaptureAll(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureAll(context, 1, null);
    }

    private static int debugCaptureAllSpeed(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureAll(context, IntegerArgumentType.getInteger(context, "multiplier"), null);
    }

    private static int debugCaptureAllDimension(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureAll(context, 1, ResourceLocationArgument.getId(context, "dimension_id"));
    }

    private static int debugCaptureAllDimensionSpeed(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureAll(context, IntegerArgumentType.getInteger(context, "multiplier"), ResourceLocationArgument.getId(context, "dimension_id"));
    }

    private static int debugCaptureAllExcludeMod(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureAll(context, 1, null, StringArgumentType.getString(context, DebugCaptureCommandSupport.ARG_EXCLUDE_MOD_ID), null);
    }

    private static int debugCaptureAllExcludeModSpeed(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureAll(context, IntegerArgumentType.getInteger(context, "multiplier"), null, StringArgumentType.getString(context, DebugCaptureCommandSupport.ARG_EXCLUDE_MOD_ID), null);
    }

    private static int debugCaptureAllExcludeDimension(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureAll(context, 1, null, null, ResourceLocationArgument.getId(context, DebugCaptureCommandSupport.ARG_EXCLUDE_DIMENSION_ID));
    }

    private static int debugCaptureAllExcludeDimensionSpeed(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureAll(context, IntegerArgumentType.getInteger(context, "multiplier"), null, null, ResourceLocationArgument.getId(context, DebugCaptureCommandSupport.ARG_EXCLUDE_DIMENSION_ID));
    }

    private static int debugCaptureMod(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureMod(context, 1, null);
    }

    private static int debugCaptureModSpeed(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureMod(context, IntegerArgumentType.getInteger(context, "multiplier"), null);
    }

    private static int debugCaptureModDimension(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureMod(context, 1, ResourceLocationArgument.getId(context, "dimension_id"));
    }

    private static int debugCaptureModDimensionSpeed(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureMod(context, IntegerArgumentType.getInteger(context, "multiplier"), ResourceLocationArgument.getId(context, "dimension_id"));
    }

    private static int debugCaptureRemainingAll(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureRemainingAll(context, 1, null);
    }

    private static int debugCaptureRemainingAllSpeed(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureRemainingAll(context, IntegerArgumentType.getInteger(context, "multiplier"), null);
    }

    private static int debugCaptureRemainingDimension(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureRemainingAll(context, 1, ResourceLocationArgument.getId(context, "dimension_id"));
    }

    private static int debugCaptureRemainingDimensionSpeed(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureRemainingAll(context, IntegerArgumentType.getInteger(context, "multiplier"), ResourceLocationArgument.getId(context, "dimension_id"));
    }

    private static int debugCaptureRemainingAllExcludeMod(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureRemainingAll(context, 1, null, StringArgumentType.getString(context, DebugCaptureCommandSupport.ARG_EXCLUDE_MOD_ID), null);
    }

    private static int debugCaptureRemainingAllExcludeModSpeed(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureRemainingAll(context, IntegerArgumentType.getInteger(context, "multiplier"), null, StringArgumentType.getString(context, DebugCaptureCommandSupport.ARG_EXCLUDE_MOD_ID), null);
    }

    private static int debugCaptureRemainingAllExcludeDimension(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureRemainingAll(context, 1, null, null, ResourceLocationArgument.getId(context, DebugCaptureCommandSupport.ARG_EXCLUDE_DIMENSION_ID));
    }

    private static int debugCaptureRemainingAllExcludeDimensionSpeed(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureRemainingAll(context, IntegerArgumentType.getInteger(context, "multiplier"), null, null, ResourceLocationArgument.getId(context, DebugCaptureCommandSupport.ARG_EXCLUDE_DIMENSION_ID));
    }

    private static int debugCaptureRemainingMod(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureRemainingMod(context, 1, null);
    }

    private static int debugCaptureRemainingModSpeed(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureRemainingMod(context, IntegerArgumentType.getInteger(context, "multiplier"), null);
    }

    private static int debugCaptureRemainingModDimension(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureRemainingMod(context, 1, ResourceLocationArgument.getId(context, "dimension_id"));
    }

    private static int debugCaptureRemainingModDimensionSpeed(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureRemainingMod(context, IntegerArgumentType.getInteger(context, "multiplier"), ResourceLocationArgument.getId(context, "dimension_id"));
    }

    private static int debugCaptureStructure(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureStructure(context, 1, null);
    }

    private static int debugCaptureStructureSpeed(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureStructure(context, IntegerArgumentType.getInteger(context, "multiplier"), null);
    }

    private static int debugCaptureStructureDimension(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureStructure(context, 1, ResourceLocationArgument.getId(context, "dimension_id"));
    }

    private static int debugCaptureStructureDimensionSpeed(CommandContext<CommandSourceStack> context) {
        return startDebugCaptureStructure(context, IntegerArgumentType.getInteger(context, "multiplier"), ResourceLocationArgument.getId(context, "dimension_id"));
    }

    private static int startDebugCaptureAll(CommandContext<CommandSourceStack> context, int speedMultiplier, ResourceLocation dimensionId) {
        return startDebugCaptureAll(context, speedMultiplier, dimensionId, null, null);
    }

    private static int startDebugCaptureAll(CommandContext<CommandSourceStack> context, int speedMultiplier, ResourceLocation dimensionId, String excludedNamespace, ResourceLocation excludedDimensionId) {
        ServerPlayer player = DebugCaptureCommandSupport.requirePlayer(context.getSource());
        if (player == null) {
            return 0;
        }
        try {
            return DebugCaptureCommandSupport.handleDebugCaptureStart(context.getSource(), DebugStructureCaptureManager.startAll(player, speedMultiplier, dimensionId, excludedNamespace, excludedDimensionId));
        } catch (Exception exception) {
            return DebugCaptureCommandSupport.handleDebugCaptureException(context.getSource(), exception);
        }
    }

    private static int startDebugCaptureRemainingAll(CommandContext<CommandSourceStack> context, int speedMultiplier, ResourceLocation dimensionId) {
        return startDebugCaptureRemainingAll(context, speedMultiplier, dimensionId, null, null);
    }

    private static int startDebugCaptureRemainingAll(CommandContext<CommandSourceStack> context, int speedMultiplier, ResourceLocation dimensionId, String excludedNamespace, ResourceLocation excludedDimensionId) {
        ServerPlayer player = DebugCaptureCommandSupport.requirePlayer(context.getSource());
        if (player == null) {
            return 0;
        }
        try {
            return DebugCaptureCommandSupport.handleDebugCaptureStart(context.getSource(), DebugStructureCaptureManager.startRemainingAll(player, speedMultiplier, dimensionId, excludedNamespace, excludedDimensionId));
        } catch (Exception exception) {
            return DebugCaptureCommandSupport.handleDebugCaptureException(context.getSource(), exception);
        }
    }

    private static int startDebugCaptureMod(CommandContext<CommandSourceStack> context, int speedMultiplier, ResourceLocation dimensionId) {
        ServerPlayer player = DebugCaptureCommandSupport.requirePlayer(context.getSource());
        if (player == null) {
            return 0;
        }
        try {
            return DebugCaptureCommandSupport.handleDebugCaptureStart(
                    context.getSource(),
                    DebugStructureCaptureManager.startByNamespace(
                            player,
                            StringArgumentType.getString(context, "mod_id"),
                            speedMultiplier,
                            dimensionId
                    )
            );
        } catch (Exception exception) {
            return DebugCaptureCommandSupport.handleDebugCaptureException(context.getSource(), exception);
        }
    }

    private static int startDebugCaptureRemainingMod(CommandContext<CommandSourceStack> context, int speedMultiplier, ResourceLocation dimensionId) {
        ServerPlayer player = DebugCaptureCommandSupport.requirePlayer(context.getSource());
        if (player == null) {
            return 0;
        }
        try {
            return DebugCaptureCommandSupport.handleDebugCaptureStart(
                    context.getSource(),
                    DebugStructureCaptureManager.startRemainingByNamespace(
                            player,
                            StringArgumentType.getString(context, "mod_id"),
                            speedMultiplier,
                            dimensionId
                    )
            );
        } catch (Exception exception) {
            return DebugCaptureCommandSupport.handleDebugCaptureException(context.getSource(), exception);
        }
    }

    private static int startDebugCaptureStructure(CommandContext<CommandSourceStack> context, int speedMultiplier, ResourceLocation dimensionId) {
        ServerPlayer player = DebugCaptureCommandSupport.requirePlayer(context.getSource());
        if (player == null) {
            return 0;
        }
        try {
            return DebugCaptureCommandSupport.handleDebugCaptureStart(
                    context.getSource(),
                    DebugStructureCaptureManager.startSingle(
                            player,
                            ResourceLocationArgument.getId(context, "structure_id"),
                            speedMultiplier,
                            dimensionId
                    )
            );
        } catch (Exception exception) {
            return DebugCaptureCommandSupport.handleDebugCaptureException(context.getSource(), exception);
        }
    }

    private static int debugCaptureStatus(CommandContext<CommandSourceStack> context) {
        DebugStructureCaptureTypes.StatusSnapshot snapshot = DebugStructureCaptureManager.getStatus();
        if (!snapshot.active()) {
            context.getSource().sendSuccess(() -> Component.translatable("jei_structures.command.debug_capture.status.idle"), false);
            return 1;
        }
        String key = snapshot.stopRequested()
                ? "jei_structures.command.debug_capture.status.active_stopping"
                : "jei_structures.command.debug_capture.status.active";
        context.getSource().sendSuccess(() -> Component.translatable(
                key,
                DebugStructureCaptureSupport.getStructureDisplayComponent(snapshot.structureId()),
                snapshot.structureIndex(),
                snapshot.structureCount(),
                snapshot.capturedCount(),
                snapshot.captureTotalCount(),
                Component.translatable(snapshot.phase()),
                snapshot.attempt(),
                snapshot.attemptCount(),
                snapshot.speedMultiplier(),
                snapshot.loadedChunkCount(),
                snapshot.totalChunkCount(),
                snapshot.dimensionId(),
                snapshot.dimensionIndex(),
                snapshot.dimensionCount(),
                snapshot.dimensionLocateCompleted(),
                snapshot.dimensionLocateTotal(),
                snapshot.dimensionLocateSucceeded(),
                snapshot.activeLocateCount(),
                snapshot.maxLocateCount(),
                DebugStructureCaptureSupport.getStructureDisplayComponent(snapshot.waitingLocateStructureId()),
                DebugCaptureCommandSupport.formatDuration(snapshot.waitingLocateSeconds()),
                snapshot.dimensionCaptureCompleted(),
                snapshot.dimensionCaptureTotal(),
                DebugCaptureCommandSupport.formatDuration(snapshot.elapsedSeconds()),
                DebugCaptureCommandSupport.formatDuration(snapshot.structureElapsedSeconds()),
                snapshot.outputRoot() != null ? snapshot.outputRoot().toString() : ""
        ), false);
        return 1;
    }

    private static int debugCaptureStop(CommandContext<CommandSourceStack> context) {
        DebugStructureCaptureTypes.StopResult result = DebugStructureCaptureManager.requestStop();
        if (!result.active()) {
            context.getSource().sendFailure(Component.translatable("jei_structures.command.debug_capture.stop.idle"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("jei_structures.command.debug_capture.stop.requested", result.outputRoot() != null ? result.outputRoot().toString() : ""), true);
        DebugStructureCaptureTypes.TimingSnapshot timingSnapshot = result.timingSnapshot();
        if (timingSnapshot != null) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "jei_structures.command.debug_capture.finished_timing",
                    timingSnapshot.totalDuration(),
                    timingSnapshot.cooldownDuration(),
                    timingSnapshot.locateDuration(),
                    timingSnapshot.prepareDuration(),
                    timingSnapshot.placeDuration(),
                    timingSnapshot.lootDuration(),
                    timingSnapshot.mobWaitDuration(),
                    timingSnapshot.mobScanDuration(),
                    timingSnapshot.clearDuration(),
                    timingSnapshot.writeDuration(),
                    timingSnapshot.otherDuration(),
                    timingSnapshot.slowLocateStructureCount()
            ), false);
        }
        return 1;
    }
}
