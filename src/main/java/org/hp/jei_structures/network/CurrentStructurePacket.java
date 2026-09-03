package org.hp.jei_structures.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.hp.jei_structures.JeiStructures;

import java.util.function.Supplier;

public record CurrentStructurePacket(String structureId) {

    public CurrentStructurePacket {
        structureId = structureId == null ? "" : structureId;
    }

    // 编码服务器发送给客户端的当前结构 ID。
    static void encode(CurrentStructurePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.structureId);
    }

    // 解码客户端收到的当前结构 ID。
    static CurrentStructurePacket decode(FriendlyByteBuf buffer) {
        return new CurrentStructurePacket(buffer.readUtf(32767));
    }

    // 在客户端线程处理当前结构更新，避免服务端加载客户端界面类。
    static void handle(CurrentStructurePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> CurrentStructureClientboundHandler.handle(packet.structureId));
        context.setPacketHandled(true);
    }
}
