package com.mine.geometry_node.client.ui.shortcut;

import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;

public class ScopedAction<T> {
    public interface EnabledReader<S> {
        boolean isEnabled(S state);
    }

    private final KeyScope mScope;
    private final T mId;
    private final String mLabel;
    private final ConfigEntry<String> mShortcutEntry;
    private final EnabledReader<?> mEnabledReader;

    public ScopedAction(KeyScope scope, T id, String label, ConfigEntry<String> shortcutEntry, EnabledReader<?> enabledReader) {
        mScope = scope;
        mId = id;
        mLabel = label;
        mShortcutEntry = shortcutEntry;
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

    public ConfigEntry<String> shortcutEntry() {
        return mShortcutEntry;
    }

    @SuppressWarnings("unchecked")
    public <S> boolean isEnabled(S state) {
        return mEnabledReader == null || ((EnabledReader<S>) mEnabledReader).isEnabled(state);
    }

    public String shortcutText(AppConfig config) {
        if (mShortcutEntry == null || config == null || config.keyBindings == null) return "";
        String shortcut = mShortcutEntry.get(config);
        return shortcut != null ? shortcut : "";
    }

    public KeyBinding keyBinding(AppConfig config) {
        return KeyBinding.parse(shortcutText(config));
    }
}
