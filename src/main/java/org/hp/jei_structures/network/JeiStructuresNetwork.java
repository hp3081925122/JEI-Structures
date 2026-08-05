package org.hp.jei_structures.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.hp.jei_structures.JeiStructures;

public final class JeiStructuresNetwork {

    private JeiStructuresNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(JeiStructures.MODID).versioned("1").optional();
        registrar.playToClient(CurrentStructurePayload.TYPE, CurrentStructurePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> CurrentStructureClientboundHandler.handle(payload.structureId()))
        );
    }
}
