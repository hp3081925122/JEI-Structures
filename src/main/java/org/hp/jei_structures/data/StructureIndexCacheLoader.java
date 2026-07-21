package org.hp.jei_structures.data;

import org.hp.jei_structures.JeiStructures;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class StructureIndexCacheLoader {

    private static final int BINARY_CACHE_FORMAT = 4;
    private static volatile StructureIndexCache cached;

    private StructureIndexCacheLoader() {
    }

    public static StructureIndexCache load() {
        StructureIndexCache snapshot = cached;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (StructureIndexCacheLoader.class) {
            if (cached == null) {
                cached = loadBinaryCache();
            }
            return cached;
        }
    }

    public static void writeExportedCache(StructureIndexCache cache) {
        if (!cache.prepareRuntimeLootTables()) {
            return;
        }
        Path binaryPath = StructureIndexPaths.getBinaryCachePath();
        Path temporaryPath = binaryPath.resolveSibling(binaryPath.getFileName() + ".tmp");
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
        } catch (Exception exception) {
            JeiStructures.LOGGER.error("Failed to write structure index binary cache: {}", binaryPath, exception);
            try {
                Files.deleteIfExists(temporaryPath);
            } catch (Exception ignored) {
            }
        }
    }

    private static StructureIndexCache loadBinaryCache() {
        Path binaryPath = StructureIndexPaths.getBinaryCachePath();
        if (!Files.exists(binaryPath)) {
            JeiStructures.LOGGER.warn("Structure index binary cache is missing: {}", binaryPath);
            return new StructureIndexCache();
        }
        long startedAt = System.nanoTime();
        try (ObjectInputStream input = new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(binaryPath))))) {
            if (input.readInt() != BINARY_CACHE_FORMAT) {
                JeiStructures.LOGGER.warn("Structure index binary cache format mismatch: {}", binaryPath);
                return new StructureIndexCache();
            }
            Object value = input.readObject();
            if (value instanceof StructureIndexCache cache && cache.prepareRuntimeLootTables()) {
                JeiStructures.LOGGER.info("Loaded {} structure index entries from binary cache in {} ms", cache.structures.size(), Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
                return cache;
            }
        } catch (Exception exception) {
            JeiStructures.LOGGER.error("Failed to read structure index binary cache: {}", binaryPath, exception);
            return new StructureIndexCache();
        }
        JeiStructures.LOGGER.warn("Structure index binary cache has an unsupported payload: {}", binaryPath);
        return new StructureIndexCache();
    }
}
