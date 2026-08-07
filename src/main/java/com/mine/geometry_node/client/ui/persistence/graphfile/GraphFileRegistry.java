package com.mine.geometry_node.client.ui.persistence.graphfile;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns graph-file identities and updates them together with filesystem mutations.
 */
public final class GraphFileRegistry {
    public static final GraphFileRegistry INSTANCE = new GraphFileRegistry();

    private final Map<Path, WeakReference<GraphFileReference>> mReferences = new HashMap<>();

    private GraphFileRegistry() {
    }

    public GraphFileReference reference(Path path) {
        Path normalized = GraphFileReference.normalize(path);
        return GraphDocumentStore.INSTANCE.withStructureRead(() -> {
            synchronized (this) {
                removeCollectedLocked();
                WeakReference<GraphFileReference> weak = mReferences.get(normalized);
                GraphFileReference existing = weak != null ? weak.get() : null;
                if (existing != null && !existing.isDeleted()) {
                    return existing;
                }
                GraphFileReference created = new GraphFileReference(normalized);
                mReferences.put(normalized, new WeakReference<>(created));
                return created;
            }
        });
    }

    public Mutation beginMove(Path source, Path destination) throws IOException {
        return beginMoves(Map.of(source, destination));
    }

    public Mutation beginMoves(Map<Path, Path> moves) throws IOException {
        List<Map.Entry<Path, Path>> normalizedMoves = new ArrayList<>();
        if (moves != null) {
            for (Map.Entry<Path, Path> move : moves.entrySet()) {
                if (move.getKey() != null && move.getValue() != null) {
                    normalizedMoves.add(Map.entry(
                            GraphFileReference.normalize(move.getKey()),
                            GraphFileReference.normalize(move.getValue())));
                }
            }
        }
        normalizedMoves.sort((first, second) -> Integer.compare(
                second.getKey().getNameCount(), first.getKey().getNameCount()));
        return begin(path -> {
            for (Map.Entry<Path, Path> move : normalizedMoves) {
                Path source = move.getKey();
                Path destination = move.getValue();
                if (path.equals(source)) {
                    return destination;
                }
                if (path.startsWith(source)) {
                    return destination.resolve(source.relativize(path)).normalize();
                }
            }
            return null;
        });
    }

    public Mutation beginDelete(List<Path> targets) throws IOException {
        List<Path> normalizedTargets = new ArrayList<>();
        if (targets != null) {
            for (Path target : targets) {
                if (target != null) normalizedTargets.add(GraphFileReference.normalize(target));
            }
        }
        return begin(path -> {
            for (Path target : normalizedTargets) {
                if (path.equals(target) || path.startsWith(target)) {
                    return Mutation.DELETED_PATH;
                }
            }
            return null;
        });
    }

    private Mutation begin(PathMapper mapper) throws IOException {
        return new Mutation(this, mapper);
    }

    private synchronized Map<GraphFileReference, Path> prepareLocked(PathMapper mapper) {
        removeCollectedLocked();
        Map<GraphFileReference, Path> changes = new LinkedHashMap<>();
        try {
            for (WeakReference<GraphFileReference> weak : mReferences.values()) {
                GraphFileReference reference = weak.get();
                if (reference == null || reference.isDeleted() || changes.containsKey(reference)) continue;
                Path destination = mapper.map(reference.path());
                if (destination != null) {
                    reference.beginMutation();
                    changes.put(reference, destination);
                }
            }
        } catch (RuntimeException e) {
            for (GraphFileReference reference : changes.keySet()) reference.rollbackMutation();
            throw e;
        }
        return changes;
    }

    private synchronized void commitLocked(Map<GraphFileReference, Path> changes) {
        for (Map.Entry<GraphFileReference, Path> change : changes.entrySet()) {
            if (change.getValue() == Mutation.DELETED_PATH) {
                change.getKey().commitDelete();
            } else {
                change.getKey().commitMove(change.getValue());
            }
        }
        rebuildLocked();
    }

    private synchronized void rollbackLocked(Map<GraphFileReference, Path> changes) {
        for (GraphFileReference reference : changes.keySet()) {
            reference.rollbackMutation();
        }
        rebuildLocked();
    }

    private void rebuildLocked() {
        Map<Path, WeakReference<GraphFileReference>> rebuilt = new HashMap<>();
        for (WeakReference<GraphFileReference> weak : mReferences.values()) {
            GraphFileReference reference = weak.get();
            if (reference != null && !reference.isDeleted()) {
                rebuilt.put(reference.path(), new WeakReference<>(reference));
            }
        }
        mReferences.clear();
        mReferences.putAll(rebuilt);
    }

    private void removeCollectedLocked() {
        mReferences.entrySet().removeIf(entry -> entry.getValue().get() == null);
    }

    @FunctionalInterface
    private interface PathMapper {
        Path map(Path path);
    }

    public static final class Mutation {
        private static final Path DELETED_PATH = Path.of("");

        private final GraphFileRegistry mRegistry;
        private final PathMapper mMapper;
        private boolean mFinished;

        private Mutation(GraphFileRegistry registry, PathMapper mapper) {
            mRegistry = registry;
            mMapper = mapper;
        }

        public void commit(GraphDocumentStore.IOCallable<Void> filesystemAction) throws IOException {
            if (mFinished) {
                throw new IllegalStateException("graph-file mutation already finished");
            }
            try {
                GraphDocumentStore.INSTANCE.withStructureMutation(() -> {
                    Map<GraphFileReference, Path> changes = mRegistry.prepareLocked(mMapper);
                    try {
                        filesystemAction.call();
                        mRegistry.commitLocked(changes);
                    } catch (IOException | RuntimeException e) {
                        mRegistry.rollbackLocked(changes);
                        throw e;
                    }
                    return null;
                });
            } finally {
                mFinished = true;
            }
        }

    }
}
