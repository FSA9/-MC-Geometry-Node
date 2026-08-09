package com.mine.geometry_node.client.ui.persistence.config;

import java.util.List;
import java.util.Objects;

/** Isolated mutable settings transaction used by the future global settings dialog. */
public final class ConfigDraft {
    private AppConfig mBaseline;
    private final AppConfig mConfig;

    ConfigDraft(AppConfig source) {
        mBaseline = Objects.requireNonNull(source, "source").copy();
        mConfig = source.copy();
    }

    public <T> T get(ConfigEntry<T> entry) {
        return Objects.requireNonNull(entry, "entry").get(mConfig);
    }

    public <T> void set(ConfigEntry<T> entry, T value) {
        Objects.requireNonNull(entry, "entry").set(mConfig, value);
    }

    public void reset(ConfigEntry<?> entry) {
        resetTyped(Objects.requireNonNull(entry, "entry"));
    }

    public void reset(ConfigCategory category) {
        for (ConfigEntry<?> entry : ConfigRegistry.INSTANCE.entries(category)) resetTyped(entry);
    }

    public void resetAll() {
        for (ConfigEntry<?> entry : ConfigRegistry.INSTANCE.entries()) resetTyped(entry);
    }

    public List<ConfigEntry<String>> shortcutConflicts(ConfigEntry<String> entry, String candidate) {
        return ConfigRegistry.INSTANCE.shortcutConflicts(entry, candidate, mConfig);
    }

    public boolean isDirty(ConfigEntry<?> entry) {
        return isDirtyTyped(Objects.requireNonNull(entry, "entry"));
    }

    public boolean isDirty() {
        for (ConfigEntry<?> entry : ConfigRegistry.INSTANCE.entries()) {
            if (isDirtyTyped(entry)) return true;
        }
        return false;
    }

    AppConfig snapshot() {
        return mConfig.copy();
    }

    void acceptAppliedState() {
        mBaseline = mConfig.copy();
    }

    private <T> void resetTyped(ConfigEntry<T> entry) {
        entry.set(mConfig, entry.defaultValue());
    }

    private <T> boolean isDirtyTyped(ConfigEntry<T> entry) {
        return !Objects.equals(entry.get(mBaseline), entry.get(mConfig));
    }
}
