package org.hp.jei_structures.debug;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class DebugCaptureTickFreeze {

    private static volatile Thread frozenThread;

    private DebugCaptureTickFreeze() {
    }

    public static void freeze(Thread serverThread) {
        frozenThread = serverThread;
    }

    public static void unfreeze() {
        frozenThread = null;
    }

    public static boolean isFrozen() {
        Thread thread = frozenThread;
        return thread != null && thread.isAlive();
    }

    public static boolean isFrozenOnServerThread() {
        Thread thread = frozenThread;
        return thread != null && Thread.currentThread() == thread;
    }

    public static boolean isEntityFrozen(Entity entity) {
        return isFrozenOnServerThread() && !(entity instanceof Player) && !hasPlayerPassenger(entity);
    }

    private static boolean hasPlayerPassenger(Entity entity) {
        for (Entity passenger : entity.getIndirectPassengers()) {
            if (passenger instanceof Player) {
                return true;
            }
        }
        return false;
    }
}
