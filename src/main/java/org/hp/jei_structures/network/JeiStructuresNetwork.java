package org.hp.jei_structures.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.hp.jei_structures.JeiStructures;

public final class JeiStructuresNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(JeiStructures.MODID, "network"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private JeiStructuresNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                0,
                CurrentStructureMessage.class,
                CurrentStructureMessage::encode,
                CurrentStructureMessage::decode,
                (message, contextSupplier) -> {
                    var context = contextSupplier.get();
                    context.enqueueWork(() -> CurrentStructureClientboundHandler.handle(message.structureId()));
                    context.setPacketHandled(true);
                },
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
}
