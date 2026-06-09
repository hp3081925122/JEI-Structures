package org.hp.jei_structures.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.hp.jei_structures.JeiStructures;

@Mod.EventBusSubscriber(modid = JeiStructures.MODID, value = Dist.CLIENT)
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
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        CurrentStructureClientState.handleClientTick();
    }
}
