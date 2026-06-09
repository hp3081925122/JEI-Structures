package org.hp.jei_structures.jei;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class StructureScrollWidget implements ISlottedRecipeWidget, IJeiInputHandler {
    private static final int SCROLLBAR_EXTRA_WIDTH = 16;
    private static final int SCROLLBAR_WIDTH = 14;
    private static final int MIN_SCROLL_MARKER_HEIGHT = 14;

    private final StructureRecipe recipe;
    private final List<IRecipeSlotDrawable> contentSlots;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final ImmutableRect2i area;
    private final ImmutableRect2i contentsArea;
    private final ImmutableRect2i scrollbarArea;
    private final DrawableNineSliceTexture scrollbarMarker;
    private final DrawableNineSliceTexture scrollbarBackground;
    private final int contentHeight;
    private final List<StructureRecipeCategory.SlotPlacement> slotPlacements;
    private double dragOriginY = -1.0D;
    private float scrollOffsetY = 0.0F;

    public StructureScrollWidget(StructureRecipe recipe, int x, int y, int width, int height, List<IRecipeSlotDrawable> contentSlots) {
        this.recipe = recipe;
        this.contentSlots = new ArrayList<>(contentSlots);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.area = new ImmutableRect2i(x, y, width, height);
        this.contentsArea = new ImmutableRect2i(0, 0, width - SCROLLBAR_EXTRA_WIDTH, height);
        this.scrollbarArea = new ImmutableRect2i(width - SCROLLBAR_WIDTH, 0, SCROLLBAR_WIDTH, height);
        Textures textures = Internal.getTextures();
        this.scrollbarMarker = textures.getScrollbarMarker();
        this.scrollbarBackground = textures.getScrollbarBackground();
        this.contentHeight = recipe.getTotalContentHeight();
        this.slotPlacements = StructureRecipeCategory.getSlotPlacements(recipe);
    }

    @Override
    public ScreenPosition getPosition() {
        return area.getScreenPosition();
    }

    @Override
    public ScreenRectangle getArea() {
        return area.toScreenRectangle();
    }

    @Override
    public void drawWidget(GuiGraphics guiGraphics, double mouseX, double mouseY) {
        scrollbarBackground.draw(guiGraphics, scrollbarArea);
        scrollbarMarker.draw(guiGraphics, getScrollbarMarkerArea());

        PoseStack poseStack = guiGraphics.pose();
        ScreenRectangle scissorArea = MathUtil.transform(contentsArea, poseStack.last().pose());
        guiGraphics.enableScissor(scissorArea.left(), scissorArea.top(), scissorArea.right(), scissorArea.bottom());
        poseStack.pushPose();
        int scrollPixels = getScrollPixels();
        poseStack.translate(0.0D, -scrollPixels, 0.0D);
        StructureRecipeCategory.drawScrollableContents(recipe, guiGraphics, 0, 0);
        drawSlots(guiGraphics);
        poseStack.popPose();
        guiGraphics.disableScissor();
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        if (!isMouseOverContent(mouseX, mouseY)) {
            return;
        }
        double adjustedMouseY = mouseY + getScrollPixels();
        for (IRecipeSlotDrawable slot : contentSlots) {
            if (slot.isMouseOver(mouseX, adjustedMouseY)) {
                slot.getTooltip(tooltip);
                return;
            }
        }
    }

    @Override
    public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
        if (!isMouseOverContent(mouseX, mouseY)) {
            return Optional.empty();
        }
        double adjustedMouseY = mouseY + getScrollPixels();
        for (IRecipeSlotDrawable slot : contentSlots) {
            if (slot.isMouseOver(mouseX, adjustedMouseY)) {
                return Optional.of(new RecipeSlotUnderMouse(slot, x, y - getScrollPixels()));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean handleInput(double mouseX, double mouseY, IJeiUserInput userInput) {
        if (!userInput.is(Internal.getKeyMappings().getLeftClick())) {
            return false;
        }
        if (!userInput.isSimulate()) {
            dragOriginY = -1.0D;
        }
        if (!scrollbarArea.contains(mouseX, mouseY) || getHiddenAmount() == 0) {
            return false;
        }
        if (userInput.isSimulate()) {
            ImmutableRect2i markerArea = getScrollbarMarkerArea();
            if (!markerArea.contains(mouseX, mouseY)) {
                moveScrollbarCenterTo(markerArea, mouseY);
                markerArea = getScrollbarMarkerArea();
            }
            dragOriginY = mouseY - markerArea.y();
        }
        return true;
    }

    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        if (getHiddenAmount() > 0) {
            float scrollAmount = (float) (scrollDeltaY * 18.0D / Math.max(contentHeight, 1));
            scrollOffsetY = Mth.clamp(scrollOffsetY - scrollAmount, 0.0F, 1.0F);
            return true;
        }
        scrollOffsetY = 0.0F;
        return true;
    }

    @Override
    public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key mouseKey, double dragX, double dragY) {
        if (dragOriginY < 0.0D || mouseKey.getValue() != 0) {
            return false;
        }
        ImmutableRect2i markerArea = getScrollbarMarkerArea();
        double topY = mouseY - dragOriginY;
        moveScrollbarTo(markerArea, topY);
        return true;
    }

    private void drawSlots(GuiGraphics guiGraphics) {
        int slotCount = Math.min(contentSlots.size(), slotPlacements.size());
        for (int index = 0; index < slotCount; index++) {
            IRecipeSlotDrawable slot = contentSlots.get(index);
            StructureRecipeCategory.SlotPlacement placement = slotPlacements.get(index);
            slot.setPosition(placement.x(), placement.y());
            slot.draw(guiGraphics);
        }
    }

    private boolean isMouseOverWidget(double mouseX, double mouseY) {
        return mouseX >= 0 && mouseX <= width && mouseY >= 0 && mouseY <= height;
    }

    private boolean isMouseOverContent(double mouseX, double mouseY) {
        return contentsArea.contains(mouseX, mouseY);
    }

    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        return scrollbarArea.contains(mouseX, mouseY);
    }

    private int getVisibleAmount() {
        return contentsArea.height();
    }

    private int getHiddenAmount() {
        return Math.max(contentHeight - height, 0);
    }

    private int getScrollPixels() {
        return Math.round((float) getHiddenAmount() * scrollOffsetY);
    }

    private int getScrollbarMarkerHeight() {
        int totalSpace = scrollbarArea.height() - 2;
        int markerHeight = Math.round((float) totalSpace * ((float) getVisibleAmount() / (float) (getVisibleAmount() + getHiddenAmount())));
        return Math.max(markerHeight, MIN_SCROLL_MARKER_HEIGHT);
    }

    private int getScrollbarMarkerY() {
        int markerHeight = getScrollbarMarkerHeight();
        int totalSpace = scrollbarArea.height() - 2 - markerHeight;
        return scrollbarArea.getY() + 1 + Math.round(totalSpace * scrollOffsetY);
    }

    private ImmutableRect2i getScrollbarMarkerArea() {
        return new ImmutableRect2i(scrollbarArea.getX() + 1, getScrollbarMarkerY(), scrollbarArea.width() - 2, getScrollbarMarkerHeight());
    }

    private void moveScrollbarCenterTo(ImmutableRect2i markerArea, double centerY) {
        double topY = centerY - (double) markerArea.height() / 2.0D;
        moveScrollbarTo(markerArea, topY);
    }

    private void moveScrollbarTo(ImmutableRect2i markerArea, double topY) {
        int minY = scrollbarArea.y();
        int maxY = scrollbarArea.y() + scrollbarArea.height() - markerArea.height();
        double relativeY = topY - (double) minY;
        int totalSpace = maxY - minY;
        if (totalSpace <= 0) {
            scrollOffsetY = 0.0F;
            return;
        }
        scrollOffsetY = Mth.clamp((float) (relativeY / (double) totalSpace), 0.0F, 1.0F);
    }

    private int getScrollbarWidth() {
        return SCROLLBAR_EXTRA_WIDTH;
    }
}
