package org.hp.jei_structures.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.hp.jei_structures.JeiStructures;

public record CurrentStructurePayload(String structureId) implements CustomPacketPayload {

    public static final Type<CurrentStructurePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JeiStructures.MODID, "current_structure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CurrentStructurePayload> STREAM_CODEC = StreamCodec.of(
            CurrentStructurePayload::encode,
            CurrentStructurePayload::decode
    );

    public CurrentStructurePayload {
        structureId = structureId == null ? "" : structureId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, CurrentStructurePayload payload) {
        buffer.writeUtf(payload.structureId);
    }

    private static CurrentStructurePayload decode(RegistryFriendlyByteBuf buffer) {
        return new CurrentStructurePayload(buffer.readUtf());
    }
}
