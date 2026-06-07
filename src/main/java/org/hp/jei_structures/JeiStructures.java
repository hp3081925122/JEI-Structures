package org.hp.jei_structures;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(JeiStructures.MODID)
public final class JeiStructures {

    public static final String MODID = "jei_structures";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JeiStructures() {
        MinecraftForge.EVENT_BUS.register(ForgeEvents.class);
    }
}

