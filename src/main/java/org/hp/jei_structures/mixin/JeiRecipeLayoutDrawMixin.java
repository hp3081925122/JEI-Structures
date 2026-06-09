package org.hp.jei_structures.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.renderer.Rect2i;
import org.hp.jei_structures.jei.StructureRecipe;
import org.hp.jei_structures.jei.StructureRecipeCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false)
public abstract class JeiRecipeLayoutDrawMixin {

    @Inject(method = "drawRecipe(Lcom/mojang/blaze3d/vertex/PoseStack;II)V", at = @At("HEAD"), remap = false)
    private void jeiStructures$setStructureRecipeScreenOffset(PoseStack poseStack, int mouseX, int mouseY, CallbackInfo ci) {
        RecipeLayoutAccess access = RecipeLayoutAccess.from(this);
        if (access == null || !(access.recipe() instanceof StructureRecipe) || !(access.category() instanceof StructureRecipeCategory category)) {
            return;
        }
        Rect2i area = access.rect();
        category.setScreenOffset(area.getX(), area.getY());
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
