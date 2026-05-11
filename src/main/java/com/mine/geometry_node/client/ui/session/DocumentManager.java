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

    private Runnable mOnTabChangedListener;

    private DocumentManager() {}

    public void setOnTabChangedListener(Runnable listener) {
        this.mOnTabChangedListener = listener;
    }

    public List<GraphSession> getSessions() {
        return mSessions;
    }

    public GraphSession getActiveSession() {
        return mActiveSession;
    }

    // 打开或新建一个图纸
    public void openSession(GraphSession session) {
        for (GraphSession s : mSessions) {
            if (s.fileId.equals(session.fileId)) {
                switchSession(s);
                return;
            }
        }
        mSessions.add(session);
        switchSession(session);
    }

    public void switchSession(GraphSession session) {
        if (mSessions.contains(session)) {
            mActiveSession = session;
            if (mOnTabChangedListener != null) {
                mOnTabChangedListener.run();
            }
        }
    }

    public void closeSession(GraphSession session) {
        mSessions.remove(session);
        if (mActiveSession == session) {
            mActiveSession = mSessions.isEmpty() ? null : mSessions.get(mSessions.size() - 1);
        }
        if (mOnTabChangedListener != null) {
            mOnTabChangedListener.run();
        }
    }

    public void moveSession(int fromIndex, int toIndex) {
        if (fromIndex >= 0 && fromIndex < mSessions.size() && toIndex >= 0 && toIndex < mSessions.size()) {
            // 交换位置
            GraphSession session = mSessions.remove(fromIndex);
            mSessions.add(toIndex, session);
            // 触发 UI 刷新
            if (mOnTabChangedListener != null) {
                mOnTabChangedListener.run();
            }
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
            if (mOnTabChangedListener != null) {
                mOnTabChangedListener.run();
            }

            System.out.println("[DocumentManager] Save Success: " + session.fileId);
        } catch (Exception e) {
            System.err.println("[DocumentManager] Save Failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void notifyTabChanged() {
        if (mOnTabChangedListener != null) {
            mOnTabChangedListener.run();
        }
    }
}