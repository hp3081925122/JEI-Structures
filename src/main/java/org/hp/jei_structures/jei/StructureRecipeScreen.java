package org.hp.jei_structures.jei;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

public final class StructureRecipeScreen extends Screen {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 210;
    private static final int CONTENT_PADDING_Y = 8;
    private static final int CARD_MARGIN = 6;
    private static final int CARD_CONTENT_INSET_X = 12;
    private static final int SLOT_SPACING = 18;
    private static final int SLOT_SIZE = 16;
    private static final int GRID_COLUMNS = 10;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final int SCROLL_STEP = 24;
    private static final int VIEWPORT_HEIGHT = PANEL_HEIGHT - 34;

    private final StructureRecipe recipe;
    private final Screen parent;
    private int scrollOffset;

    public StructureRecipeScreen(StructureRecipe recipe, Screen parent) {
        super(recipe.getDisplayName());
        this.recipe = recipe;
        this.parent = parent;
    }

    public static int getTextWrapPixelWidth() {
        return PANEL_WIDTH - 34 - CARD_CONTENT_INSET_X * 2;
    }

    public static int getContentPaddingY() {
        return CONTENT_PADDING_Y;
    }

    public static int getViewportHeight() {
        return VIEWPORT_HEIGHT;
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        clampScroll();
        drawPanel(poseStack, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
        drawHeader(poseStack, panelX, panelY);
        drawContents(poseStack, panelX, panelY, mouseX, mouseY);
        drawScrollbar(poseStack, panelX, panelY);
        drawHoverTooltip(poseStack, panelX, panelY, mouseX, mouseY);
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isInsideViewport(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int direction = delta > 0.0D ? -1 : 1;
        scrollOffset += direction * SCROLL_STEP;
        clampScroll();
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawHeader(PoseStack poseStack, int panelX, int panelY) {
        Font font = Minecraft.getInstance().font;
        Component closeHint = Component.translatable("jei_structures.screen.close_hint");
        font.draw(poseStack, recipe.getDisplayName(), panelX + 12, panelY + 10, 0xFF222222);
        font.draw(poseStack, closeHint, panelX + PANEL_WIDTH - 12 - font.width(closeHint), panelY + 10, 0xFF777777);
        GuiComponent.fill(poseStack, panelX + 10, panelY + 26, panelX + PANEL_WIDTH - 10, panelY + 27, 0xFFD4D4D4);
    }

    private void drawContents(PoseStack poseStack, int panelX, int panelY, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int viewportTop = getViewportTop(panelY);
        int viewportBottom = getViewportBottom(panelY);
        int cardX = panelX + CARD_MARGIN;
        int cardY = viewportTop;
        int cardWidth = PANEL_WIDTH - CARD_MARGIN * 2 - 12;
        drawInnerCard(poseStack, cardX, cardY, cardWidth, VIEWPORT_HEIGHT);

        int currentY = viewportTop + CONTENT_PADDING_Y - scrollOffset;
        for (StructureRecipe.ContentBlock block : recipe.getContentBlocks()) {
            int blockEndY = currentY + block.getHeight(recipe);
            if (blockEndY >= viewportTop && currentY <= viewportBottom) {
                drawBlock(poseStack, font, block, panelX, currentY, viewportTop, viewportBottom);
                drawBlockSlots(poseStack, block, panelX, currentY, viewportTop, viewportBottom);
            }
            currentY += block.getHeight(recipe);
        }
    }

    private void drawBlock(PoseStack poseStack, Font font, StructureRecipe.ContentBlock block, int panelX, int currentY, int viewportTop, int viewportBottom) {
        int blockY = currentY + block.getTitleStartY(recipe);
        if (block.hasTitle()) {
            int titleColor = getTitleColor(block);
            int titleLineY = blockY;
            for (Component titleLine : block.getWrappedTitle(recipe)) {
                drawTextIfVisible(poseStack, font, titleLine, panelX + CARD_CONTENT_INSET_X, titleLineY, resolveTextColor(titleLine, titleColor), viewportTop, viewportBottom);
                titleLineY += recipe.getTextLineHeight();
            }
            int dividerY = currentY + block.getTitleDividerY(recipe);
            if (dividerY >= viewportTop && dividerY <= viewportBottom) {
                GuiComponent.fill(poseStack, panelX + CARD_CONTENT_INSET_X, dividerY, panelX + PANEL_WIDTH - 28, dividerY + 1, getTitleDividerColor(block));
            }
        }

        blockY = currentY + block.getTextStartY(recipe);
        int groupIndex = 0;
        for (List<Component> wrappedGroup : block.getWrappedLineGroups(recipe)) {
            for (Component wrappedLine : wrappedGroup) {
                int textColor = getTextColor(block, groupIndex);
                drawTextIfVisible(poseStack, font, wrappedLine, panelX + CARD_CONTENT_INSET_X, blockY, resolveTextColor(wrappedLine, textColor), viewportTop, viewportBottom);
                blockY += recipe.getTextLineHeight();
            }
            blockY += block.getExtraLineGapAfter(groupIndex);
            groupIndex++;
        }
    }

    private void drawBlockSlots(PoseStack poseStack, StructureRecipe.ContentBlock block, int panelX, int currentY, int viewportTop, int viewportBottom) {
        int blockY = currentY + block.getItemStartY(recipe);
        int ingredientCount = block.slots().size();
        for (int index = 0; index < ingredientCount; index++) {
            int column = index % GRID_COLUMNS;
            int row = index / GRID_COLUMNS;
            int slotX = panelX + CARD_CONTENT_INSET_X + column * SLOT_SPACING;
            int slotY = blockY + row * SLOT_SPACING + 1;
            if (slotY + SLOT_SIZE < viewportTop || slotY > viewportBottom) {
                continue;
            }
            StructureRecipe.SlotDisplay slot = block.slots().get(index);
            drawSlotBackground(poseStack, slotX - 1, slotY - 1);
            drawSlotIngredient(poseStack, slot, slotX, slotY);
        }
    }

    private void drawHoverTooltip(PoseStack poseStack, int panelX, int panelY, int mouseX, int mouseY) {
        PositionedSlot slot = getSlotUnderMouse(panelX, panelY, mouseX, mouseY);
        if (slot == null) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        if (slot.slot().kind() == StructureRecipe.SlotKind.ITEM && slot.slot().itemStack() != null && !slot.slot().itemStack().isEmpty()) {
            tooltip.addAll(slot.slot().itemStack().getTooltipLines(minecraft.player, TooltipFlag.Default.NORMAL));
        } else if (slot.slot().kind() == StructureRecipe.SlotKind.BIOME && slot.slot().biome() != null) {
            tooltip.addAll(BiomeIngredient.INSTANCE.getTooltip(slot.slot().biome(), TooltipFlag.Default.NORMAL));
        }
        tooltip.addAll(recipe.getSlotTooltips(slot.slot().slotName()));
        renderComponentTooltip(poseStack, tooltip, mouseX, mouseY);
    }

    private PositionedSlot getSlotUnderMouse(int panelX, int panelY, int mouseX, int mouseY) {
        if (!isInsideViewport(mouseX, mouseY)) {
            return null;
        }
        int viewportTop = getViewportTop(panelY);
        int viewportBottom = getViewportBottom(panelY);
        int currentY = viewportTop + CONTENT_PADDING_Y - scrollOffset;
        for (StructureRecipe.ContentBlock block : recipe.getContentBlocks()) {
            int blockY = currentY + block.getItemStartY(recipe);
            int ingredientCount = block.slots().size();
            for (int index = 0; index < ingredientCount; index++) {
                int column = index % GRID_COLUMNS;
                int row = index / GRID_COLUMNS;
                int slotX = panelX + CARD_CONTENT_INSET_X + column * SLOT_SPACING;
                int slotY = blockY + row * SLOT_SPACING + 1;
                if (slotY + SLOT_SIZE < viewportTop || slotY > viewportBottom) {
                    continue;
                }
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    return new PositionedSlot(block.slots().get(index), slotX, slotY);
                }
            }
            currentY += block.getHeight(recipe);
        }
        return null;
    }

    private void drawScrollbar(PoseStack poseStack, int panelX, int panelY) {
        int maxOffset = getMaxScrollOffset();
        int trackX = panelX + PANEL_WIDTH - 14;
        int trackTop = getViewportTop(panelY);
        int trackBottom = getViewportBottom(panelY);
        GuiComponent.fill(poseStack, trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackBottom, 0xFFC9C9C9);
        if (maxOffset <= 0) {
            GuiComponent.fill(poseStack, trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackBottom, 0xFF8F8F8F);
            return;
        }
        int thumbHeight = Math.max(18, VIEWPORT_HEIGHT * VIEWPORT_HEIGHT / Math.max(recipe.getMergedContentHeight(), 1));
        int thumbTravel = Math.max(1, VIEWPORT_HEIGHT - thumbHeight);
        int thumbY = trackTop + scrollOffset * thumbTravel / maxOffset;
        GuiComponent.fill(poseStack, trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF777777);
    }

    private boolean isInsideViewport(double mouseX, double mouseY) {
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        return mouseX >= panelX + CARD_MARGIN && mouseX < panelX + PANEL_WIDTH - 18 && mouseY >= getViewportTop(panelY) && mouseY < getViewportBottom(panelY);
    }

    private int getMaxScrollOffset() {
        return Math.max(0, recipe.getMergedContentHeight() - VIEWPORT_HEIGHT);
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, getMaxScrollOffset()));
    }

