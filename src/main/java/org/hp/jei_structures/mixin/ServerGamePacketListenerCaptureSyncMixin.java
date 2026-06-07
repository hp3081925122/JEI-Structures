package org.hp.jei_structures.mixin;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.hp.jei_structures.debug.DebugCaptureOptimizationGuard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerCaptureSyncMixin {

    @Shadow
    @Final
    public ServerPlayer player;

    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void jei_structures$suppressCaptureClientSync(Packet<?> packet, CallbackInfo callbackInfo) {
        if (!DebugCaptureOptimizationGuard.shouldSuppressClientboundPacket(this.player, packet)) {
            return;
        }
        callbackInfo.cancel();
    }
}
