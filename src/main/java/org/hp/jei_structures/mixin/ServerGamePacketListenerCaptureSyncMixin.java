package org.hp.jei_structures.mixin;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.PacketSendListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.hp.jei_structures.debug.DebugCaptureOptimizationGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerGamePacketListenerCaptureSyncMixin {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void jei_structures$suppressCaptureClientSync(Packet<?> packet, PacketSendListener listener, CallbackInfo callbackInfo) {
        if (!((Object) this instanceof ServerGamePacketListenerImpl gamePacketListener)) {
            return;
        }
        ServerPlayer player = gamePacketListener.getPlayer();
        if (!DebugCaptureOptimizationGuard.shouldSuppressClientboundPacket(player, packet)) {
            return;
        }
        callbackInfo.cancel();
    }
}
