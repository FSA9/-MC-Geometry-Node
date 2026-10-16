package com.mine.geometry_node.client.graph.persistence.file;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Stable identity for a graph file whose physical path may change.
 */
public final class GraphFileReference {
    enum State {
        ACTIVE,
        MUTATING,
        DELETED
    }

    private volatile Path mPath;
    private volatile State mState = State.ACTIVE;
    private long mRevision;
    private boolean mDocumentClaimed;

    GraphFileReference(Path path) {
        mPath = normalize(path);
    }

    public Path path() {
        return mPath;
    }

    public Path requireActivePath() {
        State state = mState;
        if (state == State.DELETED) {
            throw new IllegalStateException("graph file has been deleted");
        }
        if (state == State.MUTATING) {
            throw new IllegalStateException("graph file path is being changed");
        }
        return mPath;
    }

    public boolean isDeleted() {
        return mState == State.DELETED;
    }

    synchronized long revision() {
        return mRevision;
    }

    synchronized boolean claimDocument(long expectedRevision) {
        if (mDocumentClaimed) {
            throw new IllegalStateException("graph file already has an active document");
        }
        if (mRevision != expectedRevision) return false;
        mDocumentClaimed = true;
        return true;
    }

    public synchronized void releaseDocument() {
        mDocumentClaimed = false;
    }

    synchronized void requireDocumentClaimed() {
        if (!mDocumentClaimed) {
            throw new IllegalStateException("graph file has no active document owner");
        }
    }

    synchronized void requireDocumentUnclaimed() {
        if (mDocumentClaimed) {
            throw new IllegalStateException("graph file is owned by an open document");
        }
    }

    synchronized void contentChanged() {
        mRevision++;
    }

    void beginMutation() {
        if (mState != State.ACTIVE) {
            throw new IllegalStateException("graph file is not available for mutation");
        }
        mState = State.MUTATING;
    }

    void commitMove(Path path) {
        mPath = normalize(path);
        mState = State.ACTIVE;
    }

    void commitDelete() {
        releaseDocument();
        mState = State.DELETED;
    }

    synchronized MutationSnapshot mutationSnapshot() {
        return new MutationSnapshot(mPath, mState, mDocumentClaimed);
    }

    synchronized void restoreMutation(MutationSnapshot snapshot) {
        mPath = snapshot.path();
        mState = snapshot.state();
        mDocumentClaimed = snapshot.documentClaimed();
    }

    record MutationSnapshot(Path path, State state, boolean documentClaimed) {
    }

    static Path normalize(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }
}
