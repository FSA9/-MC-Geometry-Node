package com.mine.geometry_node.client.ui.viewport.interaction;

import icyllis.modernui.view.KeyEvent;

public class KeyManager {

    // --- 新增：快捷键意图监听器 ---
    public interface KeyListener {
        void onUndo();
        void onRedo();
        void onSaveRequested();
        void onCopyRequested();
        void onPasteRequested(float uiX, float uiY);
        void onDeleteRequested();
    }

    private static final int GLFW_KEY_DELETE = 261;
    private final InteractionContext mContext;
    private KeyListener mListener;

    public KeyManager(InteractionContext context) {
        this.mContext = context;
    }

    public void setListener(KeyListener listener) {
        this.mListener = listener;
    }

    public boolean onKeyDown(KeyEvent event) {
        if (!mContext.isReady()) return false;

        boolean isCtrl = event.isCtrlPressed();
        int keyCode = event.getKeyCode();

        if (isCtrl) {
            switch (keyCode) {
                case KeyEvent.KEY_Z: if (mListener != null) mListener.onUndo(); return true;
                case KeyEvent.KEY_Y: if (mListener != null) mListener.onRedo(); return true;
                case KeyEvent.KEY_S: if (mListener != null) mListener.onSaveRequested(); return true;
                case KeyEvent.KEY_C: if (mListener != null) mListener.onCopyRequested(); return true;
                case KeyEvent.KEY_V:
                    if (mListener != null) {
                        mListener.onPasteRequested(mContext.getLastMouseUiX(), mContext.getLastMouseUiY());
                    }
                    return true;
            }
        } else {
            if (keyCode == GLFW_KEY_DELETE) {
                if (mListener != null) mListener.onDeleteRequested();
                return true;
            }
        }
        return false;
    }
}