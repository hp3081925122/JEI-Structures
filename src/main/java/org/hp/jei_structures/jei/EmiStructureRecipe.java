package org.hp.jei_structures.jei;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class EmiStructureRecipe implements EmiRecipe {

    private static final int DISPLAY_WIDTH = 204;
    private static final int DISPLAY_HEIGHT = 202;

    private final StructureRecipe recipe;
    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;

    public EmiStructureRecipe(StructureRecipe recipe) {
        this.recipe = recipe;
        List<EmiIngredient> lookupInputs = new ArrayList<>();
        List<EmiStack> lookupOutputs = new ArrayList<>();
        for (ItemStack stack : recipe.getLookupInputs()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            EmiStack emiStack = EmiStack.of(stack);
            lookupInputs.add(emiStack);
            lookupOutputs.add(emiStack.copy());
        }
        this.inputs = List.copyOf(lookupInputs);
        this.outputs = List.copyOf(lookupOutputs);
    }

    StructureRecipe getStructureRecipe() {
        return recipe;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return EmiStructurePlugin.STRUCTURE_INDEX;
    }

    @Override
    public ResourceLocation getId() {
        ResourceLocation sourceId = recipe.getId();
        return ResourceLocation.fromNamespaceAndPath(sourceId.getNamespace(), "/" + sourceId.getPath());
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return DISPLAY_WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return DISPLAY_HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.add(new EmiStructureScrollWidget(this, 0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT));
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }
}
