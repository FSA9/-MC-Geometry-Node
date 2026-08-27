package com.mine.geometry_node.client.ui.session;

import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.persistence.graphfile.GraphDocumentStore;
import com.mine.geometry_node.client.ui.persistence.graphfile.GraphFileReference;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class DocumentManager {
    public static final DocumentManager INSTANCE = new DocumentManager();

    private final List<GraphSession> mSessions = new ArrayList<>();
    private GraphSession mActiveSession = null;
    private GraphSession mLastOpenedSession = null;
    private long mOpenSessionSerial = 0L;

    private final List<Runnable> mOnTabChangedListeners = new ArrayList<>();
    private final List<Consumer<GraphSession>> mOnSessionSavedListeners = new ArrayList<>();

    private DocumentManager() {}

    public void setOnTabChangedListener(Runnable listener) {
        mOnTabChangedListeners.clear();
        if (listener != null) {
            mOnTabChangedListeners.add(listener);
        }
    }

    public void addOnTabChangedListener(Runnable listener) {
        if (listener != null && !mOnTabChangedListeners.contains(listener)) {
            mOnTabChangedListeners.add(listener);
        }
    }

    public void removeOnTabChangedListener(Runnable listener) {
        mOnTabChangedListeners.remove(listener);
    }

    public void addOnSessionSavedListener(Consumer<GraphSession> listener) {
        if (listener != null && !mOnSessionSavedListeners.contains(listener)) {
            mOnSessionSavedListeners.add(listener);
        }
    }

    public void removeOnSessionSavedListener(Consumer<GraphSession> listener) {
        mOnSessionSavedListeners.remove(listener);
    }

    public List<GraphSession> getSessions() {
        return mSessions;
    }

    public GraphSession getActiveSession() {
        return mActiveSession;
    }

    public GraphSession getLastOpenedSession() {
        return mLastOpenedSession;
    }

    public long getOpenSessionSerial() {
        return mOpenSessionSerial;
    }

    // 打开或新建一个图纸
    public void openSession(GraphSession session) {
        if (session == null || session.fileReference().isDeleted()) return;
        for (GraphSession s : mSessions) {
            if (s.fileReference() == session.fileReference()) {
                markSessionOpened(s);
                switchSession(s);
                return;
            }
        }
        mSessions.add(session);
        markSessionOpened(session);
        switchSession(session);
    }

    public void switchSession(GraphSession session) {
        if (mSessions.contains(session)) {
            mActiveSession = session;
            notifyTabChanged();
        }
    }

    public void closeSession(GraphSession session) {
        if (session == null) return;
        mSessions.remove(session);
        session.close();
        if (mActiveSession == session) {
            mActiveSession = mSessions.isEmpty() ? null : mSessions.get(mSessions.size() - 1);
        }
        notifyTabChanged();
    }

    public int closeSessionsUnder(List<Path> deletionTargets) {
        List<Path> targetPaths = normalizePaths(deletionTargets);
        if (targetPaths.isEmpty()) {
            return 0;
        }

        List<GraphSession> closingSessions = new ArrayList<>();
        for (GraphSession session : mSessions) {
            Path sessionPath = normalizePath(session.filePath());
            if (sessionPath != null && isWithinAnyTarget(sessionPath, targetPaths)) {
                closingSessions.add(session);
            }
        }
        if (closingSessions.isEmpty()) {
            return 0;
        }

        mSessions.removeAll(closingSessions);
        for (GraphSession session : closingSessions) {
            session.close();
        }
        if (closingSessions.contains(mActiveSession)) {
            mActiveSession = mSessions.isEmpty() ? null : mSessions.get(mSessions.size() - 1);
        }
        if (closingSessions.contains(mLastOpenedSession)) {
            mLastOpenedSession = null;
        }
        notifyTabChanged();
        return closingSessions.size();
    }

    public GraphSession findSession(GraphFileReference reference) {
        if (reference == null) return null;
        for (GraphSession session : mSessions) {
            if (session.fileReference() == reference) return session;
        }
        return null;
    }

    public void refreshFileReferences() {
        List<GraphSession> deleted = new ArrayList<>();
        for (GraphSession session : mSessions) {
            if (session.fileReference().isDeleted()) {
                deleted.add(session);
            } else {
                session.refreshTabName();
            }
        }
        if (!deleted.isEmpty()) {
            mSessions.removeAll(deleted);
            for (GraphSession session : deleted) {
                session.close();
            }
            if (deleted.contains(mActiveSession)) {
                mActiveSession = mSessions.isEmpty() ? null : mSessions.get(mSessions.size() - 1);
            }
            if (deleted.contains(mLastOpenedSession)) {
                mLastOpenedSession = null;
            }
        }
        notifyTabChanged();
    }

    public void moveSession(int fromIndex, int toIndex) {
        if (fromIndex >= 0 && fromIndex < mSessions.size() && toIndex >= 0 && toIndex < mSessions.size()) {
            // 交换位置
            GraphSession session = mSessions.remove(fromIndex);
            mSessions.add(toIndex, session);
            // 触发 UI 刷新
            notifyTabChanged();
        }
    }

    public boolean saveSession(GraphSession session) {
        if (session == null) return false;
        try {
            // 1. 序列化当前图纸
            String json = GraphJsonIO.toJson(session.editorContext.getGraph());

            // 2. 写入文件 (继承你之前在 GraphViewportPanel 中的逻辑)
            GraphDocumentStore.INSTANCE.writeStringAtomic(session.fileReference(), json);

            // 3. 清除脏标记
            session.isDirty = false;

            // 4. Notify file-backed consumers before refreshing tab state.
            notifySessionSaved(session);

            // 5. 触发 UI 刷新 (消除 Tab 上的星号)
            notifyTabChanged();

            System.out.println("[DocumentManager] Save Success: " + session.filePath());
            return true;
        } catch (Exception e) {
            System.err.println("[DocumentManager] Save Failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void notifyTabChanged() {
        List<Runnable> listeners = List.copyOf(mOnTabChangedListeners);
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private void notifySessionSaved(GraphSession session) {
        List<Consumer<GraphSession>> listeners = List.copyOf(mOnSessionSavedListeners);
        for (Consumer<GraphSession> listener : listeners) {
            listener.accept(session);
        }
    }

    private void markSessionOpened(GraphSession session) {
        mLastOpenedSession = session;
        mOpenSessionSerial++;
    }

    private static List<Path> normalizePaths(List<Path> sourcePaths) {
        List<Path> paths = new ArrayList<>();
        if (sourcePaths == null) {
            return paths;
        }
        for (Path sourcePath : sourcePaths) {
            if (sourcePath == null) {
                continue;
            }
            Path path = normalizePath(sourcePath);
            if (path != null && !paths.contains(path)) {
                paths.add(path);
            }
        }
        return paths;
    }

    private static Path normalizePath(Path path) {
        if (path == null) {
            return null;
        }
        try {
            return path.toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isWithinAnyTarget(Path sessionPath, List<Path> targetPaths) {
        for (Path targetPath : targetPaths) {
            if (sessionPath.equals(targetPath) || sessionPath.startsWith(targetPath)) {
                return true;
            }
        }
        return false;
    }
}
