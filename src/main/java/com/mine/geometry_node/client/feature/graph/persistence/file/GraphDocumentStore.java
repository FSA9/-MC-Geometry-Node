package com.mine.geometry_node.client.feature.graph.persistence.file;

import com.mine.geometry_node.client.feature.asset.file.AssetFileOperations;
import com.mine.geometry_node.client.feature.asset.file.AssetFileTransactionManager;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.Objects;

/**
 * Single read/write gateway for graph documents.
 */
public final class GraphDocumentStore {
    public static final GraphDocumentStore INSTANCE = new GraphDocumentStore();

    private static final int FILE_LOCK_COUNT = 64;
    private final ReentrantLock[] mFileLocks = new ReentrantLock[FILE_LOCK_COUNT];
    // Reads share the striped locks, so lock hold counts cannot distinguish a nested write from a read.
    private final ThreadLocal<Set<Path>> mMutatingPaths = new ThreadLocal<>();

    private GraphDocumentStore() {
        for (int i = 0; i < mFileLocks.length; i++) {
            mFileLocks[i] = new ReentrantLock();
        }
    }

    public String readString(Path path) throws IOException {
        return readString(path, StandardCharsets.UTF_8);
    }

    public String readString(Path path, Charset charset) throws IOException {
        return withPath(path, resolved -> Files.exists(resolved) ? Files.readString(resolved, charset) : "");
    }

    public String readString(GraphFileReference reference) throws IOException {
        return readString(reference, StandardCharsets.UTF_8);
    }

    public String readString(GraphFileReference reference, Charset charset) throws IOException {
        return withReference(reference,
                resolved -> Files.exists(resolved) ? Files.readString(resolved, charset) : "");
    }

    public ReadSnapshot readSnapshot(GraphFileReference reference) throws IOException {
        return withReference(reference, path -> new ReadSnapshot(
                Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "",
                reference.revision()));
    }

    public boolean claimDocument(GraphFileReference reference, long expectedRevision) throws IOException {
        return withMutation(reference, path -> reference.claimDocument(expectedRevision));
    }

    public void writeStringAtomic(GraphFileReference reference, String content) throws IOException {
        writeStringAtomic(reference, content, StandardCharsets.UTF_8);
    }

    public void writeStringAtomic(GraphFileReference reference, String content, Charset charset) throws IOException {
        withMutation(reference, path -> {
            reference.requireDocumentClaimed();
            writeStringAtomicLocked(path, content, charset);
            reference.contentChanged();
            return null;
        });
    }

    /** Creates or replaces a file-owned draft without bypassing an open document's ownership. */
    public void writeUnclaimedStringAtomic(GraphFileReference reference, String content) throws IOException {
        Objects.requireNonNull(content, "content");
        withMutation(reference, path -> {
            reference.requireDocumentUnclaimed();
            writeStringAtomicLocked(path, content, StandardCharsets.UTF_8);
            reference.contentChanged();
            return null;
        });
    }

    public void updateStringAtomic(GraphFileReference reference, Charset charset, StringUpdater updater)
            throws IOException {
        updateStringAtomicIfChanged(reference, charset, updater);
    }

    /** Reads an explicitly file-owned target; an open document must be accessed through its session. */
    public String readUnclaimedString(GraphFileReference reference) throws IOException {
        return withReference(reference, path -> {
            reference.requireDocumentUnclaimed();
            requireExistingFile(path);
            return Files.readString(path, StandardCharsets.UTF_8);
        });
    }

    /** No-op transformations do not write, advance the revision, or invalidate an opening snapshot. */
    public boolean updateStringAtomicIfChanged(GraphFileReference reference, Charset charset, StringUpdater updater)
            throws IOException {
        Objects.requireNonNull(updater, "updater");
        return withMutation(reference, path -> {
            reference.requireDocumentUnclaimed();
            requireExistingFile(path);
            String current = Files.readString(path, charset);
            String updated = Objects.requireNonNull(updater.update(current), "updated content");
            if (current.equals(updated)) return false;
            writeStringAtomicLocked(path, updated, charset);
            reference.contentChanged();
            return true;
        });
    }

    private <T> T withMutation(GraphFileReference reference, PathCallable<T> action) throws IOException {
        return withReference(reference, path -> {
            Set<Path> active = mMutatingPaths.get();
            if (active == null) {
                active = new HashSet<>();
                mMutatingPaths.set(active);
            }
            if (!active.add(path)) throw new IllegalStateException("Graph file mutation is already in progress: " + path);
            try { return action.call(path); }
            finally {
                active.remove(path);
                if (active.isEmpty()) mMutatingPaths.remove();
            }
        });
    }

    private static void requireExistingFile(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Graph metadata requires an existing regular file: " + path);
        }
    }

    public <T> T withStructureMutation(IOCallable<T> action) throws IOException {
        return AssetFileTransactionManager.INSTANCE.mutateStructure(action::call);
    }

    public <T> T withStructureRead(Supplier<T> action) {
        return AssetFileTransactionManager.INSTANCE.readStructure(action);
    }

    private <T> T withPath(Path path, PathCallable<T> action) throws IOException {
        Path normalized = GraphFileReference.normalize(path);
        return AssetFileTransactionManager.INSTANCE.readStructureIO(() -> {
            ReentrantLock fileLock = fileLock(normalized);
            fileLock.lock();
            try {
                return action.call(normalized);
            } finally {
                fileLock.unlock();
            }
        });
    }

    public <T> T withReference(GraphFileReference reference, PathCallable<T> action) throws IOException {
        if (reference == null) {
            throw new IllegalArgumentException("reference must not be null");
        }
        return AssetFileTransactionManager.INSTANCE.readStructureIO(() -> {
            Path path = reference.requireActivePath();
            ReentrantLock fileLock = fileLock(path);
            fileLock.lock();
            try {
                return action.call(reference.requireActivePath());
            } finally {
                fileLock.unlock();
            }
        });
    }

    private void writeStringAtomicLocked(Path target, String content, Charset charset) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = AssetFileOperations.siblingTemporary(target, "write");
        boolean committed = false;
        try {
            Files.writeString(temporary, content != null ? content : "", charset);
            AssetFileOperations.moveReplacing(temporary, target);
            committed = true;
        } finally {
            if (!committed) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private ReentrantLock fileLock(Path path) {
        int index = (path.hashCode() & Integer.MAX_VALUE) % mFileLocks.length;
        return mFileLocks[index];
    }

    @FunctionalInterface
    public interface IOCallable<T> {
        T call() throws IOException;
    }

    @FunctionalInterface
    private interface PathCallable<T> {
        T call(Path path) throws IOException;
    }

    @FunctionalInterface
    public interface StringUpdater {
        String update(String current) throws IOException;
    }

    public record ReadSnapshot(String content, long revision) {
    }

}
