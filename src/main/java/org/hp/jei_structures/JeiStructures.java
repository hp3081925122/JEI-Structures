package org.hp.jei_structures;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.hp.jei_structures.config.JeiStructuresConfig;
import org.hp.jei_structures.network.JeiStructuresNetwork;
import org.slf4j.Logger;

@Mod(JeiStructures.MODID)
public final class JeiStructures {

    public static final String MODID = "jei_structures";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JeiStructures(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, JeiStructuresConfig.COMMON_SPEC);
        modEventBus.addListener(JeiStructuresNetwork::register);
    }
}

