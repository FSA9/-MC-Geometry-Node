package com.mine.geometry_node.client.runtime.light.shader;

import com.mine.geometry_node.core.cache.CacheStats;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

/** Disk-only cache. Clearing never touches linked programs; stale queued writes cannot repopulate it. */
final class ShaderBinaryCacheStore {
    static final int MAGIC = 0x4e4c5031;
    private final Path root;
    private final java.util.concurrent.atomic.AtomicLong generation = new java.util.concurrent.atomic.AtomicLong();

    ShaderBinaryCacheStore(Path root) { this.root = root.toAbsolutePath().normalize(); }
    long generation() { return generation.get(); }

    synchronized CacheStats stats() throws IOException {
        List<Path> files = ownedFiles();
        long bytes = 0;
        for (Path path : files) bytes += Files.size(path);
        return CacheStats.disk(files.size(), bytes);
    }

    synchronized void clear() throws IOException {
        generation.incrementAndGet();
        IOException failure = null;
        for (Path path : ownedFiles()) {
            try { Files.deleteIfExists(path); }
            catch (IOException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    synchronized boolean write(long expectedGeneration, Path path, int format, byte[] binary) throws IOException {
        if (generation.get() != expectedGeneration) return false;
        path = path.toAbsolutePath().normalize();
        if (!path.startsWith(root) || root.relativize(path).getNameCount() != 2
                || !isBinary(path)) throw new IOException("Invalid shader cache path");
        rejectSymlinkParents(path.getParent());
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "native-volume-", ".tmp");
        try {
            try (var output = new DataOutputStream(Files.newOutputStream(temporary))) {
                output.writeInt(MAGIC); output.writeInt(format); output.writeInt(binary.length);
                try { output.write(MessageDigest.getInstance("SHA-256").digest(binary)); }
                catch (NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
                output.write(binary);
            }
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            final Path newest = path;
            try (var entries = Files.list(path.getParent())) {
                List<Path> previous = entries.filter(p -> !p.equals(newest) && isBinary(p)
                                && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                        .sorted(Comparator.comparingLong(ShaderBinaryCacheStore::modified).reversed()).toList();
                long retained = binary.length + 44L;
                for (int i = 0; i < previous.size(); i++) {
                    Path entry = previous.get(i);
                    retained += Files.size(entry);
                    if (i >= 23 || retained > 128L * 1024 * 1024) Files.deleteIfExists(entry);
                }
            }
            return true;
        } finally { Files.deleteIfExists(temporary); }
    }

    private List<Path> ownedFiles() throws IOException {
        rejectSymlinkParents(root);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return List.of();
        try (var paths = Files.walk(root, 2)) {
            return paths.filter(path -> root.relativize(path).getNameCount() == 2)
                    .filter(path -> isBinary(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList();
        }
    }

    private void rejectSymlinkParents(Path directory) throws IOException {
        for (Path path = directory; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IOException("Shader cache path contains a symbolic link: " + path);
        }
    }

    private static boolean isBinary(Path path) { return path.getFileName().toString().matches("[0-9a-f]{64}\\.bin"); }
    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis(); }
        catch (IOException error) { return 0; }
    }
}
