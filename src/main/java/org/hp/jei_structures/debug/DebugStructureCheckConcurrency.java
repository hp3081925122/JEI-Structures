package org.hp.jei_structures.debug;

import brightspark.asynclocator.AsyncLocator;
import brightspark.asynclocator.AsyncLocatorConfigForge;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanMaps;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.hp.jei_structures.JeiStructures;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.WeakHashMap;

public final class DebugStructureCheckConcurrency {

    private static final int DEBUG_LOCATOR_THREADS = 3;
    private static final Set<StructureCheckAccess> REGISTERED_CHECKS = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean enabled;
    private static Integer previousLocatorThreads;

    private DebugStructureCheckConcurrency() {
    }

    public static synchronized void register(StructureCheckAccess access) {
        if (access == null) {
            return;
        }
        REGISTERED_CHECKS.add(access);
        access.jei_structures$setStructureCheckConcurrency(enabled);
    }

    public static synchronized void enable() {
        enabled = true;
        enableAsyncLocatorThreads();
        for (StructureCheckAccess access : REGISTERED_CHECKS) {
            access.jei_structures$setStructureCheckConcurrency(true);
        }
    }

    public static synchronized void disable() {
        enabled = false;
        for (StructureCheckAccess access : REGISTERED_CHECKS) {
            access.jei_structures$setStructureCheckConcurrency(false);
        }
        restoreAsyncLocatorThreads();
    }

    public static int getDebugLocatorThreads(int configuredThreads) {
        return Math.max(Math.max(configuredThreads, 1), DEBUG_LOCATOR_THREADS);
    }

    private static void enableAsyncLocatorThreads() {
        try {
            int configuredThreads = AsyncLocatorConfigForge.LOCATOR_THREADS.get();
            if (configuredThreads >= DEBUG_LOCATOR_THREADS) {
                return;
            }
            if (previousLocatorThreads == null) {
                previousLocatorThreads = configuredThreads;
            }
            AsyncLocatorConfigForge.LOCATOR_THREADS.set(DEBUG_LOCATOR_THREADS);
            AsyncLocator.setupExecutorService();
        } catch (RuntimeException exception) {
            previousLocatorThreads = null;
        }
    }

    private static void restoreAsyncLocatorThreads() {
        if (previousLocatorThreads == null) {
            return;
        }
        try {
            AsyncLocatorConfigForge.LOCATOR_THREADS.set(previousLocatorThreads);
            AsyncLocator.setupExecutorService();
        } catch (RuntimeException ignored) {
        } finally {
            previousLocatorThreads = null;
        }
    }

    public static Long2ObjectMap<Object2IntMap<Structure>> copyLoadedChunks(Long2ObjectMap<Object2IntMap<Structure>> source) {
        Long2ObjectOpenHashMap<Object2IntMap<Structure>> copy = new Long2ObjectOpenHashMap<>();
        if (source != null) {
            try {
                copy.putAll(source);
            } catch (RuntimeException exception) {
                JeiStructures.LOGGER.debug("Failed to copy StructureCheck loaded chunk cache; it will be rebuilt during debug capture", exception);
            }
        }
        return Long2ObjectMaps.synchronize(copy);
    }

    public static Map<Structure, Long2BooleanMap> copyFeatureChecks(Map<Structure, Long2BooleanMap> source) {
        Map<Structure, Long2BooleanMap> copy = createFeatureChecksMap();
        if (source != null) {
            try {
                for (Map.Entry<Structure, Long2BooleanMap> entry : source.entrySet()) {
                    Long2BooleanMap valueCopy = copyFeatureCheckValue(entry.getKey(), entry.getValue());
                    if (valueCopy != null) {
                        copy.put(entry.getKey(), valueCopy);
                    }
                }
            } catch (RuntimeException exception) {
                JeiStructures.LOGGER.debug("Failed to copy StructureCheck feature cache; it will be rebuilt during debug capture", exception);
            }
        }
        return copy;
    }

    private static Long2BooleanMap copyFeatureCheckValue(Structure structure, Long2BooleanMap source) {
        Long2BooleanOpenHashMap valueCopy = new Long2BooleanOpenHashMap();
        if (source != null) {
            try {
                valueCopy.putAll(source);
            } catch (RuntimeException exception) {
                JeiStructures.LOGGER.debug("Skipped one StructureCheck feature cache entry while enabling debug capture concurrency: {}", structure, exception);
                return null;
            }
        }
        return Long2BooleanMaps.synchronize(valueCopy);
    }

    public static Map<Structure, Long2BooleanMap> createFeatureChecksMap() {
        return new ConcurrentHashMap<>() {
            @Override
            public Long2BooleanMap computeIfAbsent(Structure key, Function<? super Structure, ? extends Long2BooleanMap> mappingFunction) {
                return super.computeIfAbsent(key, structure -> Long2BooleanMaps.synchronize(new Long2BooleanOpenHashMap()));
            }
        };
    }

    public interface StructureCheckAccess {
        void jei_structures$setStructureCheckConcurrency(boolean enabled);
    }
}
