package org.hp.jei_structures;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.hp.jei_structures.command.StructureExportCommands;
import org.hp.jei_structures.debug.DebugStructureCaptureManager;
import org.hp.jei_structures.tracker.CurrentStructureTracker;

@EventBusSubscriber(modid = JeiStructures.MODID)
public final class ForgeEvents {

    private ForgeEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        StructureExportCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DebugStructureCaptureManager.tick(event.getServer());
        CurrentStructureTracker.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        DebugStructureCaptureManager.recordJoinedEntity(event.getEntity(), event.getLevel());
    }

}
