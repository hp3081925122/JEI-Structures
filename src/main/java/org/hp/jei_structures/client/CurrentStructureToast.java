package org.hp.jei_structures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.hp.jei_structures.jei.StructureRecipeCategory;
import org.hp.jei_structures.jei.StructureTextHelper;

public final class CurrentStructureToast implements Toast {

    private static final long DISPLAY_TIME_MS = 5000L;
    private static final int WIDTH = 180;
    private static final int TEXT_X = 35;
    private static final int TEXT_WIDTH = WIDTH - TEXT_X - 8;
    private final Component title;
    private final Component hint;
    private final ItemStack icon;

    private CurrentStructureToast(String structureId) {
        this.title = Component.translatable("jei_structures.toast.current_structure", StructureTextHelper.getStructureComponent(structureId));
        this.hint = Component.translatable("jei_structures.toast.open_jei_hint", JeiStructuresClientEvents.OPEN_CURRENT_STRUCTURE.getTranslatedKeyMessage());
        this.icon = StructureRecipeCategory.createStructureBlockStack();
    }

    public static void show(String structureId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getToasts() != null) {
            minecraft.getToasts().addToast(new CurrentStructureToast(structureId));
        }
    }

    @Override
    public Visibility render(PoseStack poseStack, ToastComponent toastComponent, long timeSinceLastVisible) {
        drawBackground(poseStack);
        toastComponent.getMinecraft().getItemRenderer().renderAndDecorateItem(icon, 8, 8);
        Font font = toastComponent.getMinecraft().font;
        drawAdaptive(poseStack, font, title, TEXT_X, 7, 0xAA00AA);
        drawAdaptive(poseStack, font, hint, TEXT_X, 18, 0x222222);
        return timeSinceLastVisible >= DISPLAY_TIME_MS ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    private static void drawAdaptive(PoseStack poseStack, Font font, Component component, int x, int y, int color) {
        String text = component.getString();
        int width = font.width(text);
        if (width <= TEXT_WIDTH) {
            font.draw(poseStack, component, x, y, color);
            return;
        }
        float scale = Mth.clamp((float) TEXT_WIDTH / (float) width, 0.75F, 1.0F);
        if (scale > 0.75F) {
            poseStack.pushPose();
            poseStack.translate(x, y, 0);
            poseStack.scale(scale, scale, 1.0F);
            font.draw(poseStack, component, 0, 0, color);
            poseStack.popPose();
            return;
        }
        font.draw(poseStack, font.plainSubstrByWidth(text, TEXT_WIDTH - font.width("...")) + "...", x, y, color);
    }

    private static void drawBackground(PoseStack poseStack) {
        GuiComponent.fill(poseStack, 0, 0, WIDTH, 32, 0xFF202020);
        GuiComponent.fill(poseStack, 2, 2, WIDTH - 2, 30, 0xFFE8E8E8);
        GuiComponent.fill(poseStack, 4, 4, WIDTH - 4, 28, 0xFFD7D7D7);
    }
}
