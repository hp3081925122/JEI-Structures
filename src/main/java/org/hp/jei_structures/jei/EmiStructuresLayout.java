package org.hp.jei_structures.jei;

import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarSide;
import net.minecraft.client.Minecraft;

final class EmiStructuresLayout {
    private static final int SCREEN_MARGIN = 52;
    private static final int RECIPE_AREA_HEADER = 46;
    private static final int BOTTOM_WORKSTATION_HEIGHT = 23;

    private EmiStructuresLayout() {
    }

    static int maxRecipeHeight() {
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int backgroundHeight = Math.min(EmiConfig.maximumRecipeScreenHeight, screenHeight - SCREEN_MARGIN - EmiConfig.verticalMargin);
        int recipeHeight = backgroundHeight - RECIPE_AREA_HEADER;
        if (EmiConfig.workstationLocation == SidebarSide.BOTTOM) {
            recipeHeight -= BOTTOM_WORKSTATION_HEIGHT;
        }
        return recipeHeight;
    }
}
