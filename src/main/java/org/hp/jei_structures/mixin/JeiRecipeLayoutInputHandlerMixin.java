package org.hp.jei_structures.mixin;

import mezz.jei.api.recipe.category.IRecipeCategory;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.renderer.Rect2i;
import org.hp.jei_structures.jei.StructureRecipe;
import org.hp.jei_structures.jei.StructureRecipeCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Pseudo
@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayoutInputHandler", remap = false)
public abstract class JeiRecipeLayoutInputHandlerMixin {

    @Inject(method = "handleMouseScrolled(DDD)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void jeiStructures$handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaY, CallbackInfoReturnable<Boolean> cir) {
        RecipeLayoutAccess access = RecipeLayoutAccess.from(readRecipeLayout(this));
        if (access == null || !(access.recipe() instanceof StructureRecipe recipe) || !(access.category() instanceof StructureRecipeCategory category)) {
            return;
        }
        Rect2i area = access.rect();
        double recipeMouseX = mouseX - area.getX();
        double recipeMouseY = mouseY - area.getY();
        if (category.handleMouseScrolled(recipe, recipeMouseX, recipeMouseY, scrollDeltaY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "handleMouseDragged(DDLcom/mojang/blaze3d/platform/InputConstants$Key;DD)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void jeiStructures$handleMouseDragged(double mouseX, double mouseY, InputConstants.Key mouseKey, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        RecipeLayoutAccess access = RecipeLayoutAccess.from(readRecipeLayout(this));
        if (access == null || !(access.recipe() instanceof StructureRecipe recipe) || !(access.category() instanceof StructureRecipeCategory category)) {
            return;
        }
        Rect2i area = access.rect();
        double recipeMouseX = mouseX - area.getX();
        double recipeMouseY = mouseY - area.getY();
        if (category.handleMouseDragged(recipe, recipeMouseX, recipeMouseY, mouseKey, dragX, dragY)) {
            cir.setReturnValue(true);
        }
    }

    private static Object readRecipeLayout(Object inputHandler) {
        if (inputHandler == null) {
            return null;
        }
        try {
            Field field = inputHandler.getClass().getDeclaredField("recipeLayout");
            field.setAccessible(true);
            return field.get(inputHandler);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private record RecipeLayoutAccess(IRecipeCategory<?> category, Object recipe, Rect2i rect) {

        private static RecipeLayoutAccess from(Object layout) {
            if (layout == null) {
                return null;
            }
            try {
                Object category = layout.getClass().getMethod("getRecipeCategory").invoke(layout);
                Object recipe = layout.getClass().getMethod("getRecipe").invoke(layout);
                Object rect = layout.getClass().getMethod("getRect").invoke(layout);
                if (category instanceof IRecipeCategory<?> recipeCategory && rect instanceof Rect2i recipeRect) {
                    return new RecipeLayoutAccess(recipeCategory, recipe, recipeRect);
                }
            } catch (ReflectiveOperationException ignored) {
            }
            return null;
        }
    }
}
