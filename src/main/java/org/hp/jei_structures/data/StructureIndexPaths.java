package org.hp.jei_structures.data;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class StructureIndexPaths {

    private StructureIndexPaths() {
    }

    public static Path getJsonPath() {
        return FMLPaths.CONFIGDIR.get().resolve("jei_structures").resolve("structure_index.json");
    }

    public static Path getBinaryCachePath() {
        return FMLPaths.CONFIGDIR.get().resolve("jei_structures").resolve("structure_index.cache");
    }
}
