package org.hp.jei_structures.debug;

import it.unimi.dsi.fastutil.longs.Long2BooleanFunction;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanMaps;

import java.util.function.LongPredicate;

public final class DebugComputeOutsideLockLong2BooleanMap extends Long2BooleanMaps.SynchronizedMap {

    public DebugComputeOutsideLockLong2BooleanMap(Long2BooleanMap map) {
        super(map);
    }

    @Override
    public boolean computeIfAbsent(long key, LongPredicate mappingFunction) {
        synchronized (sync) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        boolean value = mappingFunction.test(key);
        return putComputed(key, value);
    }

    @Override
    public boolean computeIfAbsent(long key, Long2BooleanFunction mappingFunction) {
        synchronized (sync) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        if (!mappingFunction.containsKey(key)) {
            synchronized (sync) {
                return map.get(key);
            }
        }
        boolean value = mappingFunction.get(key);
        return putComputed(key, value);
    }

    private boolean putComputed(long key, boolean value) {
        synchronized (sync) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
            map.put(key, value);
            return value;
        }
    }
}
