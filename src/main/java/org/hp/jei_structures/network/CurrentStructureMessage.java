package org.hp.jei_structures.network;

import net.minecraft.network.FriendlyByteBuf;

public record CurrentStructureMessage(String structureId) {

    public CurrentStructureMessage {
        structureId = structureId == null ? "" : structureId;
    }

    public static void encode(CurrentStructureMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.structureId);
    }

    public static CurrentStructureMessage decode(FriendlyByteBuf buffer) {
        return new CurrentStructureMessage(buffer.readUtf());
    }
}
