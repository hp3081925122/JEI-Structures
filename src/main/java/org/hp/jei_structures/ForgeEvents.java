package org.hp.jei_structures;

import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.hp.jei_structures.command.StructureExportCommands;
import org.hp.jei_structures.data.StructureIndexCache;
import org.hp.jei_structures.data.StructureIndexCacheLoader;
import org.hp.jei_structures.debug.DebugStructureCaptureManager;
import org.hp.jei_structures.debug.StructureCaptureEntityFilter;
import org.hp.jei_structures.export.StructureIndexExporter;

import java.nio.file.Path;

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
    }

    // 在服务器启动完成后，仅在结构索引没有有效内容时执行正式导出。
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        StructureIndexCache cache = StructureIndexCacheLoader.load();
        if (cache.structures != null && !cache.structures.isEmpty()) {
            return;
        }
        try {
            Path path = StructureIndexExporter.export(event.getServer());
            JeiStructures.LOGGER.info("Automatic structure index export completed: {}", path);
        } catch (Exception exception) {
            JeiStructures.LOGGER.error("Automatic structure index export failed", exception);
        }
    }

    // 在自然生成流程结束前给生物写入可持久化的自然生成标记。
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL) {
            return;
        }
        StructureCaptureEntityFilter.markNaturalSpawn(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        DebugStructureCaptureManager.recordJoinedEntity(event.getEntity(), event.getLevel());
    }
}
