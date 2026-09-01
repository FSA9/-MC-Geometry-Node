package com.mine.geometry_node.core.engine.system.data.library;

import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.Set;

/** Server-authoritative access to the remote Data Library database. */
public final class RemoteDataLibraryService {
    public static final RemoteDataLibraryService INSTANCE = new RemoteDataLibraryService();

    private RemoteDataLibraryService() {
    }

    public DataLibraryLoadResult refresh(MinecraftServer server) throws IOException {
        return DataLibraryFileStore.read(
                ServerDataLibraryPaths.file(server), server.overworld().registryAccess());
    }

    public DataLibraryLoadResult create(MinecraftServer server, DataLibraryDocument incoming) throws IOException {
        DataLibraryEntryKey key = requireSingle(incoming);
        return update(server, document -> {
            if (document.find(key).isPresent()) {
                throw new IllegalStateException("Data Library entry already exists: " + key);
            }
            document.put(key.type(), incoming.find(key).orElseThrow());
        });
    }

    public DataLibraryLoadResult update(MinecraftServer server, DataLibraryDocument incoming) throws IOException {
        DataLibraryEntryKey key = requireSingle(incoming);
        return update(server, document -> {
            if (document.find(key).isEmpty()) {
                throw new IllegalStateException("Data Library entry does not exist: " + key);
            }
            document.put(key.type(), incoming.find(key).orElseThrow());
        });
    }

    public DataLibraryLoadResult delete(MinecraftServer server, Set<DataLibraryEntryKey> keys) throws IOException {
        return update(server, document -> document.removeAll(keys));
    }

    private DataLibraryLoadResult update(MinecraftServer server, DocumentMutation mutation) throws IOException {
        return DataLibraryFileStore.updateAtomic(
                ServerDataLibraryPaths.file(server), server.overworld().registryAccess(), document -> {
                    mutation.apply(document);
                    return document;
                });
    }

    private static DataLibraryEntryKey requireSingle(DataLibraryDocument document) {
        if (document.size() != 1) throw new IllegalArgumentException("Operation requires exactly one Data Library entry");
        return document.entriesByType().entrySet().stream()
                .flatMap(group -> group.getValue().keySet().stream()
                        .map(id -> new DataLibraryEntryKey(group.getKey(), id)))
                .findFirst().orElseThrow();
    }

    @FunctionalInterface private interface DocumentMutation { void apply(DataLibraryDocument document); }
}
