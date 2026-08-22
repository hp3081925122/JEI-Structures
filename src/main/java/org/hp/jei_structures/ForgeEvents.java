package org.hp.jei_structures;

import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.hp.jei_structures.command.StructureExportCommands;
import org.hp.jei_structures.data.StructureIndexCache;
import org.hp.jei_structures.data.StructureIndexCacheLoader;
import org.hp.jei_structures.debug.DebugStructureCaptureManager;
import org.hp.jei_structures.debug.StructureCaptureEntityFilter;
import org.hp.jei_structures.export.StructureIndexExporter;
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

    // 在服务器启动完成后，仅在结构索引没有有效内容时执行正式导出。
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        StructureIndexCache cache = StructureIndexCacheLoader.load();
        if (cache != null && cache.structures != null && !cache.structures.isEmpty()) {
            return;
        }
        try {
            var path = StructureIndexExporter.export(event.getServer());
            JeiStructures.LOGGER.info("Automatic structure index export completed: {}", path);
        } catch (Exception exception) {
            JeiStructures.LOGGER.error("Automatic structure index export failed", exception);
        }
    }

    // 在自然生成流程结束前给生物写入可持久化的自然生成标记。
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getSpawnType() != EntitySpawnReason.NATURAL) {
            return;
        }
        StructureCaptureEntityFilter.markNaturalSpawn(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        DebugStructureCaptureManager.recordJoinedEntity(event.getEntity(), event.getLevel());
    }

}
