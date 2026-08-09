package com.mine.geometry_node.client.ui.persistence.config;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ConfigManager {
    public static final ConfigManager INSTANCE = new ConfigManager();

    private final ConfigStore mStore = new ConfigStore();
    private final List<ConfigChangeListener> mListeners = new ArrayList<>();
    private AppConfig mCurrentConfig;

    private ConfigManager() {
    }

    public void initOrLoad() {
        AppConfig loadedConfig;
        boolean shouldSave;

        try {
            JsonObject root = mStore.loadRoot();
            if (root == null || !ConfigSanitizer.looksLikeConfig(root)) {
                loadedConfig = AppConfig.defaults();
                shouldSave = true;
                if (root != null) mStore.delete();
            } else {
                ConfigSanitizer.Result result = ConfigSanitizer.fromJson(root);
                loadedConfig = result.config();
                shouldSave = result.changed();
            }
        } catch (Exception ignored) {
            loadedConfig = AppConfig.defaults();
            shouldSave = true;
            try {
                mStore.delete();
            } catch (IOException ignoredDeleteError) {
            }
        }

        synchronized (this) {
            mCurrentConfig = loadedConfig;
        }

        if (shouldSave) save();
        notifyConfigChanged(loadedConfig);
    }

    public AppConfig getConfig() {
        ensureLoaded();
        synchronized (this) {
            return mCurrentConfig.copy();
        }
    }

    public <T> T get(ConfigEntry<T> entry) {
        ensureLoaded();
        synchronized (this) {
            return entry.get(mCurrentConfig);
        }
    }

    public <T> void set(ConfigEntry<T> entry, T value) {
        Objects.requireNonNull(entry, "entry");
        update(config -> entry.set(config, value));
    }

    public ConfigDraft createDraft() {
        ensureLoaded();
        synchronized (this) {
            return new ConfigDraft(mCurrentConfig);
        }
    }

    /** Atomically validates, persists and publishes one settings-dialog transaction. */
    public void apply(ConfigDraft draft) {
        Objects.requireNonNull(draft, "draft");
        replace(draft.snapshot());
        draft.acceptAppliedState();
    }

    public void edit(Consumer<ConfigDraft> editor) {
        Objects.requireNonNull(editor, "editor");
        ConfigDraft draft = createDraft();
        editor.accept(draft);
        apply(draft);
    }

    public void reset(ConfigEntry<?> entry) {
        edit(draft -> draft.reset(entry));
    }

    public void reset(ConfigCategory category) {
        edit(draft -> draft.reset(category));
    }

    public void resetAllSettings() {
        edit(ConfigDraft::resetAll);
    }

    public void update(Consumer<AppConfig> mutator) {
        ensureLoaded();
        AppConfig updated;
        synchronized (this) {
            AppConfig candidate = mCurrentConfig.copy();
            mutator.accept(candidate);
            ConfigSanitizer.Result result = ConfigSanitizer.sanitize(candidate);
            mCurrentConfig = result.config();
            updated = mCurrentConfig;
        }
        save();
        notifyConfigChanged(updated);
    }

    private void replace(AppConfig replacement) {
        ensureLoaded();
        AppConfig updated;
        synchronized (this) {
            ConfigSanitizer.Result result = ConfigSanitizer.sanitize(replacement.copy());
            mCurrentConfig = result.config();
            updated = mCurrentConfig;
        }
        save();
        notifyConfigChanged(updated);
    }

    public void save() {
        ensureLoaded();
        AppConfig config;
        synchronized (this) {
            ConfigSanitizer.Result result = ConfigSanitizer.sanitize(mCurrentConfig);
            mCurrentConfig = result.config();
            config = mCurrentConfig;
        }

        try {
            mStore.save(config);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void addChangeListener(ConfigChangeListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public synchronized void removeChangeListener(ConfigChangeListener listener) {
        mListeners.remove(listener);
    }

    private void ensureLoaded() {
        synchronized (this) {
            if (mCurrentConfig != null) return;
        }
        initOrLoad();
    }

    private void notifyConfigChanged(AppConfig config) {
        List<ConfigChangeListener> listeners;
        synchronized (this) {
            listeners = new ArrayList<>(mListeners);
        }

        for (ConfigChangeListener listener : listeners) {
            listener.onConfigChanged(config.copy());
        }
    }
}
