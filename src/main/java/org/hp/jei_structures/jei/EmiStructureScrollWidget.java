package org.hp.jei_structures.jei;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class EmiStructureScrollWidget extends Widget implements EmiStructureScrollableWidget {

    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SLOT_SIZE = 18;

    private final EmiStructureRecipe emiRecipe;
    private final StructureRecipe recipe;
    private final List<StructureRecipe.SlotDisplay> slots;
    private final List<StructureRecipeCategory.SlotPlacement> placements;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int contentHeight;
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
        this.contentHeight = Math.max(StructureRecipeCategory.getContentHeight(), recipe.getTotalContentHeight());
    }

    @Override
    public Bounds getBounds() {
        return new Bounds(x, y, width, height);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        int contentX = x + StructureRecipeCategory.getContentX();
        int contentY = y + StructureRecipeCategory.getContentY();
        int contentWidth = StructureRecipeCategory.getContentWidth();
        int contentViewportHeight = StructureRecipeCategory.getContentHeight();

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
        int slotY = y + StructureRecipeCategory.getContentY() + placement.y() - scrollOffset;
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
        int slotY = y + StructureRecipeCategory.getContentY() + placement.y() - scrollOffset;
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
            int slotY = y + StructureRecipeCategory.getContentY() + placement.y() - scrollOffset;
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
            int slotY = y + StructureRecipeCategory.getContentY() + placement.y() - scrollOffset;
            if (isSlotVisible(slotY) && mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                return index;
            }
        }
        return -1;
    }

    private boolean isSlotVisible(int slotY) {
        int contentY = y + StructureRecipeCategory.getContentY();
        return slotY + SLOT_SIZE > contentY && slotY < contentY + StructureRecipeCategory.getContentHeight();
    }

    private boolean isOverContent(int mouseX, int mouseY) {
        int contentX = x + StructureRecipeCategory.getContentX();
        int contentY = y + StructureRecipeCategory.getContentY();
        return mouseX >= contentX
                && mouseX < contentX + StructureRecipeCategory.getContentWidth()
                && mouseY >= contentY
                && mouseY < contentY + StructureRecipeCategory.getContentHeight();
    }

    private void drawScrollbar(GuiGraphics guiGraphics) {
        int barX = scrollbarX();
        int contentY = y + StructureRecipeCategory.getContentY();
        int contentViewportHeight = StructureRecipeCategory.getContentHeight();
        guiGraphics.fill(barX, contentY + 4, barX + 8, contentY + contentViewportHeight - 4, 0xFFB5B5B5);
        if (getHiddenAmount() <= 0) {
            guiGraphics.fill(barX + 1, contentY + 5, barX + 7, contentY + contentViewportHeight - 5, 0xFFE0E0E0);
            return;
        }
        guiGraphics.fill(barX + 1, scrollbarThumbY(), barX + 7, scrollbarThumbY() + scrollbarThumbHeight(), 0xFF8A8A8A);
    }

    private boolean isOverScrollbar(int mouseX, int mouseY) {
        int contentY = y + StructureRecipeCategory.getContentY();
        return mouseX >= scrollbarX()
                && mouseX < scrollbarX() + 8
                && mouseY >= contentY + 4
                && mouseY < contentY + StructureRecipeCategory.getContentHeight() - 4;
    }

    private int scrollbarX() {
        return x + StructureRecipeCategory.getContentX() + StructureRecipeCategory.getContentWidth() - SCROLLBAR_WIDTH;
    }

    private int scrollbarTrackHeight() {
        return StructureRecipeCategory.getContentHeight() - 10;
    }

    private int scrollbarThumbHeight() {
        return Math.max(16, StructureRecipeCategory.getContentHeight() * scrollbarTrackHeight() / contentHeight);
    }

    private int scrollbarThumbY() {
        int trackY = y + StructureRecipeCategory.getContentY() + 5;
        return trackY + scrollOffset * (scrollbarTrackHeight() - scrollbarThumbHeight()) / Math.max(1, getHiddenAmount());
    }

    private void updateScrollFromMouse(int mouseY) {
        int trackY = y + StructureRecipeCategory.getContentY() + 5;
        int availableTrack = Math.max(1, scrollbarTrackHeight() - scrollbarThumbHeight());
        int thumbTop = clamp(mouseY - scrollbarGrabOffset, trackY, trackY + availableTrack);
        scrollOffset = clamp((thumbTop - trackY) * getHiddenAmount() / availableTrack, 0, getHiddenAmount());
    }

    private int getHiddenAmount() {
        return Math.max(0, contentHeight - StructureRecipeCategory.getContentHeight());
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