    private static int getViewportTop(int panelY) {
        return panelY + 30;
    }

    private static int getViewportBottom(int panelY) {
        return getViewportTop(panelY) + VIEWPORT_HEIGHT;
    }

    private static void drawSlotIngredient(PoseStack poseStack, StructureRecipe.SlotDisplay slot, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        if (slot.kind() == StructureRecipe.SlotKind.ITEM && slot.itemStack() != null && !slot.itemStack().isEmpty()) {
            minecraft.getItemRenderer().renderAndDecorateItem(slot.itemStack(), x, y);
            minecraft.getItemRenderer().renderGuiItemDecorations(minecraft.font, slot.itemStack(), x, y);
        } else if (slot.kind() == StructureRecipe.SlotKind.BIOME && slot.biome() != null) {
            poseStack.pushPose();
            poseStack.translate(x, y, 0);
            BiomeIngredient.INSTANCE.render(poseStack, slot.biome());
            poseStack.popPose();
        }
    }

    private static void drawPanel(PoseStack poseStack, int x, int y, int width, int height) {
        GuiComponent.fill(poseStack, x, y, x + width, y + height, 0xFFE3E3E3);
        GuiComponent.fill(poseStack, x, y, x + width, y + 1, 0xFFF8F8F8);
        GuiComponent.fill(poseStack, x, y, x + 1, y + height, 0xFFF8F8F8);
        GuiComponent.fill(poseStack, x + width - 1, y, x + width, y + height, 0xFF8A8A8A);
        GuiComponent.fill(poseStack, x, y + height - 1, x + width, y + height, 0xFF8A8A8A);
    }

