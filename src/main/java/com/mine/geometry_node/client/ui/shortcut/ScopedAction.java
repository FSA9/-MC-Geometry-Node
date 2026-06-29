package com.mine.geometry_node.client.ui.shortcut;

import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;

public class ScopedAction<T> {
    public interface ShortcutReader {
        String read(AppConfig config);
    }

    public interface EnabledReader<S> {
        boolean isEnabled(S state);
    }

    private final KeyScope mScope;
    private final T mId;
    private final String mLabel;
    private final ShortcutReader mShortcutReader;
    private final EnabledReader<?> mEnabledReader;

    public ScopedAction(KeyScope scope, T id, String label, ShortcutReader shortcutReader, EnabledReader<?> enabledReader) {
        mScope = scope;
        mId = id;
        mLabel = label;
        mShortcutReader = shortcutReader;
        mEnabledReader = enabledReader;
    }

    public KeyScope scope() {
        return mScope;
    }

    public T id() {
        return mId;
    }

    public String label() {
        return mLabel;
    }

    @SuppressWarnings("unchecked")
    public <S> boolean isEnabled(S state) {
        return mEnabledReader == null || ((EnabledReader<S>) mEnabledReader).isEnabled(state);
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
