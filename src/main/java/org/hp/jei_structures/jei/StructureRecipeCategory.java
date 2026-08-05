package org.hp.jei_structures.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.hp.jei_structures.JeiStructures;

import java.util.ArrayList;
import java.util.List;

public final class StructureRecipeCategory implements IRecipeCategory<StructureRecipe> {

    public static final RecipeType<StructureRecipe> TYPE = RecipeType.create(JeiStructures.MODID, "structure_index", StructureRecipe.class);

    private static final int WIDTH = 188;
    private static final int HEIGHT = 170;
    private static final int CONTENT_X = 4;
    private static final int CONTENT_Y = 16;
    private static final int CONTENT_WIDTH = 180;
    private static final int CONTENT_HEIGHT = 150;
    private static final int CONTENT_PADDING_Y = 4;
    private static final int SLOT_SPACING = 18;
    private static final int SCROLLBAR_EXTRA_WIDTH = 16;
    private static final int CONTENT_INSET_X = 4;

    private final IDrawable icon;

    public StructureRecipeCategory(IGuiHelper guiHelper) {
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
    public Identifier getRegistryName(StructureRecipe recipe) {
        return recipe != null ? recipe.getId() : null;
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
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, StructureRecipe recipe, IFocusGroup focuses) {
        for (StructureRecipe.ContentBlock block : recipe.getContentBlocks()) {
            for (StructureRecipe.SlotDisplay slot : block.slots()) {
                var slotBuilder = builder.addSlot(slot.role(), 0, 0)
                        .setStandardSlotBackground()
                        .setSlotName(slot.slotName())
                        .addRichTooltipCallback((IRecipeSlotView recipeSlotView, ITooltipBuilder tooltip) -> {
                            for (Component line : recipe.getSlotTooltips(slot.slotName())) {
                                tooltip.add(line);
                            }
                        });
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
    public void createRecipeExtras(IRecipeExtrasBuilder builder, StructureRecipe recipe, IFocusGroup focuses) {
        List<mezz.jei.api.gui.ingredient.IRecipeSlotDrawable> slots = builder.getRecipeSlots().getSlots();
        StructureScrollWidget widget = new StructureScrollWidget(recipe, CONTENT_X, CONTENT_Y, CONTENT_WIDTH, CONTENT_HEIGHT, slots);
        builder.addSlottedWidget(widget, slots);
        builder.addInputHandler(widget);
    }

    @Override
    public void draw(StructureRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor guiGraphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        guiGraphics.text(font, recipe.getDisplayName(), CONTENT_X, 4, 0xFF2B2B2B, false);
        drawPanel(guiGraphics, CONTENT_X, CONTENT_Y, CONTENT_WIDTH, CONTENT_HEIGHT);
    }

    static void drawScrollableContents(StructureRecipe recipe, GuiGraphicsExtractor guiGraphics, int x, int y) {
        Font font = Minecraft.getInstance().font;
        int currentY = y + CONTENT_PADDING_Y;
        List<StructureRecipe.ContentBlock> blocks = recipe.getContentBlocks();
        for (StructureRecipe.ContentBlock block : blocks) {
            int blockY = currentY + block.getTitleStartY(recipe);
            if (block.hasTitle()) {
                int titleColor = getTitleColor(block);
                int titleLineY = blockY;
                for (Component titleLine : block.getWrappedTitle(recipe)) {
                    guiGraphics.text(font, titleLine, x + CONTENT_INSET_X, titleLineY, resolveTextColor(titleLine, titleColor), false);
                    titleLineY += recipe.getTextLineHeight();
                }
            }
            blockY = currentY + block.getTextStartY(recipe);
            int groupIndex = 0;
            for (List<Component> wrappedGroup : block.getWrappedLineGroups(recipe)) {
                for (Component wrappedLine : wrappedGroup) {
                    int textColor = getTextColor(block, groupIndex);
                    guiGraphics.text(font, wrappedLine, x + CONTENT_INSET_X, blockY, resolveTextColor(wrappedLine, textColor), false);
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
                    int column = index % recipe.getGridColumns();
                    int row = index / recipe.getGridColumns();
                    int slotX = CONTENT_INSET_X + column * SLOT_SPACING;
                    int slotY = blockY + row * SLOT_SPACING + 1;
                    placements.add(new SlotPlacement(slotX, slotY));
                }
            }
            currentY += block.getHeight(recipe);
        }
        return placements;
    }

    static int getTextWrapPixelWidth() {
        return CONTENT_WIDTH - SCROLLBAR_EXTRA_WIDTH - CONTENT_INSET_X * 2;
    }

    static int getHeaderContentOffset(StructureRecipe recipe) {
        return CONTENT_PADDING_Y;
    }

    static int getScrollbarWidth() {
        return SCROLLBAR_EXTRA_WIDTH;
    }

    static int getContentX() {
        return CONTENT_X;
    }

    static int getContentY() {
        return CONTENT_Y;
    }

    static int getContentWidth() {
        return CONTENT_WIDTH;
    }

    static int getContentHeight() {
        return CONTENT_HEIGHT;
    }

    static void drawPanel(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, 0xFFE3E3E3);
        guiGraphics.fill(x, y, x + width, y + 1, 0xFFF8F8F8);
        guiGraphics.fill(x, y, x + 1, y + height, 0xFFF8F8F8);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFF8A8A8A);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFF8A8A8A);
    }

    private static int getTitleColor(StructureRecipe.ContentBlock block) {
        return switch (block.blockType()) {
            case SUMMARY -> 0xFF636363;
            case LEAD_DETAIL -> 0xFF151515;
            case TAIL -> 0xFF525252;
            case DEFAULT -> 0xFF5F5F5F;
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

    record SlotPlacement(int x, int y) {
    }
}
