package org.hp.jei_structures.jei;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.ScalableDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.Mth;
import org.hp.jei_structures.JeiStructures;
import org.joml.Matrix3x2fStack;

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
    private final ScreenRectangle area;
    private final ScreenRectangle contentsArea;
    private final ScreenRectangle scrollbarArea;
    private final int contentHeight;
    private final List<StructureRecipeCategory.SlotPlacement> slotPlacements;
    private final ScalableDrawable scrollbarBackground;
    private final ScalableDrawable scrollbarMarker;
    private double dragOriginY = -1.0D;
    private float scrollOffsetY = 0.0F;

    public StructureScrollWidget(StructureRecipe recipe, int x, int y, int width, int height, List<IRecipeSlotDrawable> contentSlots) {
        this.recipe = recipe;
        this.contentSlots = new ArrayList<>(contentSlots);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.area = new ScreenRectangle(x, y, width, height);
        this.contentsArea = new ScreenRectangle(0, 0, width - SCROLLBAR_EXTRA_WIDTH, height);
        this.scrollbarArea = new ScreenRectangle(width - SCROLLBAR_WIDTH, 0, SCROLLBAR_WIDTH, height);
        this.contentHeight = recipe.getTotalContentHeight();
        this.slotPlacements = StructureRecipeCategory.getSlotPlacements(recipe);
        this.scrollbarBackground = Internal.getTextures().getScrollbarBackground();
        this.scrollbarMarker = Internal.getTextures().getScrollbarMarker();
        JeiStructures.LOGGER.debug(
                "Registered structure recipe scroll widget: structure={}, slots={}, placements={}, area={}x{}@{},{}; contents={}x{}@{},{}; scrollbar={}x{}@{},{}; contentHeight={}",
                recipe.getStructureId(),
                this.contentSlots.size(),
                this.slotPlacements.size(),
                this.area.width(),
                this.area.height(),
                this.area.left(),
                this.area.top(),
                this.contentsArea.width(),
                this.contentsArea.height(),
                this.contentsArea.left(),
                this.contentsArea.top(),
                this.scrollbarArea.width(),
                this.scrollbarArea.height(),
                this.scrollbarArea.left(),
                this.scrollbarArea.top(),
                this.contentHeight
        );
    }

    @Override
    public ScreenPosition getPosition() {
        return new ScreenPosition(x, y);
    }

    @Override
    public ScreenRectangle getArea() {
        return area;
    }

    @Override
    public void drawWidget(GuiGraphicsExtractor guiGraphics, double mouseX, double mouseY) {
        scrollbarBackground.draw(
                guiGraphics,
                scrollbarArea.left(),
                scrollbarArea.top(),
                scrollbarArea.width(),
                scrollbarArea.height()
        );
        if (getHiddenAmount() > 0) {
            ScreenRectangle markerArea = getScrollbarMarkerArea();
            scrollbarMarker.draw(
                    guiGraphics,
                    markerArea.left(),
                    markerArea.top(),
                    markerArea.width(),
                    markerArea.height()
            );
        }

        Matrix3x2fStack poseStack = guiGraphics.pose();
        int scrollPixels = getScrollPixels();
        guiGraphics.enableScissor(
                contentsArea.left() + 1,
                contentsArea.top() + 1,
                contentsArea.right(),
                contentsArea.bottom() - 1
        );
        poseStack.pushMatrix();
        poseStack.translate(0.0F, -scrollPixels);
        StructureRecipeCategory.drawScrollableContents(recipe, guiGraphics, 0, 0);
        drawSlots(guiGraphics, scrollPixels);
        poseStack.popMatrix();
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
        if (userInput.getKey().getValue() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (!userInput.isSimulate()) {
            dragOriginY = -1.0D;
        }
        if (!contains(scrollbarArea, mouseX, mouseY) || getHiddenAmount() == 0) {
            return false;
        }
        if (userInput.isSimulate()) {
            ScreenRectangle markerArea = getScrollbarMarkerArea();
            if (!contains(markerArea, mouseX, mouseY)) {
                moveScrollbarCenterTo(markerArea, mouseY);
                markerArea = getScrollbarMarkerArea();
            }
            dragOriginY = mouseY - markerArea.top();
        }
        return true;
    }

    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        if (getHiddenAmount() > 0 && isMouseOverContent(mouseX, mouseY)) {
            float scrollAmount = (float) (scrollDeltaY * 18.0D / Math.max(contentHeight, 1));
            scrollOffsetY = Mth.clamp(scrollOffsetY - scrollAmount, 0.0F, 1.0F);
            return true;
        }
        scrollOffsetY = 0.0F;
        return false;
    }

    @Override
    public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key mouseKey, double dragX, double dragY) {
        if (dragOriginY < 0.0D || mouseKey.getValue() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        ScreenRectangle markerArea = getScrollbarMarkerArea();
        double topY = mouseY - dragOriginY;
        moveScrollbarTo(markerArea, topY);
        return true;
    }

    private void drawSlots(GuiGraphicsExtractor guiGraphics, int scrollPixels) {
        int slotCount = Math.min(contentSlots.size(), slotPlacements.size());
        for (int index = 0; index < slotCount; index++) {
            IRecipeSlotDrawable slot = contentSlots.get(index);
            StructureRecipeCategory.SlotPlacement placement = slotPlacements.get(index);
            if (!isSlotVisible(placement.y() - scrollPixels)) {
                continue;
            }
            slot.setPosition(placement.x(), placement.y());
            slot.draw(guiGraphics);
        }
    }

    private boolean isMouseOverContent(double mouseX, double mouseY) {
        return contains(contentsArea, mouseX, mouseY);
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
        return scrollbarArea.top() + 1 + Math.round(totalSpace * scrollOffsetY);
    }

    private ScreenRectangle getScrollbarMarkerArea() {
        return new ScreenRectangle(
                scrollbarArea.left() + 1,
                getScrollbarMarkerY(),
                scrollbarArea.width() - 2,
                getScrollbarMarkerHeight()
        );
    }

    private void moveScrollbarCenterTo(ScreenRectangle markerArea, double centerY) {
        double topY = centerY - (double) markerArea.height() / 2.0D;
        moveScrollbarTo(markerArea, topY);
    }

    private void moveScrollbarTo(ScreenRectangle markerArea, double topY) {
        int minY = scrollbarArea.top() + 1;
        int maxY = scrollbarArea.bottom() - 1 - markerArea.height();
        double relativeY = topY - (double) minY;
        int totalSpace = maxY - minY;
        if (totalSpace <= 0) {
            scrollOffsetY = 0.0F;
            return;
        }
        scrollOffsetY = Mth.clamp((float) (relativeY / (double) totalSpace), 0.0F, 1.0F);
    }

    private boolean isSlotVisible(int slotY) {
        return slotY + 18 > contentsArea.top() && slotY < contentsArea.bottom();
    }

    private static boolean contains(ScreenRectangle rectangle, double x, double y) {
        return x >= rectangle.left() && x < rectangle.right() && y >= rectangle.top() && y < rectangle.bottom();
    }
}
