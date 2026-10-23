package com.mine.geometry_node.core.engine.system.asset.preview.store;

import com.mine.geometry_node.core.engine.system.asset.repository.RemoteAssetFileService;
import com.mine.geometry_node.core.engine.system.asset.preview.*;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferHashing;
import net.minecraft.server.MinecraftServer;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewServerConfig;

import java.io.*;
import java.nio.file.*;
import java.util.Optional;
import java.util.Comparator;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;

/** Persistent immutable nativepreview artifacts owned by one server world. */
public final class ServerAssetPreviewStore {
    // Shared by service and copy/move association adapters. Used only around existing disk IO.
    private static final Object IO_LOCK = new Object();
    private static final ThreadLocal<BooleanSupplier> PUBLICATION_GUARD = new ThreadLocal<>();

    private final Function<MinecraftServer, Path> roots;
    private final LongSupplier maximumBytes;

    public ServerAssetPreviewStore() {
        this(RemoteAssetFileService::previewCacheRoot, AssetPreviewServerConfig::cacheMaxBytes);
    }

    ServerAssetPreviewStore(Function<MinecraftServer, Path> roots, LongSupplier maximumBytes) {
        this.roots = roots;
        this.maximumBytes = maximumBytes;
    }

    public static <T> T withPublicationGuard(BooleanSupplier allowed, AssetTransferIoExecutor.IoSupplier<T> operation)
            throws Exception {
        BooleanSupplier previous = PUBLICATION_GUARD.get();
        PUBLICATION_GUARD.set(allowed);
        try { return operation.get(); }
        finally {
            if (previous == null) PUBLICATION_GUARD.remove(); else PUBLICATION_GUARD.set(previous);
        }
    }

    private static boolean ownerCurrent() {
        BooleanSupplier guard = PUBLICATION_GUARD.get();
        return guard == null || guard.getAsBoolean();
    }

    public long size(MinecraftServer server) throws IOException {
        synchronized (IO_LOCK) { return AssetPreviewCacheMaintenance.size(roots.apply(server)); }
    }

    public void enforceLimit(MinecraftServer server) throws IOException {
        synchronized (IO_LOCK) {
            if (!ownerCurrent()) return;
            AssetPreviewCacheMaintenance.enforceLimit(roots.apply(server),
                    maximumBytes.getAsLong(), null, METADATA_MAGIC);
        }
    }

    public void clear(MinecraftServer server) throws IOException {
        synchronized (IO_LOCK) {
            if (!ownerCurrent()) return;
            Path root = roots.apply(server);
            if (Files.isSymbolicLink(root)) throw new IOException("Refusing to clear a linked preview cache root");
            // Staging belongs to active producers. Only derived, published output is cache data.
            for (String directory : java.util.List.of("metadata", "artifacts")) {
                Path target = root.resolve(directory);
                if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
                try (var paths = Files.walk(target)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static final int METADATA_MAGIC = 0x474E5052;

    public Optional<StoredPreview> find(MinecraftServer server, AssetPreviewRevision revision) throws IOException {
        synchronized (IO_LOCK) {
            Path root = roots.apply(server);
            Path metadata = metadataPath(root, revision.cacheKey());
            if (!Files.isRegularFile(metadata) || Files.isSymbolicLink(metadata)) return Optional.empty();
            AssetPreviewDescriptor descriptor = readMetadata(metadata);
            if (!descriptor.revision().equals(revision)) return Optional.empty();
            Path artifact = AssetPreviewCachePaths.resolveArtifact(root.resolve("artifacts"),
                    revision.cacheKey(), descriptor.format());
            if (!Files.isRegularFile(artifact) || Files.isSymbolicLink(artifact)
                    || Files.size(artifact) != descriptor.encodedBytes()) return Optional.empty();
            AssetPreviewCacheMaintenance.touch(artifact);
            return Optional.of(new StoredPreview(descriptor, artifact));
        }
    }

    public byte[] read(StoredPreview preview) throws IOException {
        synchronized (IO_LOCK) {
            return readContent(preview.path(), preview.descriptor());
        }
    }

    private static byte[] readContent(Path source, AssetPreviewDescriptor descriptor) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Preview artifact is unavailable");
        }
        byte[] content;
        try (InputStream input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)) {
            // Descriptor construction already enforces MAX_ENCODED_BYTES. One extra byte detects
            // append/racing replacement without allocating according to the file's mutable size.
            content = input.readNBytes(descriptor.encodedBytes() + 1);
        }
        if (content.length != descriptor.encodedBytes()
                || !AssetTransferHashing.toHex(AssetTransferHashing.newSha256().digest(content))
                        .equals(descriptor.sha256())) {
            throw new IOException("Preview artifact does not match its descriptor");
        }
        return content;
    }

    public StoredPreview publish(MinecraftServer server, Path verifiedSource,
                                 AssetPreviewDescriptor descriptor) throws IOException {
        synchronized (IO_LOCK) {
            if (!ownerCurrent()) throw new IOException("Preview owner has ended");
            byte[] content = readContent(verifiedSource, descriptor);
            Path root = roots.apply(server);
            Path artifact = AssetPreviewCachePaths.resolveArtifact(root.resolve("artifacts"),
                    descriptor.revision().cacheKey(), descriptor.format());
            Files.createDirectories(artifact.getParent());
            writeArtifact(content, artifact);
            writeMetadata(metadataPath(root, descriptor.revision().cacheKey()), descriptor);

            return new StoredPreview(descriptor, artifact);
        }
    }

    public Optional<StoredPreview> copyAssociation(MinecraftServer server, AssetPreviewRevision sourceRevision,
                                                   AssetPreviewRevision targetRevision) throws IOException {
        synchronized (IO_LOCK) {
            Optional<StoredPreview> source = find(server, sourceRevision);
            if (source.isEmpty()) return Optional.empty();
            AssetPreviewDescriptor descriptor = source.get().descriptor();
            AssetPreviewDescriptor targetDescriptor = new AssetPreviewDescriptor(targetRevision, descriptor.format(),
                    descriptor.width(), descriptor.height(), descriptor.encodedBytes(), descriptor.sha256());
            return Optional.of(publish(server, source.get().path(), targetDescriptor));
        }
    }

    private static Path metadataPath(Path root, String keyValue) {
        String key = AssetPreviewCachePaths.validateCacheKey(keyValue);
        Path base = root.toAbsolutePath().normalize().resolve("metadata");
        Path path = base.resolve(key.substring(0, 2)).resolve(key + ".bin").normalize();
        if (!path.startsWith(base)) throw new IllegalArgumentException("Preview metadata escapes cache root");
        return path;
    }

    private static void writeMetadata(Path target, AssetPreviewDescriptor descriptor) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            AssetPreviewMetadataCodec.write(out, METADATA_MAGIC, descriptor);
        }
        atomicMove(temporary, target);
    }

    private static AssetPreviewDescriptor readMetadata(Path source) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
            return AssetPreviewMetadataCodec.read(in, METADATA_MAGIC);
        }
    }

    private static void writeArtifact(byte[] content, Path target) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, content);
        atomicMove(temporary, target);
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    public record StoredPreview(AssetPreviewDescriptor descriptor, Path path) {}
}
