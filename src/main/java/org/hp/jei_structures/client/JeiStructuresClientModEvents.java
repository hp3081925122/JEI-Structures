package org.hp.jei_structures.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.hp.jei_structures.JeiStructures;

@Mod.EventBusSubscriber(modid = JeiStructures.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class JeiStructuresClientModEvents {

    private JeiStructuresClientModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(JeiStructuresClientEvents.OPEN_CURRENT_STRUCTURE);
    }
}
