package org.hp.jei_structures;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import org.hp.jei_structures.config.JeiStructuresConfig;
import org.slf4j.Logger;

@Mod(JeiStructures.MODID)
public final class JeiStructures {

    public static final String MODID = "jei_structures";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JeiStructures() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, JeiStructuresConfig.COMMON_SPEC);
        MinecraftForge.EVENT_BUS.register(ForgeEvents.class);
    }
}

