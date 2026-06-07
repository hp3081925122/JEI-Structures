package org.hp.jei_structures.data;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class StructureBindingPaths {

    private StructureBindingPaths() {
    }

    public static Path getBindingsRoot() {
        return FMLPaths.CONFIGDIR.get().resolve("jei_structures").resolve("bindings");
    }

    public static Path getReportsRoot() {
        return FMLPaths.CONFIGDIR.get().resolve("jei_structures").resolve("reports");
    }

    public static Path getStructureToMobsDir() {
        return getBindingsRoot().resolve("structure_to_mobs");
    }

    public static Path getMobToStructuresDir() {
        return getBindingsRoot().resolve("mob_to_structures");
    }

    public static Path getStructureLootBindingsDir() {
        return getBindingsRoot().resolve("structure_loot_bindings");
    }

    public static Path getLootToStructuresDir() {
        return getBindingsRoot().resolve("loot_to_structures");
    }

    public static Path getReportRunRoot(String runId) {
        return getReportsRoot().resolve(runId);
    }
}
