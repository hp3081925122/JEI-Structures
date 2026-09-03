package org.hp.jei_structures.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.hp.jei_structures.JeiStructures;

public final class JeiStructuresNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(JeiStructures.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static int packetId;
    private static boolean registered;

    private JeiStructuresNetwork() {
    }

    // 注册当前结构同步数据包，供服务器把玩家所在结构发送到客户端。
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(packetId++, CurrentStructurePacket.class, CurrentStructurePacket::encode, CurrentStructurePacket::decode, CurrentStructurePacket::handle);
    }
}
