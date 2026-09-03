package org.hp.jei_structures.compat;

import net.minecraft.resources.ResourceLocation;
import org.hp.jei_structures.JeiStructures;
import org.hp.jei_structures.jei.StructureRecipe;

import java.lang.reflect.Method;

public final class EmiStructureRecipeOpener {

    private EmiStructureRecipeOpener() {
    }

    // 通过反射调用可选 EMI API，避免未安装 EMI 时服务端加载失败。
    public static boolean open(StructureRecipe targetRecipe) {
        if (targetRecipe == null) {
            return false;
        }
        try {
            Class<?> emiApiClass = Class.forName("dev.emi.emi.api.EmiApi");
            Class<?> emiRecipeClass = Class.forName("dev.emi.emi.api.recipe.EmiRecipe");
            Class<?> emiRecipeManagerClass = Class.forName("dev.emi.emi.api.recipe.EmiRecipeManager");
            Object recipeManager = emiApiClass.getMethod("getRecipeManager").invoke(null);
            if (recipeManager == null) {
                JeiStructures.LOGGER.debug("Cannot open structure recipe through EMI because its recipe manager is unavailable: {}", targetRecipe.getStructureId());
                return false;
            }
            ResourceLocation sourceId = targetRecipe.getId();
            ResourceLocation emiRecipeId = ResourceLocation.fromNamespaceAndPath(sourceId.getNamespace(), "/" + sourceId.getPath());
            Object recipe = emiRecipeManagerClass.getMethod("getRecipe", ResourceLocation.class).invoke(recipeManager, emiRecipeId);
            if (recipe == null) {
                JeiStructures.LOGGER.debug("No native EMI recipe was found for current structure: {}", targetRecipe.getStructureId());
                return false;
            }
            Method displayRecipe = emiApiClass.getMethod("displayRecipe", emiRecipeClass);
            displayRecipe.invoke(null, recipe);
            JeiStructures.LOGGER.debug("Opened current structure recipe through native EMI: {}", targetRecipe.getStructureId());
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            JeiStructures.LOGGER.debug("Failed to open current structure recipe through native EMI: {}", targetRecipe.getStructureId(), exception);
            return false;
        }
    }
}