    private static void drawInnerCard(PoseStack poseStack, int x, int y, int width, int height) {
        GuiComponent.fill(poseStack, x, y, x + width, y + height, 0xFFFBFBFB);
        GuiComponent.fill(poseStack, x, y, x + width, y + 1, 0xFFFFFFFF);
        GuiComponent.fill(poseStack, x, y, x + 1, y + height, 0xFFFFFFFF);
        GuiComponent.fill(poseStack, x + width - 1, y, x + width, y + height, 0xFFD5D5D5);
        GuiComponent.fill(poseStack, x, y + height - 1, x + width, y + height, 0xFFD5D5D5);
    }

    private static void drawSlotBackground(PoseStack poseStack, int x, int y) {
        GuiComponent.fill(poseStack, x, y, x + 18, y + 18, 0xFF8B8B8B);
        GuiComponent.fill(poseStack, x + 1, y + 1, x + 17, y + 17, 0xFFE6E6E6);
        GuiComponent.fill(poseStack, x + 2, y + 2, x + 16, y + 16, 0xFFBDBDBD);
    }

    private static void drawTextIfVisible(PoseStack poseStack, Font font, Component text, int x, int y, int color, int viewportTop, int viewportBottom) {
        if (y + font.lineHeight < viewportTop || y > viewportBottom) {
            return;
        }
        font.draw(poseStack, text, x, y, color);
    }

    private static int getTitleColor(StructureRecipe.ContentBlock block) {
        return switch (block.blockType()) {
            case SUMMARY -> 0xFF636363;
            case LEAD_DETAIL -> 0xFF151515;
            case TAIL -> 0xFF525252;
            case DEFAULT -> 0xFF5F5F5F;
        };
    }

    private static int getTitleDividerColor(StructureRecipe.ContentBlock block) {
        if (block.isMergedItemOnlyBlock()) {
            return 0xFF686868;
        }
        return switch (block.blockType()) {
            case SUMMARY -> 0xFFD8D8D8;
            case LEAD_DETAIL -> 0xFF686868;
            case TAIL -> 0xFFD7D7D7;
            case DEFAULT -> 0xFFECECEC;
        };
    }

    private static int getTextColor(StructureRecipe.ContentBlock block, int groupIndex) {
        return switch (block.blockType()) {
            case SUMMARY -> groupIndex < 2 ? 0xFF717171 : 0xFF929292;
            case LEAD_DETAIL -> groupIndex == 0 ? 0xFF0B0B0B : 0xFF131313;
            case TAIL -> 0xFF777777;
            case DEFAULT -> 0xFF646464;
        };
    }

    private static int resolveTextColor(Component text, int fallbackColor) {
        Integer explicitColor = text.getStyle().getColor() != null ? text.getStyle().getColor().getValue() : null;
        if (explicitColor != null) {
            return 0xFF000000 | explicitColor;
        }
        return fallbackColor;
    }

    private record PositionedSlot(StructureRecipe.SlotDisplay slot, int x, int y) {
    }
}
