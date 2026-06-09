package org.hp.jei_structures.jei;

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
import org.hp.jei_structures.JeiStructures;

import java.util.ArrayList;
import java.util.List;

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
    private static final int HEADER_CONTENT_GAP = 6;
    private static final int CARD_CONTENT_INSET_X = 11;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slotBackground;

    public StructureRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(createStructureBlockStack());
        this.slotBackground = guiHelper.getSlotDrawable();
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
        List<SlotPlacement> placements = getSlotPlacements(recipe);
        int placementIndex = 0;
        for (StructureRecipe.ContentBlock block : recipe.getContentBlocks()) {
            for (StructureRecipe.SlotDisplay slot : block.slots()) {
                SlotPlacement placement = placementIndex < placements.size() ? placements.get(placementIndex) : new SlotPlacement(getCardContentInsetX(), CONTENT_PADDING_Y);
                placementIndex++;
                var slotBuilder = builder.addSlot(slot.role(), CONTENT_X + placement.x(), CONTENT_Y + placement.y())
                        .setBackground(slotBackground, -1, -1)
                        .setSlotName(slot.slotName())
                        .addTooltipCallback((recipeSlotView, tooltip) -> tooltip.addAll(recipe.getSlotTooltips(slot.slotName())));
                if (slot.kind() == StructureRecipe.SlotKind.ITEM) {
                    slotBuilder.addItemStack(slot.itemStack());
                } else if (slot.kind() == StructureRecipe.SlotKind.BIOME && slot.biome() != null) {
                    slotBuilder.addIngredient(BiomeIngredient.INSTANCE, slot.biome())
                            .setCustomRenderer(BiomeIngredient.INSTANCE, BiomeIngredient.INSTANCE);
                }
            }
        }

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
        drawPanel(poseStack, CONTENT_X, CONTENT_Y, CONTENT_WIDTH, CONTENT_HEIGHT);
        drawFixedHeader(recipe, poseStack, CONTENT_X, CONTENT_Y);
        drawScrollableContents(recipe, poseStack, CONTENT_X, CONTENT_Y);
    }

    static void drawScrollableContents(StructureRecipe recipe, PoseStack poseStack, int x, int y) {
        Font font = Minecraft.getInstance().font;
        int currentY = y + CONTENT_PADDING_Y;
        int mergedCardX = x + getCardLeftInset();
        int mergedCardY = currentY;
        int mergedCardWidth = getCardWidth();
        int mergedCardHeight = recipe.getMergedContentHeight();
        drawInnerCard(poseStack, mergedCardX, mergedCardY, mergedCardWidth, mergedCardHeight);

        List<StructureRecipe.ContentBlock> blocks = recipe.getContentBlocks();
        currentY = mergedCardY;
        for (int index = 0; index < blocks.size(); index++) {
            StructureRecipe.ContentBlock block = blocks.get(index);
            int blockHeight = block.getHeight(recipe) - block.getTrailingSpacing(recipe);
            int blockY = currentY + block.getTitleStartY(recipe);
            if (block.hasTitle()) {
                int titleColor = getTitleColor(block);
                int titleLineY = blockY;
                for (Component titleLine : block.getWrappedTitle(recipe)) {
                    GuiComponent.drawString(poseStack, font, titleLine, x + getCardContentInsetX(), titleLineY, resolveTextColor(titleLine, titleColor));
                    titleLineY += recipe.getTextLineHeight();
                }
                int titleLineStartX = x + getCardContentInsetX();
                int titleLineEndX = x + getContentRightEdge() - getCardContentInsetX();
                int lineColor = getTitleDividerColor(block);
                int dividerY = currentY + block.getTitleDividerY(recipe);
                GuiComponent.fill(poseStack, titleLineStartX, dividerY, titleLineEndX, dividerY + 1, lineColor);
            }
            blockY = currentY + block.getTextStartY(recipe);
            int groupIndex = 0;
            for (List<Component> wrappedGroup : block.getWrappedLineGroups(recipe)) {
                for (Component wrappedLine : wrappedGroup) {
                    int textColor = getTextColor(block, groupIndex);
                    GuiComponent.drawString(poseStack, font, wrappedLine, x + getCardContentInsetX(), blockY, resolveTextColor(wrappedLine, textColor));
                    blockY += recipe.getTextLineHeight();
                }
                blockY += block.getExtraLineGapAfter(groupIndex);
                groupIndex++;
            }
            currentY += block.getHeight(recipe);
        }

    }

    static List<SlotPlacement> getSlotPlacements(StructureRecipe recipe) {
        List<SlotPlacement> placements = new ArrayList<>();
        int currentY = getHeaderContentOffset(recipe);
        for (StructureRecipe.ContentBlock block : recipe.getContentBlocks()) {
            int blockY = currentY + block.getItemStartY(recipe);
            int ingredientCount = block.slots().size();
            if (ingredientCount > 0) {
                for (int index = 0; index < ingredientCount; index++) {
                    int column = index % GRID_COLUMNS;
                    int row = index / GRID_COLUMNS;
                    int slotX = getCardContentInsetX() + column * SLOT_SPACING;
                    int slotY = blockY + row * SLOT_SPACING + 1;
                    placements.add(new SlotPlacement(slotX, slotY));
                }
            }
            currentY += block.getHeight(recipe);
        }
        return placements;
    }

    static int getTextWrapPixelWidth() {
        return getContentWidthWithoutScrollbar() - getCardContentInsetX() * 2;
    }

    static int getHeaderContentOffset(StructureRecipe recipe) {
        return CONTENT_PADDING_Y;
    }

    static int getCardContentInsetX() {
        return CARD_CONTENT_INSET_X;
    }

    static int getScrollbarWidth() {
        return SCROLLBAR_EXTRA_WIDTH;
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

    static void drawPanel(PoseStack poseStack, int x, int y, int width, int height) {
        GuiComponent.fill(poseStack, x, y, x + width, y + height, 0xFFE3E3E3);
        GuiComponent.fill(poseStack, x, y, x + width, y + 1, 0xFFF8F8F8);
        GuiComponent.fill(poseStack, x, y, x + 1, y + height, 0xFFF8F8F8);
        GuiComponent.fill(poseStack, x + width - 1, y, x + width, y + height, 0xFF8A8A8A);
        GuiComponent.fill(poseStack, x, y + height - 1, x + width, y + height, 0xFF8A8A8A);
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

    private static void drawInnerCard(PoseStack poseStack, int x, int y, int width, int height) {
        GuiComponent.fill(poseStack, x, y, x + width, y + height, 0xFFFBFBFB);
        GuiComponent.fill(poseStack, x, y, x + width, y + 1, 0xFFFFFFFF);
        GuiComponent.fill(poseStack, x, y, x + 1, y + height, 0xFFFFFFFF);
        GuiComponent.fill(poseStack, x + width - 1, y, x + width, y + height, 0xFFD5D5D5);
        GuiComponent.fill(poseStack, x, y + height - 1, x + width, y + height, 0xFFD5D5D5);
    }

    private static void drawCenteredString(PoseStack poseStack, Font font, Component text, int centerX, int y, int color) {
        int width = font.width(text);
        GuiComponent.drawString(poseStack, font, text, centerX - width / 2, y + TITLE_CENTER_Y - font.lineHeight / 2, resolveTextColor(text, color));
    }

    private static void drawFixedHeader(StructureRecipe recipe, PoseStack poseStack, int x, int y) {
        Font font = Minecraft.getInstance().font;
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

    private static int resolveTextColor(Component text, int fallbackColor) {
        Integer explicitColor = text.getStyle().getColor() != null ? text.getStyle().getColor().getValue() : null;
        if (explicitColor != null) {
            return 0xFF000000 | explicitColor;
        }
        return fallbackColor;
    }

    record SlotPlacement(int x, int y) {
    }
}
