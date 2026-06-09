package org.hp.jei_structures.jei;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.hp.jei_structures.JeiStructures;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class StructureRecipeCategory implements IRecipeCategory<StructureRecipe> {

    public static final RecipeType<StructureRecipe> TYPE = RecipeType.create(JeiStructures.MODID, "structure_index", StructureRecipe.class);

    private static final int WIDTH = 200;
    private static final int HEIGHT = 200;
    private static final int CONTENT_X = 6;
    private static final int CONTENT_Y = 14;
    private static final int CONTENT_WIDTH = 198;
    private static final int CONTENT_HEIGHT = 188;
    private static final int CONTENT_PADDING_Y = 5;
    private static final int TITLE_CENTER_Y = 6;
    private static final int FIXED_TITLE_Y = 12;
    private static final int SLOT_SPACING = 18;
    private static final int GRID_COLUMNS = 6;
    private static final int SCROLLBAR_EXTRA_WIDTH = 16;
    private static final int CARD_MARGIN = 4;
    private static final int HEADER_DIVIDER_INSET = 12;
    private static final int CARD_CONTENT_INSET_X = 11;
    private static final int SLOT_SIZE = 16;
    private static final int SCROLLBAR_WIDTH = 14;
    private static final int MIN_SCROLL_MARKER_HEIGHT = 14;
    private static final int SCROLL_STEP = 24;

    private final IDrawable background;
    private final IDrawable icon;
    private final Map<StructureRecipe, Integer> scrollOffsets = new IdentityHashMap<>();
    private StructureRecipe draggingScrollbarRecipe;

    public StructureRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(createStructureBlockStack());
    }

    public static ItemStack createStructureBlockStack() {
        return new ItemStack(Items.STRUCTURE_BLOCK);
    }

    @Override
    public RecipeType<StructureRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei_structures.category.structure_index");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, StructureRecipe recipe, IFocusGroup focuses) {
        List<ItemStack> lookupInputs = recipe.getLookupInputs();
        if (!lookupInputs.isEmpty()) {
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStacks(lookupInputs);
        }
        List<ItemStack> lookupOutputs = recipe.getLookupOutputs();
        if (!lookupOutputs.isEmpty()) {
            builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStacks(lookupOutputs);
        }
    }

    @Override
    public void draw(StructureRecipe recipe, IRecipeSlotsView recipeSlotsView, PoseStack poseStack, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        clampScroll(recipe);
        drawPanel(poseStack, CONTENT_X, CONTENT_Y, CONTENT_WIDTH, CONTENT_HEIGHT);
        drawFixedHeader(recipe, poseStack, font, CONTENT_X, CONTENT_Y);
        drawContents(poseStack, font, recipe);
        drawScrollbar(poseStack, recipe);
    }

    @Override
    public boolean handleInput(StructureRecipe recipe, double mouseX, double mouseY, InputConstants.Key input) {
        if (input.getType() != InputConstants.Type.MOUSE || input.getValue() != 0) {
            return false;
        }
        if (isInsideScrollbar(mouseX, mouseY)) {
            return setScrollFromMouseY(recipe, mouseY);
        }
        if (!isInsideViewport(mouseX, mouseY)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new StructureRecipeScreen(recipe, minecraft.screen));
        return true;
    }

    @Override
    public List<Component> getTooltipStrings(StructureRecipe recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        PositionedSlot slot = getSlotUnderMouse(recipe, mouseX, mouseY);
        if (slot == null) {
            return List.of();
        }
        Minecraft minecraft = Minecraft.getInstance();
        List<Component> tooltip = new ArrayList<>();
        if (slot.slot().kind() == StructureRecipe.SlotKind.ITEM && slot.slot().itemStack() != null && !slot.slot().itemStack().isEmpty()) {
            tooltip.addAll(slot.slot().itemStack().getTooltipLines(minecraft.player, TooltipFlag.Default.NORMAL));
        } else if (slot.slot().kind() == StructureRecipe.SlotKind.BIOME && slot.slot().biome() != null) {
            tooltip.addAll(BiomeIngredient.INSTANCE.getTooltip(slot.slot().biome(), TooltipFlag.Default.NORMAL));
        }
        tooltip.addAll(recipe.getSlotTooltips(slot.slot().slotName()));
        return List.copyOf(tooltip);
    }

    public boolean handleMouseScrolled(StructureRecipe recipe, double mouseX, double mouseY, double scrollDeltaY) {
        if (!isInsideViewport(mouseX, mouseY) || getMaxScrollOffset(recipe) <= 0) {
            return false;
        }
        int direction = scrollDeltaY > 0.0D ? -1 : 1;
        scrollOffsets.put(recipe, getScrollOffset(recipe) + direction * SCROLL_STEP);
        clampScroll(recipe);
        return true;
    }

    public boolean handleMouseDragged(StructureRecipe recipe, double mouseX, double mouseY, InputConstants.Key mouseKey, double dragX, double dragY) {
        if (mouseKey.getType() != InputConstants.Type.MOUSE || mouseKey.getValue() != 0 || getMaxScrollOffset(recipe) <= 0) {
            draggingScrollbarRecipe = null;
            return false;
        }
        if (draggingScrollbarRecipe != recipe && !isInsideScrollbar(mouseX, mouseY)) {
            return false;
        }
        draggingScrollbarRecipe = recipe;
        return setScrollFromMouseY(recipe, mouseY);
    }

    static int getTextWrapPixelWidth() {
        return getContentWidthWithoutScrollbar() - getCardContentInsetX() * 2;
    }

    static int getHeaderContentOffset(StructureRecipe recipe) {
        return CONTENT_PADDING_Y;
    }

    static int getPageContentHeight() {
        return CONTENT_HEIGHT;
    }

    static int getCardContentInsetX() {
        return CARD_CONTENT_INSET_X;
    }

    static int getScrollbarWidth() {
        return SCROLLBAR_EXTRA_WIDTH;
    }

    private void drawContents(PoseStack poseStack, Font font, StructureRecipe recipe) {
        int viewportTop = getViewportTop();
        int viewportBottom = getViewportBottom();
        int cardX = CONTENT_X + getCardLeftInset();
        int cardY = CONTENT_Y + CONTENT_PADDING_Y - getScrollOffset(recipe);
        int cardWidth = getCardWidth();
        int cardHeight = recipe.getMergedContentHeight();
        drawInnerCard(poseStack, cardX, cardY, cardWidth, cardHeight);

        int currentY = cardY;
        for (StructureRecipe.ContentBlock block : recipe.getContentBlocks()) {
            int blockEndY = currentY + block.getHeight(recipe);
            if (blockEndY >= viewportTop && currentY <= viewportBottom) {
                drawBlock(poseStack, font, recipe, block, currentY, viewportTop, viewportBottom);
                drawBlockSlots(poseStack, recipe, block, currentY, viewportTop, viewportBottom);
            }
            currentY += block.getHeight(recipe);
        }
    }

    private void drawBlock(PoseStack poseStack, Font font, StructureRecipe recipe, StructureRecipe.ContentBlock block, int currentY, int viewportTop, int viewportBottom) {
        int blockY = currentY + block.getTitleStartY(recipe);
        if (block.hasTitle()) {
            int titleColor = getTitleColor(block);
            int titleLineY = blockY;
            for (Component titleLine : block.getWrappedTitle(recipe)) {
                drawTextIfVisible(poseStack, font, titleLine, CONTENT_X + getCardContentInsetX(), titleLineY, resolveTextColor(titleLine, titleColor), viewportTop, viewportBottom);
                titleLineY += recipe.getTextLineHeight();
            }
            int dividerY = currentY + block.getTitleDividerY(recipe);
            if (dividerY >= viewportTop && dividerY <= viewportBottom) {
                GuiComponent.fill(poseStack, CONTENT_X + getCardContentInsetX(), dividerY, CONTENT_X + getContentRightEdge() - getCardContentInsetX(), dividerY + 1, getTitleDividerColor(block));
            }
        }

        blockY = currentY + block.getTextStartY(recipe);
        int groupIndex = 0;
        for (List<Component> wrappedGroup : block.getWrappedLineGroups(recipe)) {
            for (Component wrappedLine : wrappedGroup) {
                int textColor = getTextColor(block, groupIndex);
                drawTextIfVisible(poseStack, font, wrappedLine, CONTENT_X + getCardContentInsetX(), blockY, resolveTextColor(wrappedLine, textColor), viewportTop, viewportBottom);
                blockY += recipe.getTextLineHeight();
            }
            blockY += block.getExtraLineGapAfter(groupIndex);
            groupIndex++;
        }
    }

    private void drawBlockSlots(PoseStack poseStack, StructureRecipe recipe, StructureRecipe.ContentBlock block, int currentY, int viewportTop, int viewportBottom) {
        int blockY = currentY + block.getItemStartY(recipe);
        int ingredientCount = block.slots().size();
        for (int index = 0; index < ingredientCount; index++) {
            int column = index % GRID_COLUMNS;
            int row = index / GRID_COLUMNS;
            int slotX = CONTENT_X + getCardContentInsetX() + column * SLOT_SPACING;
            int slotY = blockY + row * SLOT_SPACING + 1;
            if (slotY + SLOT_SIZE < viewportTop || slotY > viewportBottom) {
                continue;
            }
            StructureRecipe.SlotDisplay slot = block.slots().get(index);
            drawSlotBackground(poseStack, slotX - 1, slotY - 1);
            drawSlotIngredient(poseStack, slot, slotX, slotY);
        }
    }

    private PositionedSlot getSlotUnderMouse(StructureRecipe recipe, double mouseX, double mouseY) {
        if (!isInsideViewport(mouseX, mouseY)) {
            return null;
        }
        int viewportTop = getViewportTop();
        int viewportBottom = getViewportBottom();
        int currentY = CONTENT_Y + CONTENT_PADDING_Y - getScrollOffset(recipe);
        for (StructureRecipe.ContentBlock block : recipe.getContentBlocks()) {
            int blockY = currentY + block.getItemStartY(recipe);
            int ingredientCount = block.slots().size();
            for (int index = 0; index < ingredientCount; index++) {
                int column = index % GRID_COLUMNS;
                int row = index / GRID_COLUMNS;
                int slotX = CONTENT_X + getCardContentInsetX() + column * SLOT_SPACING;
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

    private void drawScrollbar(PoseStack poseStack, StructureRecipe recipe) {
        int maxOffset = getMaxScrollOffset(recipe);
        int trackX = CONTENT_X + CONTENT_WIDTH - SCROLLBAR_WIDTH;
        int trackTop = getViewportTop();
        int trackBottom = getViewportBottom();
        GuiComponent.fill(poseStack, trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackBottom, 0xFFC9C9C9);
        if (maxOffset <= 0) {
            GuiComponent.fill(poseStack, trackX + 1, trackTop + 1, trackX + SCROLLBAR_WIDTH - 1, trackBottom - 1, 0xFF8F8F8F);
            return;
        }
        int thumbHeight = Math.max(MIN_SCROLL_MARKER_HEIGHT, CONTENT_HEIGHT * CONTENT_HEIGHT / Math.max(recipe.getTotalContentHeight(), 1));
        int thumbTravel = Math.max(1, CONTENT_HEIGHT - 2 - thumbHeight);
        int thumbY = trackTop + getScrollOffset(recipe) * thumbTravel / maxOffset;
        GuiComponent.fill(poseStack, trackX + 1, thumbY + 1, trackX + SCROLLBAR_WIDTH - 1, thumbY + 1 + thumbHeight, 0xFF777777);
    }

    private boolean isInsideViewport(double mouseX, double mouseY) {
        return mouseX >= CONTENT_X && mouseX < CONTENT_X + getContentWidthWithoutScrollbar() && mouseY >= getViewportTop() && mouseY < getViewportBottom();
    }

    private boolean isInsideScrollbar(double mouseX, double mouseY) {
        int trackX = CONTENT_X + CONTENT_WIDTH - SCROLLBAR_WIDTH;
        return mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH && mouseY >= getViewportTop() && mouseY < getViewportBottom();
    }

    private boolean setScrollFromMouseY(StructureRecipe recipe, double mouseY) {
        int maxOffset = getMaxScrollOffset(recipe);
        if (maxOffset <= 0) {
            scrollOffsets.put(recipe, 0);
            return false;
        }
        int thumbHeight = Math.max(MIN_SCROLL_MARKER_HEIGHT, CONTENT_HEIGHT * CONTENT_HEIGHT / Math.max(recipe.getTotalContentHeight(), 1));
        int thumbTravel = Math.max(1, CONTENT_HEIGHT - 2 - thumbHeight);
        double relativeY = mouseY - getViewportTop() - 1 - (double) thumbHeight / 2.0D;
        int offset = (int) Math.round(relativeY * maxOffset / (double) thumbTravel);
        scrollOffsets.put(recipe, offset);
        clampScroll(recipe);
        return true;
    }

    private int getScrollOffset(StructureRecipe recipe) {
        return scrollOffsets.getOrDefault(recipe, 0);
    }

    private int getMaxScrollOffset(StructureRecipe recipe) {
        return Math.max(0, recipe.getTotalContentHeight() - CONTENT_HEIGHT);
    }

    private void clampScroll(StructureRecipe recipe) {
        scrollOffsets.put(recipe, Math.max(0, Math.min(getScrollOffset(recipe), getMaxScrollOffset(recipe))));
    }

    private static int getViewportTop() {
        return CONTENT_Y;
    }

    private static int getViewportBottom() {
        return getViewportTop() + CONTENT_HEIGHT;
    }

    private static int getContentWidthWithoutScrollbar() {
        return CONTENT_WIDTH - getScrollbarWidth();
    }

    private static int getContentRightEdge() {
        return getContentWidthWithoutScrollbar();
    }

    private static int getCardWidth() {
        return getContentWidthWithoutScrollbar() - getCardLeftInset() * 2;
    }

    private static int getCardLeftInset() {
        return CARD_MARGIN;
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

    private static void drawSlotIngredient(PoseStack poseStack, StructureRecipe.SlotDisplay slot, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        if (slot.kind() == StructureRecipe.SlotKind.ITEM && slot.itemStack() != null && !slot.itemStack().isEmpty()) {
            ScreenOffset offset = getScreenOffset(poseStack);
            int screenX = x + offset.x();
            int screenY = y + offset.y();
            minecraft.getItemRenderer().renderAndDecorateItem(slot.itemStack(), screenX, screenY);
            minecraft.getItemRenderer().renderGuiItemDecorations(minecraft.font, slot.itemStack(), screenX, screenY);
        } else if (slot.kind() == StructureRecipe.SlotKind.BIOME && slot.biome() != null) {
            poseStack.pushPose();
            poseStack.translate(x, y, 0);
            BiomeIngredient.INSTANCE.render(poseStack, slot.biome());
            poseStack.popPose();
        }
    }

    private static ScreenOffset getScreenOffset(PoseStack poseStack) {
        FloatBuffer buffer = FloatBuffer.allocate(16);
        poseStack.last().pose().store(buffer);
        return new ScreenOffset(Math.round(buffer.get(3)), Math.round(buffer.get(7)));
    }

    private static void drawTextIfVisible(PoseStack poseStack, Font font, Component text, int x, int y, int color, int viewportTop, int viewportBottom) {
        if (y + font.lineHeight < viewportTop || y > viewportBottom) {
            return;
        }
        font.draw(poseStack, text, x, y, color);
    }

    private static void drawCenteredString(PoseStack poseStack, Font font, Component text, int centerX, int y, int color) {
        int width = font.width(text);
        font.draw(poseStack, text, centerX - width / 2, y + TITLE_CENTER_Y - font.lineHeight / 2, resolveTextColor(text, color));
    }

    private static void drawFixedHeader(StructureRecipe recipe, PoseStack poseStack, Font font, int x, int y) {
        int titleY = y - FIXED_TITLE_Y;
        drawCenteredString(poseStack, font, recipe.getDisplayName(), x + getContentRightEdge() / 2, titleY, 0xFF2A2A2A);
        int dividerY = y - 1;
        GuiComponent.fill(
                poseStack,
                x + getCardContentInsetX() + HEADER_DIVIDER_INSET,
                dividerY,
                x + getContentRightEdge() - getCardContentInsetX() - HEADER_DIVIDER_INSET,
                dividerY + 1,
                0xFFDDDDDD
        );
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

    private record ScreenOffset(int x, int y) {
    }
}
