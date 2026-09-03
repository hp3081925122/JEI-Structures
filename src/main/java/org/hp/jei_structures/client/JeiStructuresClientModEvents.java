package org.hp.jei_structures.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.hp.jei_structures.JeiStructures;

@Mod.EventBusSubscriber(modid = JeiStructures.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class JeiStructuresClientModEvents {

    private JeiStructuresClientModEvents() {
    }

    // 把当前结构按键注册到 Minecraft 的控制设置页面。
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(JeiStructuresClientEvents.OPEN_CURRENT_STRUCTURE);
        JeiStructures.LOGGER.debug("Registered current structure key mapping: {}", JeiStructuresClientEvents.OPEN_CURRENT_STRUCTURE.getName());
    }
}
