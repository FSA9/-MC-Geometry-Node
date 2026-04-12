package com.mine.geometry_node.client.ui.session;

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
        // 如果已经打开了，直接切换过去
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
        // TODO: 这里将在后续阶段接入 isDirty 的弹窗拦截逻辑
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
}