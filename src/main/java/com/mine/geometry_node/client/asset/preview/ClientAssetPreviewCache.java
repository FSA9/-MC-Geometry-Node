package com.mine.geometry_node.client.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewCachePaths;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewDescriptor;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewFormat;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewIdentity;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewRevision;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferHashing;
import com.mine.geometry_node.core.engine.system.asset.preview.store.AssetPreviewCacheMaintenance;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;

/** Persistent immutable client artifacts isolated by stable server identity. */
public final class ClientAssetPreviewCache {
    private static final int METADATA_MAGIC = 0x474E5043;

    public synchronized Optional<CachedPreview> find(String serverIdentity, AssetPreviewRevision revision)
            throws IOException {
        Path root = ClientAssetPreviewPaths.serverRoot(serverIdentity);
        Path metadata = metadataPath(root, revision.cacheKey());
        if (!Files.isRegularFile(metadata) || Files.isSymbolicLink(metadata)) return Optional.empty();
        AssetPreviewDescriptor descriptor = readMetadata(metadata);
        if (!descriptor.revision().equals(revision)) return Optional.empty();
        Path artifact = AssetPreviewCachePaths.resolveArtifact(root.resolve("artifacts"),
                revision.cacheKey(), descriptor.format());
        if (!Files.isRegularFile(artifact) || Files.isSymbolicLink(artifact)
                || Files.size(artifact) != descriptor.encodedBytes()
                || !AssetTransferHashing.sha256(artifact).equals(descriptor.sha256())) {
            return Optional.empty();
        }
        AssetPreviewCacheMaintenance.touch(artifact);
        return Optional.of(new CachedPreview(descriptor, artifact));
    }

    public synchronized CachedPreview store(String serverIdentity, AssetPreviewDescriptor descriptor, byte[] content)
            throws IOException {
        if (content == null || content.length != descriptor.encodedBytes()
                || !AssetTransferHashing.toHex(AssetTransferHashing.newSha256().digest(content))
                .equals(descriptor.sha256())) {
            throw new IOException("Preview content does not match its descriptor");
        }
        Path root = ClientAssetPreviewPaths.serverRoot(serverIdentity);
        Path artifact = AssetPreviewCachePaths.resolveArtifact(root.resolve("artifacts"),
                descriptor.revision().cacheKey(), descriptor.format());
        Files.createDirectories(artifact.getParent());
        writeAtomically(artifact, content);
        writeMetadata(metadataPath(root, descriptor.revision().cacheKey()), descriptor);
        AssetPreviewCacheMaintenance.enforceLimit(ClientAssetPreviewPaths.root(), maximumBytes(), artifact);
        return new CachedPreview(descriptor, artifact);
    }

    public synchronized void clear(String serverIdentity) throws IOException {
        deleteOwnedRoot(ClientAssetPreviewPaths.serverRoot(serverIdentity));
    }

    public synchronized void clearAll() throws IOException {
        Path root = ClientAssetPreviewPaths.root();
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) return;
        try (var children = Files.list(root)) {
            for (Path child : children.filter(ClientAssetPreviewCache::isServerNamespace).toList()) {
                deleteOwnedRoot(child);
            }
        }
    }

    public synchronized long sizeAll() throws IOException {
        return AssetPreviewCacheMaintenance.size(ClientAssetPreviewPaths.root());
    }

    public synchronized Path location() {
        return ClientAssetPreviewPaths.root();
    }

    public synchronized void enforceConfiguredLimit() throws IOException {
        AssetPreviewCacheMaintenance.enforceLimit(ClientAssetPreviewPaths.root(), maximumBytes(), null);
    }

    private static long maximumBytes() {
        return ConfigManager.INSTANCE.getConfig().previewCache.maxSizeMiB * 1024L * 1024L;
    }

    private static Path metadataPath(Path root, String cacheKey) {
        String key = AssetPreviewCachePaths.validateCacheKey(cacheKey);
        Path base = root.toAbsolutePath().normalize().resolve("metadata");
        Path path = base.resolve(key.substring(0, 2)).resolve(key + ".bin").normalize();
        if (!path.startsWith(base)) throw new IllegalArgumentException("Preview metadata escapes cache root");
        return path;
    }

    private static void writeMetadata(Path target, AssetPreviewDescriptor descriptor) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            output.writeInt(METADATA_MAGIC);
            output.writeUTF(descriptor.revision().identity().remotePath());
            output.writeInt(descriptor.revision().identity().kind().ordinal());
            output.writeLong(descriptor.revision().sourceSize());
            output.writeLong(descriptor.revision().sourceLastModified());
            output.writeInt(descriptor.revision().formatVersion());
            output.writeInt(descriptor.format().ordinal());
            output.writeInt(descriptor.width());
            output.writeInt(descriptor.height());
            output.writeInt(descriptor.encodedBytes());
            output.writeUTF(descriptor.sha256());
        }
        atomicMove(temporary, target);
    }

    private static AssetPreviewDescriptor readMetadata(Path source) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
            if (input.readInt() != METADATA_MAGIC) throw new IOException("Invalid nativepreview metadata");
            String remotePath = input.readUTF();
            int kind = input.readInt();
            long size = input.readLong();
            long modified = input.readLong();
            int version = input.readInt();
            int format = input.readInt();
            if (kind < 0 || kind >= AssetPreviewKind.values().length
                    || format < 0 || format >= AssetPreviewFormat.values().length) {
                throw new IOException("Invalid nativepreview metadata enum");
            }
            AssetPreviewRevision revision = new AssetPreviewRevision(
                    new AssetPreviewIdentity(remotePath, AssetPreviewKind.values()[kind]), size, modified, version);
            return new AssetPreviewDescriptor(revision, AssetPreviewFormat.values()[format],
                    input.readInt(), input.readInt(), input.readInt(), input.readUTF());
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid nativepreview metadata", exception);
        }
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, content);
        atomicMove(temporary, target);
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteOwnedRoot(Path requestedRoot) throws IOException {
        Path owner = ClientAssetPreviewPaths.root();
        Path root = requestedRoot.toAbsolutePath().normalize();
        if (!root.startsWith(owner) || root.getNameCount() < owner.getNameCount()) {
            throw new IOException("Refusing to clear a path outside the nativepreview cache");
        }
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isSymbolicLink(path)) {
                    Files.deleteIfExists(path);
                } else {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static boolean isServerNamespace(Path path) {
        String name = path.getFileName() != null ? path.getFileName().toString() : "";
        return name.length() == 64 && name.chars().allMatch(character ->
                character >= '0' && character <= '9' || character >= 'a' && character <= 'f');
    }

    public record CachedPreview(AssetPreviewDescriptor descriptor, Path path) {
    }
}
