package com.mine.geometry_node.client.model.asset;

import com.mine.geometry_node.client.asset.preview.ClientAssetPreviewPaths;
import com.mine.geometry_node.core.engine.graph.storage.GraphPathMapper;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferHashing;
import com.mine.geometry_node.core.engine.system.asset.preview.store.AssetPreviewCacheMaintenance;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.core.engine.system.model.api.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.UUID;

final class ClientModelAssetCache {
    Optional<MaterializedModelAsset> find(String serverIdentity, RemoteModelAssetRevision revision) throws IOException {
        Path model = modelPath(serverIdentity, revision);
        Path marker = markerPath(model);
        if (!Files.isRegularFile(model) || Files.isSymbolicLink(model)
                || Files.size(model) != revision.sourceSize()
                || !Files.isRegularFile(marker) || Files.isSymbolicLink(marker)) {
            return Optional.empty();
        }
        String markerValue = Files.readString(marker, StandardCharsets.UTF_8);
        String expectedPrefix = revision.canonical() + '\0';
        if (!markerValue.startsWith(expectedPrefix)) return Optional.empty();
        String sha256 = markerValue.substring(expectedPrefix.length());
        if (!sha256.equals(AssetTransferHashing.sha256(model))) {
            return Optional.empty();
        }
        Files.setLastModifiedTime(model, FileTime.fromMillis(System.currentTimeMillis()));
        return Optional.of(materialized(serverIdentity, revision, model, sha256));
    }

    Path staging(String serverIdentity, UUID token) throws IOException {
        Path serverRoot = ClientAssetPreviewPaths.serverRoot(serverIdentity);
        Path staging = serverRoot.resolve("model-staging")
                .resolve(token + ".glb").normalize();
        createSecureDirectories(serverRoot, staging.getParent());
        return staging;
    }

    String validate(Path staging, RemoteModelAssetRevision revision) throws IOException {
        if (!Files.isRegularFile(staging) || Files.isSymbolicLink(staging) || Files.size(staging) != revision.sourceSize()) {
            throw new IOException("downloaded GLB does not match remote revision size");
        }
        return AssetTransferHashing.sha256(staging);
    }

    MaterializedModelAsset publish(Path staging, String serverIdentity, RemoteModelAssetRevision revision, String sha256) throws IOException {
        Path model = modelPath(serverIdentity, revision);
        Path serverRoot = ClientAssetPreviewPaths.serverRoot(serverIdentity);
        createSecureDirectories(serverRoot, model.getParent());
        try {
            Files.move(staging, model, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(staging, model, StandardCopyOption.REPLACE_EXISTING);
        }
        Path marker = markerPath(model);
        Files.createDirectories(marker.getParent());
        Path temporary = marker.resolveSibling(marker.getFileName() + ".tmp");
        Files.writeString(temporary, revision.canonical() + '\0' + sha256, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, marker, StandardCopyOption.REPLACE_EXISTING);
        }
        AssetPreviewCacheMaintenance.enforceLimit(ClientAssetPreviewPaths.root(), maximumBytes(), model);
        return materialized(serverIdentity, revision, model, sha256);
    }

    void discard(Path staging) throws IOException { if (staging != null) Files.deleteIfExists(staging); }

    void invalidate(String serverIdentity, java.util.Collection<String> paths) throws IOException {
        Path root = modelsRoot(serverIdentity);
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) return;
        java.util.List<String> normalized = paths.stream()
                .map(path -> GraphPathMapper.normalizeRelativePath(path, false)).filter(path -> !path.isEmpty()).toList();
        try (var markers = Files.walk(root)) {
            for (Path marker : markers.filter(path -> path.getFileName().toString().endsWith(".revision")).toList()) {
                String content;
                try { content = Files.readString(marker, StandardCharsets.UTF_8); }
                catch (IOException ignored) { continue; }
                String remotePath = content.split("\0", -1)[0];
                if (normalized.stream().noneMatch(path -> remotePath.equals(path) || remotePath.startsWith(path + "/"))) continue;
                Files.deleteIfExists(marker);
                Files.deleteIfExists(modelForMarker(marker));
            }
        }
    }

    void clear(String serverIdentity) throws IOException { deleteTree(modelsRoot(serverIdentity)); }
    void clearStaging(String serverIdentity) throws IOException {
        deleteTree(ClientAssetPreviewPaths.serverRoot(serverIdentity).resolve("model-staging").normalize());
    }
    void clearAll() throws IOException {
        clearAllUnder(ClientAssetPreviewPaths.root());
    }

    static void clearAllUnder(Path root) throws IOException {
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) return;
        try (var servers = Files.list(root)) {
            for (Path server : servers.filter(ClientAssetPreviewPaths::isServerNamespace).toList()) {
                deleteTree(server.resolve("model-assets"));
                deleteTree(server.resolve("model-staging"));
            }
        }
    }

    private static Path modelPath(String serverIdentity, RemoteModelAssetRevision revision) {
        String key = AssetTransferHashing.toHex(AssetTransferHashing.newSha256().digest(
                revision.canonical().getBytes(StandardCharsets.UTF_8)));
        return modelsRoot(serverIdentity).resolve(key.substring(0, 2)).resolve(key + ".glb").normalize();
    }
    private static Path modelsRoot(String serverIdentity) {
        return ClientAssetPreviewPaths.serverRoot(serverIdentity).resolve("model-assets").normalize();
    }
    private static Path markerPath(Path model) { return model.resolveSibling(model.getFileName() + ".revision"); }
    private static Path modelForMarker(Path marker) {
        String name = marker.getFileName().toString();
        return marker.resolveSibling(name.substring(0, name.length() - ".revision".length()));
    }
    private static void deleteTree(Path root) throws IOException {
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void createSecureDirectories(Path trustedRoot, Path directory) throws IOException {
        Path root = trustedRoot.toAbsolutePath().normalize();
        Path target = directory.toAbsolutePath().normalize();
        if (!target.startsWith(root)) throw new IOException("model cache path escapes its server root");
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("model cache server root is not a real directory: " + root);
            }
        } else {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("model cache server root is not a real directory: " + root);
            }
        }
        Path current = root;
        for (Path segment : root.relativize(target)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("model cache directory is not a real directory: " + current);
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    private static MaterializedModelAsset materialized(String serverIdentity, RemoteModelAssetRevision revision,
                                                       Path model, String sha256) {
        ModelAssetReference reference = new ModelAssetReference(ModelSourceKind.REMOTE, serverIdentity,
                revision.remotePath(), new ModelAssetRevision(
                revision.sourceSize(), revision.sourceLastModified(), sha256));
        return new MaterializedModelAsset(reference, model);
    }

    private static long maximumBytes() {
        return ConfigManager.INSTANCE.getConfig().previewCache.maxSizeMiB * 1024L * 1024L;
    }
}
