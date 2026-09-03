package org.hp.jei_structures.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.hp.jei_structures.JeiStructures;
import org.hp.jei_structures.jei.JeiStructuresPlugin;

import java.util.HashSet;
import java.util.Set;

public final class CurrentStructureClientState {

    private static String currentStructureId = "";
    private static final Set<String> shownToastStructureIds = new HashSet<>();

    private CurrentStructureClientState() {
    }

    // 保存服务器同步的结构 ID，并在首次进入该结构时显示提示。
    public static void updateCurrentStructure(String structureId) {
        currentStructureId = structureId == null ? "" : structureId;
        boolean showToast = !currentStructureId.isBlank() && shownToastStructureIds.add(currentStructureId);
        JeiStructures.LOGGER.debug("Updated current structure client state: structure={}, showToast={}", currentStructureId, showToast);
        if (showToast) {
            CurrentStructureToast.show(currentStructureId);
        }
    }

    // 消费按键并打开当前结构的 JEI 或 EMI 页面。
    public static void handleClientTick() {
        while (JeiStructuresClientEvents.OPEN_CURRENT_STRUCTURE.consumeClick()) {
            if (currentStructureId.isBlank()) {
                JeiStructures.LOGGER.debug("Ignored current structure key press because no structure is active");
                continue;
            }
            JeiStructures.LOGGER.debug("Opening current structure recipe: {}", currentStructureId);
            if (!JeiStructuresPlugin.openStructureRecipe(currentStructureId)) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(Component.translatable("jei_structures.toast.jei_unavailable").withStyle(ChatFormatting.RED), true);
                }
            }
        }
    }
}
