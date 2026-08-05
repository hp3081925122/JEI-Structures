package org.hp.jei_structures.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.hp.jei_structures.debug.DebugCaptureOptimizationGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerGamePacketListenerCaptureSyncMixin {

    @Shadow
    protected abstract GameProfile playerProfile();

    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void jei_structures$suppressCaptureClientSync(Packet<?> packet, CallbackInfo callbackInfo) {
        if (!DebugCaptureOptimizationGuard.shouldSuppressClientboundPacket(this.playerProfile().id(), packet)) {
            return;
        }
        callbackInfo.cancel();
    }
}
