package org.hp.jei_structures.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {

    @Invoker("releaseLightTicket")
    void jei_structures$releaseLightTicket(ChunkPos chunkPos);
}
