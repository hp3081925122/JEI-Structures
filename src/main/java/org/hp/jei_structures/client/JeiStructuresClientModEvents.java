package org.hp.jei_structures.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.hp.jei_structures.JeiStructures;

@EventBusSubscriber(modid = JeiStructures.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class JeiStructuresClientModEvents {

    private JeiStructuresClientModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(JeiStructuresClientEvents.OPEN_CURRENT_STRUCTURE);
    }
}
