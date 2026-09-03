package org.hp.jei_structures.tracker;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.network.PacketDistributor;
import org.hp.jei_structures.JeiStructures;
import org.hp.jei_structures.data.StructureIndexCache;
import org.hp.jei_structures.data.StructureIndexCacheLoader;
import org.hp.jei_structures.network.CurrentStructurePacket;
import org.hp.jei_structures.network.JeiStructuresNetwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CurrentStructureTracker {

    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final Map<UUID, String> LAST_SENT_STRUCTURE = new HashMap<>();
    private static List<TrackedStructure> cachedStructures = List.of();
    private static StructureIndexCache cachedSource;
    private static int tickCounter;

    private CurrentStructureTracker() {
    }

    // 每秒检查玩家所在的结构，并在结构变化时同步一次客户端状态。
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        List<TrackedStructure> structures = getTrackedStructures(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            updatePlayer(player, structures);
        }
        LAST_SENT_STRUCTURE.keySet().removeIf(playerId -> server.getPlayerList().getPlayer(playerId) == null);
    }

    // 根据当前结构索引建立服务器端可查询的结构对象缓存。
    private static List<TrackedStructure> getTrackedStructures(MinecraftServer server) {
        StructureIndexCache cache = StructureIndexCacheLoader.load();
        if (cache == cachedSource) {
            return cachedStructures;
        }
        Registry<Structure> registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<TrackedStructure> structures = new ArrayList<>();
        for (StructureIndexCache.StructureEntry entry : cache.structures) {
            ResourceLocation id = ResourceLocation.tryParse(entry.structureId);
            if (id == null) {
                continue;
            }
            Structure structure = registry.get(id);
            if (structure != null) {
                structures.add(new TrackedStructure(entry.structureId, structure));
            }
        }
        cachedSource = cache;
        cachedStructures = List.copyOf(structures);
        JeiStructures.LOGGER.debug("Updated current structure tracker cache: {} structures", cachedStructures.size());
        return cachedStructures;
    }

    // 只在玩家所在结构发生变化时发送网络数据包。
    private static void updatePlayer(ServerPlayer player, List<TrackedStructure> structures) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        String previous = LAST_SENT_STRUCTURE.getOrDefault(player.getUUID(), "");
        String current = level.structureManager().hasAnyStructureAt(player.blockPosition())
                ? findCurrentStructure(level, player, structures, previous)
                : "";
        if (!current.equals(previous)) {
            LAST_SENT_STRUCTURE.put(player.getUUID(), current);
            JeiStructuresNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CurrentStructurePacket(current));
            JeiStructures.LOGGER.debug("Updated current structure for player {}: {}", player.getGameProfile().getName(), current);
        }
    }

    // 优先保留玩家已在其中的结构，避免结构重叠时界面在相邻结构之间跳动。
    private static String findCurrentStructure(ServerLevel level, ServerPlayer player, List<TrackedStructure> structures, String previous) {
        for (TrackedStructure tracked : structures) {
            if (tracked.structureId().equals(previous) && contains(level, player, tracked.structure())) {
                return tracked.structureId();
            }
        }
        for (TrackedStructure tracked : structures) {
            if (contains(level, player, tracked.structure())) {
                return tracked.structureId();
            }
        }
        return "";
    }

    // 使用结构管理器确认玩家是否位于该结构的有效片段内。
    private static boolean contains(ServerLevel level, ServerPlayer player, Structure structure) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), structure);
        return start != null && start.isValid();
    }

    private record TrackedStructure(String structureId, Structure structure) {
    }
}
