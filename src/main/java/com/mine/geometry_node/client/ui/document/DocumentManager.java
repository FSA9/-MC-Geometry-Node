package com.mine.geometry_node.client.ui.document;

import com.mine.geometry_node.client.feature.graph.document.GraphDocumentManager;
import com.mine.geometry_node.client.feature.graph.document.GraphSession;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphFileReference;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Tab activation and view state facade over the single application document registry. */
public final class DocumentManager {
    private static final System.Logger LOGGER = System.getLogger(DocumentManager.class.getName());
    public static final DocumentManager INSTANCE = new DocumentManager();
    private final GraphDocumentManager documents = GraphDocumentManager.INSTANCE;
    private GraphSession mActiveSession;
    private GraphSession mLastOpenedSession;
    private long mOpenSessionSerial;
    private boolean openingTab;
    private final List<Runnable> mOnTabChangedListeners = new ArrayList<>();

    private DocumentManager() { documents.addChangedListener(this::documentsChanged); }

    public void setOnTabChangedListener(Runnable listener) {
        mOnTabChangedListeners.clear();
        addOnTabChangedListener(listener);
    }

    public void addOnTabChangedListener(Runnable listener) {
        if (listener != null && !mOnTabChangedListeners.contains(listener)) mOnTabChangedListeners.add(listener);
    }

    public void removeOnTabChangedListener(Runnable listener) { mOnTabChangedListeners.remove(listener); }
    public void addOnSessionSavedListener(Consumer<GraphSession> listener) { documents.addOnSessionSavedListener(listener); }
    public void removeOnSessionSavedListener(Consumer<GraphSession> listener) { documents.removeOnSessionSavedListener(listener); }
    public List<GraphSession> getSessions() { return documents.getSessions(); }
    public GraphSession getActiveSession() { return mActiveSession; }
    public GraphSession getLastOpenedSession() { return mLastOpenedSession; }
    public long getOpenSessionSerial() { return mOpenSessionSerial; }

    public void openSession(GraphSession session) {
        GraphSession registered;
        openingTab = true;
        try { registered = documents.openSession(session); }
        finally { openingTab = false; }
        if (registered == null) return;
        mLastOpenedSession = registered;
        mOpenSessionSerial++;
        switchSession(registered);
    }

    public void switchSession(GraphSession session) {
        if (getSessions().contains(session)) {
            mActiveSession = session;
            notifyTabChanged();
        }
    }

    public void closeSession(GraphSession session) { documents.closeSession(session); }
    public boolean closeSessionIfSaved(GraphSession session, long savedRevision) {
        return documents.closeSessionIfSaved(session, savedRevision);
    }
    public int closeSessionsUnder(List<Path> targets) { return documents.closeSessionsUnder(targets); }
    public GraphSession findSession(GraphFileReference reference) { return documents.findSession(reference); }
    public void refreshFileReferences() {
        for (GraphSession session : getSessions()) {
            if (!session.fileReference().isDeleted()) GraphSessionViewState.of(session).refreshTabName(session);
        }
        documents.refreshFileReferences();
    }
    public void moveSession(int fromIndex, int toIndex) { documents.moveSession(fromIndex, toIndex); }
    public boolean saveSession(GraphSession session) { return documents.saveSession(session); }

    public void notifyTabChanged() {
        for (Runnable listener : List.copyOf(mOnTabChangedListeners)) {
            try { listener.run(); }
            catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.WARNING, "Graph tab observer failed", failure);
            }
        }
    }

    private void documentsChanged() {
        if (openingTab) return;
        GraphSessionViewState.discardClosed();
        for (GraphSession session : getSessions()) GraphSessionViewState.of(session).refreshFileReference(session);
        if (mActiveSession != null && !getSessions().contains(mActiveSession)) {
            GraphSessionViewState.forget(mActiveSession);
            mActiveSession = getSessions().isEmpty() ? null : getSessions().getLast();
        }
        if (mLastOpenedSession != null && !getSessions().contains(mLastOpenedSession)) {
            GraphSessionViewState.forget(mLastOpenedSession);
            mLastOpenedSession = null;
        }
        notifyTabChanged();
    }
}
