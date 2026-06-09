package org.hp.jei_structures.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
    public Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        drawBackground(graphics);
        graphics.renderFakeItem(icon, 8, 8);
        Font font = toastComponent.getMinecraft().font;
        drawAdaptive(graphics, font, title, TEXT_X, 7, 0xAA00AA);
        drawAdaptive(graphics, font, hint, TEXT_X, 18, 0x222222);
        double duration = DISPLAY_TIME_MS * toastComponent.getNotificationDisplayTimeMultiplier();
        return timeSinceLastVisible >= duration ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    private static void drawAdaptive(GuiGraphics graphics, Font font, Component component, int x, int y, int color) {
        String text = component.getString();
        int width = font.width(text);
        if (width <= TEXT_WIDTH) {
            graphics.drawString(font, component, x, y, color, false);
            return;
        }
        float scale = Mth.clamp((float) TEXT_WIDTH / (float) width, 0.75F, 1.0F);
        if (scale > 0.75F) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, component, 0, 0, color, false);
            graphics.pose().popPose();
            return;
        }
        graphics.drawString(font, font.plainSubstrByWidth(text, TEXT_WIDTH - font.width("...")) + "...", x, y, color, false);
    }

    private static void drawBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, WIDTH, 32, 0xFF202020);
        graphics.fill(2, 2, WIDTH - 2, 30, 0xFFE8E8E8);
        graphics.fill(4, 4, WIDTH - 4, 28, 0xFFD7D7D7);
    }
}
