package org.hp.jei_structures.jei;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.hp.jei_structures.JeiStructures;

@EmiEntrypoint
public final class EmiStructurePlugin implements EmiPlugin {

    public static final EmiRecipeCategory STRUCTURE_INDEX = new EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(JeiStructures.MODID, "structure_index"),
            EmiStack.of(Items.STRUCTURE_BLOCK)
    );

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(STRUCTURE_INDEX);
        for (StructureRecipe recipe : JeiStructuresPlugin.getSharedRecipes()) {
            registry.addRecipe(new EmiStructureRecipe(recipe));
        }
        JeiStructures.LOGGER.debug("Registered {} native EMI structure recipes", JeiStructuresPlugin.getSharedRecipes().size());
    }
}
