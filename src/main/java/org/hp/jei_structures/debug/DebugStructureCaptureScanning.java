package org.hp.jei_structures.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.hp.jei_structures.data.LootTableItemResolver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DebugStructureCaptureScanning {

    private DebugStructureCaptureScanning() {
    }

    public static MobSampleResult captureLivingEntities(ServerLevelContext context, BoundingBox box, Set<String> mobIds, boolean discard) {
        if (context == null || context.level() == null || box == null || mobIds == null) {
            return new MobSampleResult(0, 0, mobIds != null ? mobIds.size() : 0);
        }
        int before = mobIds.size();
        int sampled = 0;
        AABB bounds = new AABB(
                box.minX(),
                box.minY(),
                box.minZ(),
                box.maxX() + 1.0D,
                box.maxY() + 1.0D,
                box.maxZ() + 1.0D
        );
        List<LivingEntity> entities = context.level().getEntitiesOfClass(LivingEntity.class, bounds, entity -> entity != null && entity.isAlive() && !context.playerPredicate().test(entity));
        for (LivingEntity entity : entities) {
            sampled++;
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (entityId != null) {
                mobIds.add(entityId.toString());
            }
            if (discard && entity.isAlive()) {
                entity.discard();
            }
        }
        int after = mobIds.size();
        return new MobSampleResult(sampled, Math.max(after - before, 0), after);
    }

    public static LootBlockResult readLootBlock(BlockState state, BlockEntity blockEntity, LootTableItemResolver lootResolver) {
        if (state == null || blockEntity == null) {
            return null;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) {
            return null;
        }
        var nbt = blockEntity.saveWithId(blockEntity.getLevel().registryAccess());
        String lootTableId = readLootTable(nbt);
        if (lootTableId.isBlank()) {
            return null;
        }
        LinkedHashSet<String> items = new LinkedHashSet<>();
        ResourceLocation lootId = ResourceLocation.tryParse(lootTableId);
        if (lootId != null && lootResolver != null) {
            items.addAll(lootResolver.resolveLootItems(lootId));
        }
        return new LootBlockResult(blockId.toString(), lootTableId, Set.copyOf(items));
    }

    public static List<BlockPos> collectLootPositions(net.minecraft.server.level.ServerLevel level, BoundingBox box, List<ChunkPos> placeChunks) {
        if (level == null || box == null || placeChunks == null || placeChunks.isEmpty()) {
            return List.of();
        }
        List<BlockPos> positions = new ArrayList<>();
        for (ChunkPos chunkPos : placeChunks) {
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                BlockPos pos = entry.getKey();
                if (pos != null && box.isInside(pos)) {
                    positions.add(pos.immutable());
                }
            }
        }
        return List.copyOf(positions);
    }

    public static String readLootTable(net.minecraft.nbt.CompoundTag nbt) {
        if (nbt.contains("LootTable", net.minecraft.nbt.Tag.TAG_STRING)) {
            return nbt.getString("LootTable");
        }
        if (nbt.contains("loot_table", net.minecraft.nbt.Tag.TAG_STRING)) {
            return nbt.getString("loot_table");
        }
        return "";
    }

    public record MobSampleResult(int sampledLivingCount, int newMobCount, int totalMobCount) {
    }

    public record LootBlockResult(String blockId, String lootTableId, Set<String> items) {
    }

    public record ServerLevelContext(net.minecraft.server.level.ServerLevel level, java.util.function.Predicate<LivingEntity> playerPredicate) {
    }
}
