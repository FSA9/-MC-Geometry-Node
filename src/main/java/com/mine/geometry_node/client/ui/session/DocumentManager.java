package com.mine.geometry_node.client.ui.session;

import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DocumentManager {
    public static final DocumentManager INSTANCE = new DocumentManager();

    private final List<GraphSession> mSessions = new ArrayList<>();
    private GraphSession mActiveSession = null;
    private GraphSession mLastOpenedSession = null;
    private long mOpenSessionSerial = 0L;

    private final List<Runnable> mOnTabChangedListeners = new ArrayList<>();

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
        for (GraphSession s : mSessions) {
            if (s.fileId.equals(session.fileId)) {
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
        mSessions.remove(session);
        if (mActiveSession == session) {
            mActiveSession = mSessions.isEmpty() ? null : mSessions.get(mSessions.size() - 1);
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

    public void saveSession(GraphSession session) {
        if (session == null) return;
        try {
            // 1. 序列化当前图纸
            String json = GraphJsonIO.toJson(session.editorContext.getGraph());

            // 2. 写入文件 (继承你之前在 ViewportPanel 中的逻辑)
            Files.writeString(Path.of(session.fileId), json);

            // 3. 清除脏标记
            session.isDirty = false;

            // 4. 触发 UI 刷新 (消除 Tab 上的星号)
            notifyTabChanged();

            System.out.println("[DocumentManager] Save Success: " + session.fileId);
        } catch (Exception e) {
            System.err.println("[DocumentManager] Save Failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void notifyTabChanged() {
        List<Runnable> listeners = List.copyOf(mOnTabChangedListeners);
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private void markSessionOpened(GraphSession session) {
        mLastOpenedSession = session;
        mOpenSessionSerial++;
    }
}
