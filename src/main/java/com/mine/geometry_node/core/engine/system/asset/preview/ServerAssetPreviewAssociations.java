package com.mine.geometry_node.core.engine.system.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.AssetTypeCatalog;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetMutationRegistry;
import com.mine.geometry_node.core.engine.system.asset.ServerAssetPaths;
import com.mine.geometry_node.core.engine.system.asset.preview.store.ServerAssetPreviewStore;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/** Captures immutable nativepreview revisions before asset copy/move and rekeys them after the file operation. */
public final class ServerAssetPreviewAssociations {
    private static final ServerAssetPreviewStore STORE = new ServerAssetPreviewStore();
    private static boolean initialized;

    private ServerAssetPreviewAssociations() {
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        RemoteAssetMutationRegistry.INSTANCE.register(ServerAssetPreviewAssociations::prepare);
    }

    private static RemoteAssetMutationRegistry.PreparedMutation prepare(
            MinecraftServer server,
            RemoteAssetMutationRegistry.Operation operation,
            Path source,
            Path target
    ) throws IOException {
        if (target == null || (operation != RemoteAssetMutationRegistry.Operation.COPY
                && operation != RemoteAssetMutationRegistry.Operation.MOVE
                && operation != RemoteAssetMutationRegistry.Operation.RENAME)) {
            return RemoteAssetMutationRegistry.PreparedMutation.NONE;
        }
        Migration migration = capture(server, source, target);
        return new RemoteAssetMutationRegistry.PreparedMutation() {
            @Override
            public void commit() {
                migration.apply();
            }
        };
    }

    public static Migration capture(MinecraftServer server, Path source, Path target) throws IOException {
        Path root = ServerAssetPaths.root(server);
        List<Entry> entries = new ArrayList<>();
        if (Files.isRegularFile(source) && !Files.isSymbolicLink(source)) {
            captureFile(root, source, target, entries);
        } else if (Files.isDirectory(source) && !Files.isSymbolicLink(source)) {
            try (var paths = Files.walk(source)) {
                for (Path file : paths.filter(Files::isRegularFile).filter(path -> !Files.isSymbolicLink(path)).toList()) {
                    captureFile(root, file, target.resolve(source.relativize(file)), entries);
                }
            }
        }
        return new Migration(server, List.copyOf(entries));
    }

    private static void captureFile(Path root, Path source, Path target, List<Entry> entries) throws IOException {
        AssetPreviewKind kind = AssetTypeCatalog.previewKind(AssetTypeCatalog.inspect(source).typeId());
        if (!kind.isConcrete()) return;
        BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);
        String relative = root.relativize(source.toAbsolutePath().normalize()).toString().replace('\\', '/');
        AssetPreviewRevision revision = AssetPreviewRevision.current(
                new AssetPreviewIdentity(relative, kind), attributes.size(), attributes.lastModifiedTime().toMillis());
        entries.add(new Entry(revision, target.toAbsolutePath().normalize(), kind));
    }

    public record Migration(MinecraftServer server, List<Entry> entries) {
        public void apply() {
            for (Entry entry : entries) {
                try {
                    if (!Files.isRegularFile(entry.target) || Files.isSymbolicLink(entry.target)) continue;
                    BasicFileAttributes attributes = Files.readAttributes(entry.target, BasicFileAttributes.class);
                    Path root = ServerAssetPaths.root(server);
                    String relative = root.relativize(entry.target).toString().replace('\\', '/');
                    AssetPreviewRevision targetRevision = AssetPreviewRevision.current(
                            new AssetPreviewIdentity(relative, entry.kind), attributes.size(),
                            attributes.lastModifiedTime().toMillis());
                    STORE.copyAssociation(server, entry.sourceRevision, targetRevision);
                } catch (IOException | RuntimeException exception) {
                    System.err.println("[AssetPreview] Failed to migrate nativepreview for " + entry.target
                            + ": " + exception.getMessage());
                }
            }
        }
    }

    public record Entry(AssetPreviewRevision sourceRevision, Path target, AssetPreviewKind kind) {
    }
}
