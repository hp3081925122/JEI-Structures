package org.hp.jei_structures.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.hp.jei_structures.JeiStructures;
import org.hp.jei_structures.export.StructureIndexExporter;

import java.nio.file.Path;

public final class StructureExportCommands {

    private StructureExportCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("jei_structures")
                .requires(source -> source.hasPermission(2));

        root.then(Commands.literal("export")
                .executes(StructureExportCommands::export));
        root.then(DebugCaptureQuickCommands.build());
        dispatcher.register(root);
    }

    private static int export(CommandContext<CommandSourceStack> context) {
        try {
            Path path = StructureIndexExporter.export(context.getSource().getServer());
            context.getSource().sendSuccess(() -> Component.translatable("jei_structures.command.export.success", path), true);
            return 1;
        } catch (Exception exception) {
            String detail = DebugCaptureCommandSupport.buildExceptionDetail(exception);
            JeiStructures.LOGGER.error("JEI Structures export failed", exception);
            context.getSource().sendFailure(Component.translatable("jei_structures.command.export.failure", detail));
            return 0;
        }
    }
}
