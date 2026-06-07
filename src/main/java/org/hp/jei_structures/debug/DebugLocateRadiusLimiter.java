package org.hp.jei_structures.debug;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DebugLocateRadiusLimiter {

    private static final ConcurrentMap<UUID, Integer> ACTIVE_RADIUS_CAPS = new ConcurrentHashMap<>();

    private DebugLocateRadiusLimiter() {
    }

    public static void begin(UUID requestId, int radiusCap) {
        if (requestId == null || radiusCap <= 0) {
            return;
        }
        ACTIVE_RADIUS_CAPS.put(requestId, radiusCap);
    }

    public static void end(UUID requestId) {
        if (requestId == null) {
            return;
        }
        ACTIVE_RADIUS_CAPS.remove(requestId);
    }

    public static int clamp(int radius) {
        int radiusCap = 0;
        for (int cap : ACTIVE_RADIUS_CAPS.values()) {
            if (radiusCap <= 0 || cap < radiusCap) {
                radiusCap = cap;
            }
        }
        if (radiusCap <= 0 || radius <= 0) {
            return radius;
        }
        return Math.min(radius, radiusCap);
    }
}
