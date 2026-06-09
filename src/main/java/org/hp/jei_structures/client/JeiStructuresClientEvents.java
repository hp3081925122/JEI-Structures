package org.hp.jei_structures.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.hp.jei_structures.JeiStructures;

@EventBusSubscriber(modid = JeiStructures.MODID, value = Dist.CLIENT)
public final class JeiStructuresClientEvents {

    public static final KeyMapping OPEN_CURRENT_STRUCTURE = new KeyMapping(
            "key.jei_structures.open_current_structure",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_K,
            "key.categories.jei_structures"
    );

    private JeiStructuresClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        CurrentStructureClientState.handleClientTick();
    }
}
