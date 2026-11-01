package org.hp.jei_structures.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;

@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {

    @Accessor(value = "distanceManager", remap = false)
    DistanceManager jei_structures$getDistanceManager();

    @Accessor(value = "ticketStorage", remap = false)
    TicketStorage jei_structures$getTicketStorage();

    @Invoker(value = "getVisibleChunkIfPresent", remap = false)
    ChunkHolder jei_structures$getVisibleChunkIfPresent(long chunkPos);

    @Invoker(value = "getChunkFutureMainThread", remap = false)
    CompletableFuture<ChunkResult<ChunkAccess>> jei_structures$getChunkFutureMainThread(int x, int z, ChunkStatus status, boolean requireChunk);
}
