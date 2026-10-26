package com.mine.geometry_node.client.feature.graph.document;

import com.mine.geometry_node.client.feature.graph.persistence.GraphJsonIO;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphDocumentStore;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphFileReference;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The single registry of open graph documents, confined to the editor owner thread. */
public final class GraphDocumentManager {
    private static final System.Logger LOGGER = System.getLogger(GraphDocumentManager.class.getName());
    public static final GraphDocumentManager INSTANCE = new GraphDocumentManager();

    private final List<GraphSession> sessions = new ArrayList<>();
    private final List<Runnable> changed = new ArrayList<>();
    private final List<Consumer<GraphSession>> saved = new ArrayList<>();

    private GraphDocumentManager() {}

    /** Immutable membership/order snapshot; registration and release go through the owner APIs. */
    public List<GraphSession> getSessions() { return List.copyOf(sessions); }

    /** Registers once by file identity; the caller still owns any redundant loaded session. */
    public GraphSession openSession(GraphSession session) {
        if (session == null || session.fileReference().isDeleted()) return null;
        GraphSession existing = findSession(session.fileReference());
        if (existing != null) return existing;
        sessions.add(session);
        notifyChanged();
        return session;
    }

    public GraphSession findSession(GraphFileReference reference) {
        if (reference == null) return null;
        for (GraphSession session : sessions) {
            if (session.fileReference() == reference) return session;
        }
        return null;
    }

    public void closeSession(GraphSession session) {
        if (session == null) return;
        sessions.remove(session);
        session.close();
        notifyChanged();
    }

    /** Revalidates the exact revision on the same owner thread that commits graph edits. */
    public boolean closeSessionIfSaved(GraphSession session, long savedRevision) {
        if (session == null || !sessions.contains(session)) return false;
        var commands = session.editorContext.getCommandManager();
        if (!commands.canSave() || commands.historyState().dirty()
                || commands.revision() != savedRevision) return false;
        closeSession(session);
        return true;
    }

    public int closeSessionsUnder(List<Path> deletionTargets) {
        List<Path> targets = new ArrayList<>();
        if (deletionTargets != null) for (Path target : deletionTargets) {
            Path normalized = normalizePath(target);
            if (normalized != null && !targets.contains(normalized)) targets.add(normalized);
        }
        if (targets.isEmpty()) return 0;
        List<GraphSession> closing = new ArrayList<>();
        for (GraphSession session : sessions) {
            Path path = normalizePath(session.filePath());
            if (path != null && targets.stream().anyMatch(path::startsWith)) closing.add(session);
        }
        if (closing.isEmpty()) return 0;
        sessions.removeAll(closing);
        for (GraphSession session : closing) session.close();
        notifyChanged();
        return closing.size();
    }

    public void refreshFileReferences() {
        List<GraphSession> deleted = sessions.stream()
                .filter(session -> session.fileReference().isDeleted()).toList();
        sessions.removeAll(deleted);
        for (GraphSession session : deleted) session.close();
        notifyChanged();
    }

    /** Reorders the registry's presentation without creating a second membership authority. */
    public void moveSession(int fromIndex, int toIndex) {
        if (fromIndex >= 0 && fromIndex < sessions.size() && toIndex >= 0 && toIndex < sessions.size()) {
            sessions.add(toIndex, sessions.remove(fromIndex));
            notifyChanged();
        }
    }

    /**
     * False means persistence did not complete. After writing, history is marked saved before
     * observers run; ordinary observer failures are logged and do not change a successful save.
     * A failing history transition or fatal observer Error propagates instead of reporting an
     * already written document as an ordinary failed save.
     */
    public boolean saveSession(GraphSession session) {
        if (session == null || !session.editorContext.getCommandManager().canSave()) return false;
        try {
            String json = GraphJsonIO.toJson(session.editorContext.getGraph());
            GraphDocumentStore.INSTANCE.writeStringAtomic(session.fileReference(), json);
        } catch (Exception failure) {
            System.err.println("[DocumentManager] Save Failed: " + failure.getMessage());
            failure.printStackTrace();
            return false;
        }
        session.editorContext.getCommandManager().markSaved();
        for (Consumer<GraphSession> listener : List.copyOf(saved)) {
            try { listener.accept(session); }
            catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.WARNING, "Graph saved observer failed after file write", failure);
            }
        }
        notifyChanged();
        System.out.println("[DocumentManager] Save Success: " + session.filePath());
        return true;
    }

    public void addChangedListener(Runnable listener) {
        if (listener != null && !changed.contains(listener)) changed.add(listener);
    }

    public void removeChangedListener(Runnable listener) { changed.remove(listener); }

    public void addOnSessionSavedListener(Consumer<GraphSession> listener) {
        if (listener != null && !saved.contains(listener)) saved.add(listener);
    }

    public void removeOnSessionSavedListener(Consumer<GraphSession> listener) { saved.remove(listener); }

    public void notifyChanged() {
        for (Runnable listener : List.copyOf(changed)) {
            try { listener.run(); }
            catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.WARNING, "Graph document observer failed", failure);
            }
        }
    }

    private static Path normalizePath(Path path) {
        if (path == null) return null;
        try { return path.toAbsolutePath().normalize(); }
        catch (RuntimeException ignored) { return null; }
    }
}
