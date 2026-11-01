package org.hp.jei_structures.debug;

import java.util.concurrent.atomic.AtomicInteger;

public final class DebugLocateControl {

    private static final ThreadLocal<LocateToken> CURRENT = new ThreadLocal<>();
    private static final AtomicInteger RUNNING = new AtomicInteger();

    private DebugLocateControl() {
    }

    public static void begin(LocateToken token) {
        token.runStartMillis = System.currentTimeMillis();
        CURRENT.set(token);
        RUNNING.incrementAndGet();
    }

    public static void end() {
        CURRENT.remove();
        RUNNING.decrementAndGet();
    }

    public static boolean isLocateThread() {
        return CURRENT.get() != null;
    }

    public static boolean isCancelled() {
        LocateToken token = CURRENT.get();
        return token != null && token.cancelled;
    }

    public static void checkCancelled() {
        if (isCancelled()) {
            throw new LocateCancelledException();
        }
    }

    public static int runningCount() {
        return RUNNING.get();
    }

    public static final class LocateToken {
        private volatile boolean cancelled;
        private volatile long runStartMillis;

        public void cancel() {
            cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public long runStartMillis() {
            return runStartMillis;
        }
    }

    public static final class LocateCancelledException extends RuntimeException {
        private LocateCancelledException() {
            super("Structure debug locate cancelled", null, false, false);
        }
    }
}
