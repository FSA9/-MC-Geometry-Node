package com.mine.geometry_node.client.config;

import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

/** Isolated settings transaction; only edited entries are merged into the live configuration. */
public final class ConfigDraft {
    private AppConfig mBaseline;
    private AppConfig mConfig;
    private final Set<String> mResetEntries = new HashSet<>();

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

    AppConfig mergeOnto(AppConfig current) {
        AppConfig merged = Objects.requireNonNull(current, "current").copy();
        for (ConfigEntry<?> entry : ConfigRegistry.INSTANCE.entries()) mergeEntry(entry, merged);
        return merged;
    }

    private <T> void mergeEntry(ConfigEntry<T> entry, AppConfig target) {
        if (isDirtyTyped(entry)) entry.set(target, entry.get(mConfig));
    }

    void acceptAppliedState(AppConfig applied) {
        mConfig = applied.copy();
        mBaseline = applied.copy();
        mResetEntries.clear();
    }

    private <T> void resetTyped(ConfigEntry<T> entry) {
        entry.set(mConfig, entry.defaultValue());
        // Reset is an explicit write even when the opening snapshot already held the default.
        mResetEntries.add(entry.id());
    }

    private <T> boolean isDirtyTyped(ConfigEntry<T> entry) {
        return mResetEntries.contains(entry.id()) || !Objects.equals(entry.get(mBaseline), entry.get(mConfig));
    }
}
