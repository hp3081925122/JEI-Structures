package org.hp.jei_structures.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.hp.jei_structures.jei.JeiStructuresPlugin;

import java.util.HashSet;
import java.util.Set;

public final class CurrentStructureClientState {

    private static String currentStructureId = "";
    private static final Set<String> shownToastStructureIds = new HashSet<>();

    private CurrentStructureClientState() {
    }

    public static void updateCurrentStructure(String structureId) {
        currentStructureId = structureId == null ? "" : structureId;
        if (!currentStructureId.isBlank() && shownToastStructureIds.add(currentStructureId)) {
            CurrentStructureToast.show(currentStructureId);
        }
    }

    public static void handleClientTick() {
        while (JeiStructuresClientEvents.OPEN_CURRENT_STRUCTURE.consumeClick()) {
            if (currentStructureId.isBlank()) {
                continue;
            }
            if (!JeiStructuresPlugin.openStructureRecipe(currentStructureId)) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(Component.translatable("jei_structures.toast.jei_unavailable").withStyle(ChatFormatting.RED), true);
                }
            }
        }
    }
}
