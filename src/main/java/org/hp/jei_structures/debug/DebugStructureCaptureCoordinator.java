package org.hp.jei_structures.debug;

public final class DebugStructureCaptureCoordinator {

    private static boolean quickActive;

    private DebugStructureCaptureCoordinator() {
    }

    public static synchronized boolean canStartQuick() {
        return !quickActive;
    }

    public static synchronized void markQuickStarted() {
        quickActive = true;
    }

    public static synchronized void markQuickStopped() {
        quickActive = false;
    }
}
