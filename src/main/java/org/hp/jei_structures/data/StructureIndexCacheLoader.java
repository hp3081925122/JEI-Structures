package org.hp.jei_structures.data;

import org.hp.jei_structures.JeiStructures;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StructureIndexCacheLoader {

    private static volatile CachedIndex cached;

    private StructureIndexCacheLoader() {
    }

    public static StructureIndexCache load() {
        Path path = StructureIndexPaths.getCachePath();
        long lastModified = getLastModifiedTime(path);
        CachedIndex snapshot = cached;
        if (snapshot != null && snapshot.lastModified == lastModified) {
            return snapshot.cache;
        }
        synchronized (StructureIndexCacheLoader.class) {
            snapshot = cached;
            if (snapshot == null || snapshot.lastModified != lastModified) {
                cached = new CachedIndex(loadFromDisk(path), lastModified);
            }
            return cached.cache;
        }
    }

    public static void reload() {
        synchronized (StructureIndexCacheLoader.class) {
            Path path = StructureIndexPaths.getCachePath();
            cached = new CachedIndex(loadFromDisk(path), getLastModifiedTime(path));
        }
    }

    private static StructureIndexCache loadFromDisk(Path path) {
        if (!Files.exists(path)) {
            return new StructureIndexCache();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StructureIndexCache cache = StructureIndexCache.GSON.fromJson(reader, StructureIndexCache.class);
            if (cache == null) {
                return new StructureIndexCache();
            }
            if (cache.version != StructureIndexCache.CURRENT_VERSION) {
                JeiStructures.LOGGER.warn(
                        "Structure index cache version mismatch. Current: {}, Cache: {}, falling back to empty index: {}",
                        StructureIndexCache.CURRENT_VERSION,
                        cache.version,
                        path
                );
                return new StructureIndexCache();
            }
            return cache;
        } catch (Exception exception) {
            JeiStructures.LOGGER.error("Failed to read structure index cache: {}", path, exception);
            return new StructureIndexCache();
        }
    }

    private static long getLastModifiedTime(Path path) {
        try {
            if (!Files.exists(path)) {
                return 0L;
            }
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception exception) {
            JeiStructures.LOGGER.error("Failed to read structure index cache timestamp: {}", path, exception);
            return -1L;
        }
    }

    private record CachedIndex(StructureIndexCache cache, long lastModified) {
    }
}
