package org.hp.jei_structures.jei;

import org.hp.jei_structures.JeiStructures;
import org.hp.jei_structures.config.JeiStructuresConfig;

import java.util.concurrent.atomic.AtomicLongArray;

public final class DebugRenderTiming {

    public enum Section {
        WIDGET,
        CONTENTS,
        SLOTS,
        HEADER,
        TOOLTIP,
        SET_RECIPE,
        CREATE_EXTRAS,
        LOOKUP,
        SHARED_RECIPES,
        BUILD_RECIPES,
        RECIPE_COPY,
        RECIPE_NAME,
        RECIPE_GENERATION,
        RECIPE_SPECIAL,
        RECIPE_ENTITIES,
        RECIPE_STORED,
        RECIPE_LOOT,
        RECIPE_LOOKUP,
        LOOT_GROUPS,
        LOOT_STACK,
        LOOT_SLOT,
        LOOT_TOOLTIP
    }

    private static final int SECTION_COUNT = Section.values().length;
    private static final long SUMMARY_INTERVAL_NANOS = 1_000_000_000L;
    private static final long SLOW_FRAME_NANOS = 100_000_000L;
    private static final long SLOW_SINGLE_NANOS = 5_000_000L;
    private static final AtomicLongArray COUNTS = new AtomicLongArray(SECTION_COUNT);
    private static final AtomicLongArray TOTALS = new AtomicLongArray(SECTION_COUNT);
    private static final AtomicLongArray MAXES = new AtomicLongArray(SECTION_COUNT);
    private static final AtomicLongArray FRAME_TOTALS = new AtomicLongArray(SECTION_COUNT);
    private static long lastFrameNanos;
    private static long summaryStartNanos;
    private static int frames;
    private static long frameIntervalTotal;
    private static long frameIntervalMax;
    private static String lastStructureId = "";

    private DebugRenderTiming() {
    }

    public static boolean enabled() {
        return JeiStructuresConfig.debugRenderTiming();
    }

    public static long start() {
        return enabled() ? System.nanoTime() : 0L;
    }

    public static void end(Section section, long start) {
        end(section, start, null);
    }

    public static void end(Section section, long start, String detail) {
        if (start == 0L) {
            return;
        }
        long elapsed = System.nanoTime() - start;
        int index = section.ordinal();
        COUNTS.incrementAndGet(index);
        TOTALS.addAndGet(index, elapsed);
        FRAME_TOTALS.addAndGet(index, elapsed);
        MAXES.accumulateAndGet(index, elapsed, Math::max);
        if (detail != null && elapsed >= SLOW_SINGLE_NANOS) {
            JeiStructures.LOGGER.info("[JEI_DEBUG] render slow {} {} ms detail={} thread={}", section, millis(elapsed), detail, Thread.currentThread().getName());
        }
    }

    public static synchronized void frame(StructureRecipe recipe, int slotCount) {
        if (!enabled()) {
            return;
        }
        long now = System.nanoTime();
        String structureId = recipe.getStructureId();
        if (!structureId.equals(lastStructureId) || lastFrameNanos == 0L || now - lastFrameNanos > 5_000_000_000L) {
            JeiStructures.LOGGER.info("[JEI_DEBUG] render page structure={} slots={} blocks={} contentHeight={}", structureId, slotCount, recipe.getContentBlocks().size(), recipe.getTotalContentHeight());
            lastStructureId = structureId;
            lastFrameNanos = 0L;
        }
        if (lastFrameNanos != 0L) {
            long interval = now - lastFrameNanos;
            frames++;
            frameIntervalTotal += interval;
            frameIntervalMax = Math.max(frameIntervalMax, interval);
            if (interval >= SLOW_FRAME_NANOS) {
                JeiStructures.LOGGER.info("[JEI_DEBUG] render slow frame {} ms structure={} ownTime={}", millis(interval), structureId, describeFrameTotals());
            }
        } else {
            summaryStartNanos = now;
        }
        resetFrameTotals();
        lastFrameNanos = now;
        if (now - summaryStartNanos >= SUMMARY_INTERVAL_NANOS) {
            logSummary(structureId);
            summaryStartNanos = now;
        }
    }

    public static void logSections(String label, Section... sections) {
        if (!enabled()) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (Section section : sections) {
            int index = section.ordinal();
            long count = COUNTS.getAndSet(index, 0L);
            long total = TOTALS.getAndSet(index, 0L);
            long max = MAXES.getAndSet(index, 0L);
            builder.append(' ').append(section).append("[n=").append(count)
                    .append(",total=").append(millis(total))
                    .append(",max=").append(millis(max)).append(']');
        }
        JeiStructures.LOGGER.info("[JEI_DEBUG] render {}{}", label, builder);
    }

    private static void logSummary(String structureId) {
        StringBuilder builder = new StringBuilder();
        for (Section section : Section.values()) {
            int index = section.ordinal();
            long count = COUNTS.getAndSet(index, 0L);
            long total = TOTALS.getAndSet(index, 0L);
            long max = MAXES.getAndSet(index, 0L);
            if (count == 0L) {
                continue;
            }
            builder.append(' ').append(section).append("[n=").append(count)
                    .append(",total=").append(millis(total))
                    .append(",max=").append(millis(max)).append(']');
        }
        JeiStructures.LOGGER.info(
                "[JEI_DEBUG] render summary structure={} frames={} avgFrame={} ms maxFrame={} ms{}",
                structureId,
                frames,
                frames > 0 ? millis(frameIntervalTotal / frames) : "0",
                millis(frameIntervalMax),
                builder
        );
        frames = 0;
        frameIntervalTotal = 0L;
        frameIntervalMax = 0L;
    }

    private static String describeFrameTotals() {
        StringBuilder builder = new StringBuilder();
        for (Section section : Section.values()) {
            long total = FRAME_TOTALS.get(section.ordinal());
            if (total > 0L) {
                builder.append(section).append('=').append(millis(total)).append(' ');
            }
        }
        return builder.toString().trim();
    }

    private static void resetFrameTotals() {
        for (int index = 0; index < SECTION_COUNT; index++) {
            FRAME_TOTALS.set(index, 0L);
        }
    }

    private static String millis(long nanos) {
        return String.format("%.2f", nanos / 1_000_000.0D);
    }
}
