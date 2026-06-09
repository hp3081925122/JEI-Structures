package org.hp.jei_structures;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.hp.jei_structures.command.StructureExportCommands;
import org.hp.jei_structures.debug.DebugStructureCaptureManager;
import org.hp.jei_structures.tracker.CurrentStructureTracker;

@Mod.EventBusSubscriber(modid = JeiStructures.MODID)
public final class ForgeEvents {

    private ForgeEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        StructureExportCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        DebugStructureCaptureManager.tick(event.getServer());
        CurrentStructureTracker.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        DebugStructureCaptureManager.recordJoinedEntity(event.getEntity(), event.getLevel());
    }
}
