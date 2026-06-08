package com.mine.geometry_node.client.ui.viewport.action;

import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;

public final class ViewportAction {
    public interface ShortcutReader {
        String read(AppConfig config);
    }

    public interface EnabledReader {
        boolean isEnabled(ViewportActionState state);
    }

    private final ViewportActionId mId;
    private final String mLabel;
    private final ShortcutReader mShortcutReader;
    private final EnabledReader mEnabledReader;

    ViewportAction(ViewportActionId id, String label, ShortcutReader shortcutReader, EnabledReader enabledReader) {
        mId = id;
        mLabel = label;
        mShortcutReader = shortcutReader;
        mEnabledReader = enabledReader;
    }

    public ViewportActionId id() {
        return mId;
    }

    public String label() {
        return mLabel;
    }

    public boolean isEnabled(ViewportActionState state) {
        return mEnabledReader == null || mEnabledReader.isEnabled(state);
    }

    public String shortcutText(AppConfig config) {
        if (mShortcutReader == null || config == null || config.keyBindings == null) return "";
        String shortcut = mShortcutReader.read(config);
        return shortcut != null ? shortcut : "";
    }

    public KeyBinding keyBinding(AppConfig config) {
        return KeyBinding.parse(shortcutText(config));
    }
}
