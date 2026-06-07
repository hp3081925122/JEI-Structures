package org.hp.jei_structures.data;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class StructureIndexPaths {

    private StructureIndexPaths() {
    }

    public static Path getCachePath() {
        return FMLPaths.CONFIGDIR.get().resolve("jei_structures").resolve("structure_index.json");
    }
}

