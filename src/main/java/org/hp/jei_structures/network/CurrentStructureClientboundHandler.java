package org.hp.jei_structures.network;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.hp.jei_structures.JeiStructures;

public final class CurrentStructureClientboundHandler {

    private CurrentStructureClientboundHandler() {
    }

    // 通过反射把客户端状态更新限制在客户端发行环境。
    public static void handle(String structureId) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> stateClass = Class.forName("org.hp.jei_structures.client.CurrentStructureClientState");
            stateClass.getMethod("updateCurrentStructure", String.class).invoke(null, structureId);
        } catch (ReflectiveOperationException exception) {
            JeiStructures.LOGGER.warn("Failed to update current structure client state", exception);
        }
    }
}
