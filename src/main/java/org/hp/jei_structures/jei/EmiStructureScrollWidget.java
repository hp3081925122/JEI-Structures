package org.hp.jei_structures.jei;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.util.ImmutableRect2i;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class EmiStructureScrollWidget extends Widget implements EmiStructureScrollableWidget {

    private static final int SCROLLBAR_WIDTH = 14;
    private static final int MIN_SCROLL_MARKER_HEIGHT = 14;
    private static final int SLOT_SIZE = 18;

    private final EmiStructureRecipe emiRecipe;
    private final StructureRecipe recipe;
    private final List<StructureRecipe.SlotDisplay> slots;
    private final List<StructureRecipeCategory.SlotPlacement> placements;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int contentTop;
    private final int viewportHeight;
    private final int contentHeight;
    private final DrawableNineSliceTexture scrollbarMarker;
    private final DrawableNineSliceTexture scrollbarBackground;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private int scrollbarGrabOffset;

    public EmiStructureScrollWidget(EmiStructureRecipe emiRecipe, int x, int y, int width, int height) {
        this.emiRecipe = emiRecipe;
        this.recipe = emiRecipe.getStructureRecipe();
        this.slots = collectSlots(recipe);
        this.placements = StructureRecipeCategory.getSlotPlacements(recipe);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.contentTop = StructureRecipeCategory.getContentY(recipe);
        this.viewportHeight = height - contentTop;
        this.contentHeight = Math.max(viewportHeight, recipe.getTotalContentHeight());
        Textures textures = Internal.getTextures();
        this.scrollbarMarker = textures.getScrollbarMarker();
        this.scrollbarBackground = textures.getScrollbarBackground();
    }

    @Override
    public Bounds getBounds() {
        return new Bounds(x, y, width, height);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        int contentX = x + StructureRecipeCategory.getContentX();
        int contentY = y + contentTop;
        int contentWidth = StructureRecipeCategory.getContentWidth();
        int contentViewportHeight = viewportHeight;

        StructureRecipeCategory.drawPanel(guiGraphics, contentX, contentY, contentWidth, contentViewportHeight);
        StructureRecipeCategory.drawFixedHeader(recipe, guiGraphics, contentX, contentY);

        Matrix4f matrix = guiGraphics.pose().last().pose();
        int screenX = Math.round(matrix.m30());
        int screenY = Math.round(matrix.m31());
        guiGraphics.enableScissor(
                screenX + contentX + 1,
                screenY + contentY + 1,
                screenX + contentX + contentWidth - StructureRecipeCategory.getScrollbarWidth(),
                screenY + contentY + contentViewportHeight - 1
        );
        StructureRecipeCategory.drawScrollableContents(recipe, guiGraphics, contentX, contentY - scrollOffset);
        drawSlots(guiGraphics, mouseX, mouseY, delta);
        guiGraphics.disableScissor();
        drawScrollbar(guiGraphics);
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        int index = hoveredSlotIndex(mouseX, mouseY);
        if (index < 0) {
            return List.of();
        }
        StructureRecipe.SlotDisplay display = slots.get(index);
        StructureRecipeCategory.SlotPlacement placement = placements.get(index);
        int slotX = x + StructureRecipeCategory.getContentX() + placement.x();
        int slotY = y + contentTop + placement.y() - scrollOffset;
        List<Component> tooltip = recipe.getSlotTooltips(display.slotName());
        if (display.kind() == StructureRecipe.SlotKind.ITEM && display.itemStack() != null && !display.itemStack().isEmpty()) {
            SlotWidget slot = createItemSlot(display, slotX, slotY);
            return slot.getTooltip(mouseX, mouseY);
        }
        return toClientTooltip(tooltip);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && getHiddenAmount() > 0 && isOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            scrollbarGrabOffset = mouseY >= scrollbarThumbY() && mouseY < scrollbarThumbY() + scrollbarThumbHeight()
                    ? mouseY - scrollbarThumbY()
                    : scrollbarThumbHeight() / 2;
            updateScrollFromMouse(mouseY);
            return true;
        }
        int index = hoveredSlotIndex(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        StructureRecipe.SlotDisplay display = slots.get(index);
        if (display.kind() != StructureRecipe.SlotKind.ITEM || display.itemStack() == null || display.itemStack().isEmpty()) {
            return false;
        }
        StructureRecipeCategory.SlotPlacement placement = placements.get(index);
        int slotX = x + StructureRecipeCategory.getContentX() + placement.x();
        int slotY = y + contentTop + placement.y() - scrollOffset;
        return createItemSlot(display, slotX, slotY).mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean jeiStructures$mouseScrolled(int mouseX, int mouseY, double delta) {
        if (!isOverContent(mouseX, mouseY) || getHiddenAmount() <= 0) {
            return false;
        }
        scrollOffset = clamp(scrollOffset - (int) Math.signum(delta) * SLOT_SIZE, 0, getHiddenAmount());
        return true;
    }

    @Override
    public boolean jeiStructures$mouseDragged(int mouseX, int mouseY, int button, double dragX, double dragY) {
        if (!draggingScrollbar || button != 0) {
            return false;
        }
        updateScrollFromMouse(mouseY);
        return true;
    }

    @Override
    public boolean jeiStructures$mouseReleased(int mouseX, int mouseY, int button) {
        if (!draggingScrollbar || button != 0) {
            return false;
        }
        draggingScrollbar = false;
        return true;
    }

    private void drawSlots(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        int slotCount = Math.min(slots.size(), placements.size());
        for (int index = 0; index < slotCount; index++) {
            StructureRecipe.SlotDisplay display = slots.get(index);
            StructureRecipeCategory.SlotPlacement placement = placements.get(index);
            int slotX = x + StructureRecipeCategory.getContentX() + placement.x();
            int slotY = y + contentTop + placement.y() - scrollOffset;
            if (!isSlotVisible(slotY)) {
                continue;
            }
            if (display.kind() == StructureRecipe.SlotKind.ITEM && display.itemStack() != null && !display.itemStack().isEmpty()) {
                createItemSlot(display, slotX, slotY).render(guiGraphics, mouseX, mouseY, delta);
            } else if (display.kind() == StructureRecipe.SlotKind.BIOME && display.biome() != null) {
                drawBiomeSlot(guiGraphics, display.biome(), slotX, slotY);
            }
        }
    }

    private SlotWidget createItemSlot(StructureRecipe.SlotDisplay display, int slotX, int slotY) {
        SlotWidget slot = new SlotWidget(EmiStack.of(display.itemStack()), slotX, slotY).drawBack(true);
        for (Component line : recipe.getSlotTooltips(display.slotName())) {
            slot.appendTooltip(line);
        }
        return slot;
    }

    private void drawBiomeSlot(GuiGraphics guiGraphics, StructureBiomeIcon biome, int slotX, int slotY) {
        guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xFF8B8B8B);
        guiGraphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, 0xFF373737);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(slotX + 1, slotY + 1, 0.0F);
        BiomeIngredient.INSTANCE.render(guiGraphics, biome);
        guiGraphics.pose().popPose();
    }

    private int hoveredSlotIndex(int mouseX, int mouseY) {
        if (!isOverContent(mouseX, mouseY)) {
            return -1;
        }
        int slotCount = Math.min(slots.size(), placements.size());
        for (int index = 0; index < slotCount; index++) {
            StructureRecipeCategory.SlotPlacement placement = placements.get(index);
            int slotX = x + StructureRecipeCategory.getContentX() + placement.x();
            int slotY = y + contentTop + placement.y() - scrollOffset;
            if (isSlotVisible(slotY) && mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                return index;
            }
        }
        return -1;
    }

    private boolean isSlotVisible(int slotY) {
        int contentY = y + contentTop;
        return slotY + SLOT_SIZE > contentY && slotY < contentY + viewportHeight;
    }

    private boolean isOverContent(int mouseX, int mouseY) {
        int contentX = x + StructureRecipeCategory.getContentX();
        int contentY = y + contentTop;
        return mouseX >= contentX
                && mouseX < contentX + StructureRecipeCategory.getContentWidth()
                && mouseY >= contentY
                && mouseY < contentY + viewportHeight;
    }

    private void drawScrollbar(GuiGraphics guiGraphics) {
        int top = y + contentTop;
        scrollbarBackground.draw(guiGraphics, new ImmutableRect2i(scrollbarX(), top, SCROLLBAR_WIDTH, viewportHeight));
        scrollbarMarker.draw(guiGraphics, new ImmutableRect2i(scrollbarX() + 1, scrollbarThumbY(), SCROLLBAR_WIDTH - 2, scrollbarThumbHeight()));
    }

    private boolean isOverScrollbar(int mouseX, int mouseY) {
        int top = y + contentTop;
        return mouseX >= scrollbarX()
                && mouseX < scrollbarX() + SCROLLBAR_WIDTH
                && mouseY >= top
                && mouseY < top + viewportHeight;
    }

    private int scrollbarX() {
        return x + StructureRecipeCategory.getContentX() + StructureRecipeCategory.getContentWidth() - SCROLLBAR_WIDTH;
    }

    private int scrollbarTrackHeight() {
        return viewportHeight - 2;
    }

    private int scrollbarThumbHeight() {
        return Math.min(scrollbarTrackHeight(), Math.max(MIN_SCROLL_MARKER_HEIGHT, viewportHeight * scrollbarTrackHeight() / contentHeight));
    }

    private int scrollbarThumbY() {
        int trackY = y + contentTop + 1;
        return trackY + scrollOffset * (scrollbarTrackHeight() - scrollbarThumbHeight()) / Math.max(1, getHiddenAmount());
    }

    private void updateScrollFromMouse(int mouseY) {
        int trackY = y + contentTop + 1;
        int availableTrack = Math.max(1, scrollbarTrackHeight() - scrollbarThumbHeight());
        int thumbTop = clamp(mouseY - scrollbarGrabOffset, trackY, trackY + availableTrack);
        scrollOffset = clamp((thumbTop - trackY) * getHiddenAmount() / availableTrack, 0, getHiddenAmount());
    }

    private int getHiddenAmount() {
        return Math.max(0, contentHeight - viewportHeight);
    }

    private static List<StructureRecipe.SlotDisplay> collectSlots(StructureRecipe recipe) {
        List<StructureRecipe.SlotDisplay> displays = new ArrayList<>();
        for (StructureRecipe.ContentBlock block : recipe.getContentBlocks()) {
            displays.addAll(block.slots());
        }
        return List.copyOf(displays);
    }

    private static List<ClientTooltipComponent> toClientTooltip(List<Component> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<ClientTooltipComponent> tooltip = new ArrayList<>(lines.size());
        for (Component line : lines) {
            tooltip.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        }
        return List.copyOf(tooltip);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
