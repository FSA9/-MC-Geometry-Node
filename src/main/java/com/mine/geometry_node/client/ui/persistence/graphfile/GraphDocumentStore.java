package com.mine.geometry_node.client.ui.persistence.graphfile;

import com.mine.geometry_node.client.asset.file.AssetFileOperations;
import com.mine.geometry_node.client.asset.file.AssetFileTransactionManager;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Single read/write gateway for graph documents.
 */
public final class GraphDocumentStore {
    public static final GraphDocumentStore INSTANCE = new GraphDocumentStore();

    private static final int FILE_LOCK_COUNT = 64;
    private final ReentrantLock[] mFileLocks = new ReentrantLock[FILE_LOCK_COUNT];

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
        return withReference(reference, path -> reference.claimDocument(expectedRevision));
    }

    public void writeStringAtomic(GraphFileReference reference, String content) throws IOException {
        writeStringAtomic(reference, content, StandardCharsets.UTF_8);
    }

    public void writeStringAtomic(GraphFileReference reference, String content, Charset charset) throws IOException {
        withReference(reference, path -> {
            reference.requireDocumentClaimed();
            writeStringAtomicLocked(path, content, charset);
            reference.contentChanged();
            return null;
        });
    }

    public void updateStringAtomic(GraphFileReference reference, Charset charset, StringUpdater updater)
            throws IOException {
        withReference(reference, path -> {
            reference.requireDocumentUnclaimed();
            String current = Files.exists(path) ? Files.readString(path, charset) : "";
            writeStringAtomicLocked(path, updater.update(current), charset);
            reference.contentChanged();
            return null;
        });
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
