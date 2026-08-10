package com.mine.geometry_node.core.engine.system.asset.preview.store;

import com.mine.geometry_node.core.engine.system.asset.RemoteAssetFileService;
import com.mine.geometry_node.core.engine.system.asset.preview.*;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferHashing;
import net.minecraft.server.MinecraftServer;
import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferServerConfig;

import java.io.*;
import java.nio.file.*;
import java.util.Optional;

/** Persistent immutable preview artifacts owned by one server world. */
public final class ServerAssetPreviewStore {
    private static final int METADATA_MAGIC = 0x474E5052;

    public Optional<StoredPreview> find(MinecraftServer server, AssetPreviewRevision revision) throws IOException {
        Path root = RemoteAssetFileService.previewCacheRoot(server);
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

    public StoredPreview publish(MinecraftServer server, Path verifiedSource,
                                 AssetPreviewDescriptor descriptor) throws IOException {
        if (!Files.isRegularFile(verifiedSource) || Files.isSymbolicLink(verifiedSource)) {
            throw new IOException("Preview source is unavailable");
        }
        if (Files.size(verifiedSource) != descriptor.encodedBytes()
                || !AssetTransferHashing.sha256(verifiedSource).equals(descriptor.sha256())) {
            throw new IOException("Preview artifact does not match its descriptor");
        }
        Path root = RemoteAssetFileService.previewCacheRoot(server);
        Path artifact = AssetPreviewCachePaths.resolveArtifact(root.resolve("artifacts"),
                descriptor.revision().cacheKey(), descriptor.format());
        Files.createDirectories(artifact.getParent());
        atomicCopy(verifiedSource, artifact);
        writeMetadata(metadataPath(root, descriptor.revision().cacheKey()), descriptor);
        AssetPreviewCacheMaintenance.enforceLimit(root,
                AssetTransferServerConfig.previewCacheMaxBytes(), artifact);
        return new StoredPreview(descriptor, artifact);
    }

    public Optional<StoredPreview> copyAssociation(MinecraftServer server, AssetPreviewRevision sourceRevision,
                                                   AssetPreviewRevision targetRevision) throws IOException {
        Optional<StoredPreview> source = find(server, sourceRevision);
        if (source.isEmpty()) return Optional.empty();
        AssetPreviewDescriptor descriptor = source.get().descriptor();
        AssetPreviewDescriptor targetDescriptor = new AssetPreviewDescriptor(targetRevision, descriptor.format(),
                descriptor.width(), descriptor.height(), descriptor.encodedBytes(), descriptor.sha256());
        return Optional.of(publish(server, source.get().path(), targetDescriptor));
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
            out.writeInt(METADATA_MAGIC);
            out.writeUTF(descriptor.revision().identity().remotePath());
            out.writeInt(descriptor.revision().identity().kind().ordinal());
            out.writeLong(descriptor.revision().sourceSize());
            out.writeLong(descriptor.revision().sourceLastModified());
            out.writeInt(descriptor.revision().formatVersion());
            out.writeInt(descriptor.format().ordinal());
            out.writeInt(descriptor.width()); out.writeInt(descriptor.height());
            out.writeInt(descriptor.encodedBytes()); out.writeUTF(descriptor.sha256());
        }
        atomicMove(temporary, target);
    }

    private static AssetPreviewDescriptor readMetadata(Path source) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
            if (in.readInt() != METADATA_MAGIC) throw new IOException("Invalid preview metadata");
            String path = in.readUTF(); int kind = in.readInt(); long size = in.readLong(); long modified = in.readLong();
            int version = in.readInt(); int format = in.readInt();
            if (kind < 0 || kind >= AssetPreviewKind.values().length || format < 0 || format >= AssetPreviewFormat.values().length) {
                throw new IOException("Invalid preview metadata enum");
            }
            AssetPreviewRevision revision = new AssetPreviewRevision(
                    new AssetPreviewIdentity(path, AssetPreviewKind.values()[kind]), size, modified, version);
            return new AssetPreviewDescriptor(revision, AssetPreviewFormat.values()[format],
                    in.readInt(), in.readInt(), in.readInt(), in.readUTF());
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid preview metadata", exception);
        }
    }

    private static void atomicCopy(Path source, Path target) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
        atomicMove(temporary, target);
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    public record StoredPreview(AssetPreviewDescriptor descriptor, Path path) {}
}
