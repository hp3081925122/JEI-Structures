package org.hp.jei_structures.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
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
    private long visibleTimeMs;
    private double displayDurationMs = DISPLAY_TIME_MS;

    private CurrentStructureToast(String structureId) {
        this.title = Component.translatable("jei_structures.toast.current_structure", StructureTextHelper.getStructureComponent(structureId));
        this.hint = Component.translatable("jei_structures.toast.open_jei_hint", JeiStructuresClientEvents.OPEN_CURRENT_STRUCTURE.getTranslatedKeyMessage());
        this.icon = StructureRecipeCategory.createStructureBlockStack();
    }

    public static void show(String structureId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getToastManager() != null) {
            minecraft.getToastManager().addToast(new CurrentStructureToast(structureId));
        }
    }

    @Override
    public void update(ToastManager manager, long timeSinceLastVisible) {
        this.visibleTimeMs = timeSinceLastVisible;
        this.displayDurationMs = DISPLAY_TIME_MS * manager.getNotificationDisplayTimeMultiplier();
    }

    @Override
    public Visibility getWantedVisibility() {
        return visibleTimeMs >= displayDurationMs ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long timeSinceLastVisible) {
        drawBackground(graphics);
        graphics.fakeItem(icon, 8, 8);
        drawAdaptive(graphics, font, title, TEXT_X, 7, 0xAA00AA);
        drawAdaptive(graphics, font, hint, TEXT_X, 18, 0x222222);
    }

    @Override
    public int width() {
        return WIDTH;
    }

    private static void drawAdaptive(GuiGraphicsExtractor graphics, Font font, Component component, int x, int y, int color) {
        String text = component.getString();
        int width = font.width(text);
        if (width <= TEXT_WIDTH) {
            graphics.text(font, component, x, y, color);
            return;
        }
        graphics.text(font, font.plainSubstrByWidth(text, TEXT_WIDTH - font.width("...")) + "...", x, y, color);
    }

    private static void drawBackground(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, WIDTH, 32, 0xFF202020);
        graphics.fill(2, 2, WIDTH - 2, 30, 0xFFE8E8E8);
        graphics.fill(4, 4, WIDTH - 4, 28, 0xFFD7D7D7);
    }
}
