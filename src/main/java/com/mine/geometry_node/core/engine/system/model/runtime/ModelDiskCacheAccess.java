package com.mine.geometry_node.core.engine.system.model.runtime;

import com.mine.geometry_node.core.cache.CacheStats;
import java.io.IOException;
import java.nio.file.*;
import java.util.function.Predicate;

/** Coordinates optional derived files, never source files or live runtime assets. All I/O is worker-only. */
public final class ModelDiskCacheAccess {
    @FunctionalInterface public interface Write { void run() throws IOException; }
    private long generation;

    public synchronized long generation() { return generation; }

    /** A clear retires in-flight writes without cancelling the model that requested the result. */
    public synchronized boolean publish(long expectedGeneration, Write write) throws IOException {
        if (generation != expectedGeneration) return false;
        write.run();
        return true;
    }

    public synchronized void clear(Path directory, Predicate<String> ownedFile) throws IOException {
        generation++;
        if (!directoryExists(directory)) return;
        IOException failure = null;
        try (var files = Files.newDirectoryStream(directory)) {
            for (Path path : files) {
                if (!ownedFile.test(path.getFileName().toString())
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                try { Files.deleteIfExists(path); }
                catch (IOException error) {
                    if (failure == null) failure = new IOException("Some model cache files could not be removed");
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) throw failure;
    }

    public synchronized CacheStats stats(Path directory, Predicate<String> ownedFile) throws IOException {
        if (!directoryExists(directory)) return CacheStats.disk(0, 0);
        long entries = 0, bytes = 0;
        try (var files = Files.newDirectoryStream(directory)) {
            for (Path path : files) {
                if (!ownedFile.test(path.getFileName().toString())
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                try { bytes += Files.size(path); entries++; }
                catch (NoSuchFileException ignored) { /* External cleanup raced the inspection. */ }
            }
        }
        return CacheStats.disk(entries, bytes);
    }

    private static boolean directoryExists(Path directory) throws IOException {
        for (Path current = directory.toAbsolutePath().normalize(); current != null; current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new IOException("Refusing symbolic model cache directory: " + current);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return false;
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a model cache directory: " + directory);
        return true;
    }
}
