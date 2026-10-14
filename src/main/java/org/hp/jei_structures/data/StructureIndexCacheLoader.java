package org.hp.jei_structures.data;

import org.hp.jei_structures.JeiStructures;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class StructureIndexCacheLoader {

    private static final int BINARY_CACHE_FORMAT = 1;
    private static final int LEGACY_JSON_VERSION = 11;
    private static volatile CachedIndex cached;

    private StructureIndexCacheLoader() {
    }

    public static StructureIndexCache load() {
        Path path = getSourcePath();
        long lastModified = getLastModifiedTime(path);
        CachedIndex snapshot = cached;
        if (snapshot != null && snapshot.sourcePath.equals(path) && snapshot.lastModified == lastModified) {
            return snapshot.cache;
        }
        synchronized (StructureIndexCacheLoader.class) {
            snapshot = cached;
            if (snapshot == null || !snapshot.sourcePath.equals(path) || snapshot.lastModified != lastModified) {
                StructureIndexCache cache = loadFromDisk();
                Path sourcePath = getSourcePath();
                cached = new CachedIndex(cache, sourcePath, getLastModifiedTime(sourcePath));
            }
            return cached.cache;
        }
    }

    public static void reload() {
        synchronized (StructureIndexCacheLoader.class) {
            StructureIndexCache cache = loadFromDisk();
            Path sourcePath = getSourcePath();
            cached = new CachedIndex(cache, sourcePath, getLastModifiedTime(sourcePath));
        }
    }

    public static synchronized void writeExportedCache(StructureIndexCache cache) throws IOException {
        if (cache.version != StructureIndexCache.CURRENT_VERSION) {
            throw new IOException("Unsupported structure index version: " + cache.version);
        }
        Path binaryPath = StructureIndexPaths.getBinaryCachePath();
        Path temporaryPath = binaryPath.resolveSibling(binaryPath.getFileName() + ".tmp");
        cache.compactLootTables();
        try {
            Files.createDirectories(binaryPath.getParent());
            try (ObjectOutputStream output = new ObjectOutputStream(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temporaryPath))))) {
                output.writeInt(BINARY_CACHE_FORMAT);
                output.writeObject(cache);
            }
            try {
                Files.move(temporaryPath, binaryPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryPath, binaryPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            JeiStructures.LOGGER.error("Failed to write structure index binary cache: {}", binaryPath, exception);
            try {
                Files.deleteIfExists(temporaryPath);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        } finally {
            cache.prepareRuntimeLootTables();
        }
        cached = new CachedIndex(cache, binaryPath, getLastModifiedTime(binaryPath));
        JeiStructures.LOGGER.info("Saved compressed structure index: structures={}, uniqueLootTables={}, file={}", cache.structures.size(), cache.lootTableDetails.size(), binaryPath);
    }

    private static StructureIndexCache loadFromDisk() {
        Path path = StructureIndexPaths.getBinaryCachePath();
        if (!Files.exists(path)) {
            return loadLegacyJson();
        }
        try (ObjectInputStream input = new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(path))))) {
            if (input.readInt() != BINARY_CACHE_FORMAT) {
                JeiStructures.LOGGER.warn("Structure index binary cache format mismatch: {}", path);
                return new StructureIndexCache();
            }
            Object value = input.readObject();
            if (value instanceof StructureIndexCache cache && cache.prepareRuntimeLootTables()) {
                JeiStructures.LOGGER.info("Loaded compressed structure index: structures={}, uniqueLootTables={}, file={}", cache.structures.size(), cache.lootTableDetails.size(), path);
                return cache;
            }
            JeiStructures.LOGGER.warn("Structure index binary cache has an unsupported payload or version: {}", path);
        } catch (Exception exception) {
            JeiStructures.LOGGER.error("Failed to read structure index binary cache: {}", path, exception);
        }
        return new StructureIndexCache();
    }

    private static StructureIndexCache loadLegacyJson() {
        Path path = StructureIndexPaths.getCachePath();
        if (!Files.exists(path)) {
            return new StructureIndexCache();
        }
        StructureIndexCache cache;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            cache = StructureIndexCache.GSON.fromJson(reader, StructureIndexCache.class);
            if (cache == null || (cache.version != LEGACY_JSON_VERSION && cache.version != 12 && cache.version != StructureIndexCache.CURRENT_VERSION)) {
                JeiStructures.LOGGER.warn("Unsupported legacy structure index JSON version: {}", path);
                return new StructureIndexCache();
            }
        } catch (Exception exception) {
            JeiStructures.LOGGER.error("Failed to read legacy structure index JSON: {}", path, exception);
            return new StructureIndexCache();
        }
        cache.version = StructureIndexCache.CURRENT_VERSION;
        try {
            writeExportedCache(cache);
            JeiStructures.LOGGER.info("Migrated legacy structure index JSON to compressed binary cache: {}", path);
        } catch (IOException exception) {
            JeiStructures.LOGGER.warn("Using legacy structure index in memory after binary migration failed: {}", path, exception);
        }
        return cache;
    }

    private static Path getSourcePath() {
        Path binaryPath = StructureIndexPaths.getBinaryCachePath();
        return Files.exists(binaryPath) ? binaryPath : StructureIndexPaths.getCachePath();
    }

    private static long getLastModifiedTime(Path path) {
        try {
            return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0L;
        } catch (IOException exception) {
            JeiStructures.LOGGER.error("Failed to read structure index cache timestamp: {}", path, exception);
            return -1L;
        }
    }

    private record CachedIndex(StructureIndexCache cache, Path sourcePath, long lastModified) {
    }
}
