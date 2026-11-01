package org.hp.jei_structures.debug;

import org.hp.jei_structures.JeiStructures;

public final class DebugCaptureStallWatchdog {

    private static final long STALL_THRESHOLD_NANOS = 500_000_000L;
    private static final long DUMP_INTERVAL_NANOS = 1_000_000_000L;
    private static final int MAX_FRAMES = 60;

    private static volatile Thread serverThread;
    private static volatile Thread watchdogThread;
    private static volatile long lastProgressNanos;

    private DebugCaptureStallWatchdog() {
    }

    public static synchronized void start(Thread thread) {
        stop();
        serverThread = thread;
        lastProgressNanos = System.nanoTime();
        Thread watcher = new Thread(DebugCaptureStallWatchdog::run, "JEI Structures Stall Watchdog");
        watcher.setDaemon(true);
        watchdogThread = watcher;
        watcher.start();
    }

    public static synchronized void stop() {
        Thread watcher = watchdogThread;
        watchdogThread = null;
        serverThread = null;
        if (watcher != null) {
            watcher.interrupt();
        }
    }

    public static void markProgress() {
        lastProgressNanos = System.nanoTime();
    }

    private static void run() {
        Thread self = Thread.currentThread();
        long stallStart = 0L;
        long nextDump = 0L;
        while (watchdogThread == self) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException exception) {
                return;
            }
            Thread target = serverThread;
            if (target == null) {
                return;
            }
            long last = lastProgressNanos;
            long now = System.nanoTime();
            if (now - last < STALL_THRESHOLD_NANOS) {
                if (stallStart != 0L) {
                    JeiStructures.LOGGER.info("[JEI_DEBUG] stall ended stallMs={}", (now - stallStart) / 1_000_000L);
                }
                stallStart = 0L;
                continue;
            }
            if (stallStart != last) {
                stallStart = last;
                nextDump = now;
            }
            if (now < nextDump) {
                continue;
            }
            nextDump = now + DUMP_INTERVAL_NANOS;
            StackTraceElement[] stack = target.getStackTrace();
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < Math.min(stack.length, MAX_FRAMES); i++) {
                builder.append("\n\tat ").append(stack[i]);
            }
            JeiStructures.LOGGER.info("[JEI_DEBUG] server thread stalled stallMs={} state={}{}", (now - last) / 1_000_000L, target.getState(), builder);
        }
    }
}
