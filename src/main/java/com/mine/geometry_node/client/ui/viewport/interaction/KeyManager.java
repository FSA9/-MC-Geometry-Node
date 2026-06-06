package com.mine.geometry_node.client.ui.viewport.interaction;

import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.ConfigChangeListener;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;
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
        void onToggleSnapToGridRequested();
        void onToggleGridAndAxisRequested();
        void onGroupIntoFrameRequested();
    }

    private final InteractionContext mContext;
    private final ConfigChangeListener mConfigChangeListener = this::applyConfig;
    private KeyListener mListener;
    private KeyBinding mUndoBinding;
    private KeyBinding mRedoBinding;
    private KeyBinding mSaveBinding;
    private KeyBinding mCopyBinding;
    private KeyBinding mPasteBinding;
    private KeyBinding mDeleteBinding;
    private KeyBinding mToggleSnapToGridBinding;
    private KeyBinding mToggleGridAndAxisBinding;
    private KeyBinding mGroupIntoFrameBinding;

    public KeyManager(InteractionContext context) {
        this.mContext = context;
        applyConfig(ConfigManager.INSTANCE.getConfig());
        ConfigManager.INSTANCE.addChangeListener(mConfigChangeListener);
    }

    public void dispose() {
        ConfigManager.INSTANCE.removeChangeListener(mConfigChangeListener);
    }

    public void setListener(KeyListener listener) {
        this.mListener = listener;
    }

    public boolean onKeyDown(KeyEvent event) {
        if (!mContext.isReady()) return false;
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;

        if (matches(mUndoBinding, event)) {
            if (mListener != null) mListener.onUndo();
            return true;
        }
        if (matches(mRedoBinding, event)) {
            if (mListener != null) mListener.onRedo();
            return true;
        }
        if (matches(mSaveBinding, event)) {
            if (mListener != null) mListener.onSaveRequested();
            return true;
        }
        if (matches(mCopyBinding, event)) {
            if (mListener != null) mListener.onCopyRequested();
            return true;
        }
        if (matches(mPasteBinding, event)) {
            if (mListener != null) {
                mListener.onPasteRequested(mContext.getLastMouseUiX(), mContext.getLastMouseUiY());
            }
            return true;
        }
        if (matches(mDeleteBinding, event)) {
            if (mListener != null) mListener.onDeleteRequested();
            return true;
        }
        if (matches(mToggleSnapToGridBinding, event)) {
            if (mListener != null) mListener.onToggleSnapToGridRequested();
            return true;
        }
        if (matches(mToggleGridAndAxisBinding, event)) {
            if (mListener != null) mListener.onToggleGridAndAxisRequested();
            return true;
        }
        if (matches(mGroupIntoFrameBinding, event)) {
            if (mListener != null) mListener.onGroupIntoFrameRequested();
            return true;
        }
        return false;
    }

    private void applyConfig(AppConfig config) {
        if (config == null || config.keyBindings == null) return;
        mUndoBinding = KeyBinding.parse(config.keyBindings.undo);
        mRedoBinding = KeyBinding.parse(config.keyBindings.redo);
        mSaveBinding = KeyBinding.parse(config.keyBindings.save);
        mCopyBinding = KeyBinding.parse(config.keyBindings.copy);
        mPasteBinding = KeyBinding.parse(config.keyBindings.paste);
        mDeleteBinding = KeyBinding.parse(config.keyBindings.delete);
        mToggleSnapToGridBinding = KeyBinding.parse(config.keyBindings.toggleSnapToGrid);
        mToggleGridAndAxisBinding = KeyBinding.parse(config.keyBindings.toggleGridAndAxis);
        mGroupIntoFrameBinding = KeyBinding.parse(config.keyBindings.groupIntoFrame);
    }

    private static boolean matches(KeyBinding binding, KeyEvent event) {
        return binding != null && binding.matches(event);
    }
}
