package com.mine.geometry_node.client.config;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ConfigManager {
    public static final ConfigManager INSTANCE = new ConfigManager();

    private static final System.Logger LOGGER = System.getLogger(ConfigManager.class.getName());
    private final ConfigStore mStore;
    private final List<ConfigChangeListener> mListeners = new ArrayList<>();
    private final ArrayDeque<Notification> mNotifications = new ArrayDeque<>();
    private boolean mDrainingNotifications;
    private AppConfig mCurrentConfig;

    private ConfigManager() {
        this(new ConfigStore());
    }

    ConfigManager(ConfigStore store) {
        mStore = Objects.requireNonNull(store, "store");
    }

    public void initOrLoad() {
        synchronized (this) {
            loadAndPublish();
        }
        drainNotifications();
    }

    /** Called under the manager lock, including lazy initialization. */
    private void loadAndPublish() {
        AppConfig loadedConfig;
        boolean shouldSave;
        try {
            JsonObject root = mStore.loadRoot();
            if (root == null || !ConfigJsonCodec.looksLikeConfig(root)) {
                loadedConfig = AppConfig.defaults();
                shouldSave = true;
            } else {
                ConfigJsonCodec.Result result = ConfigJsonCodec.fromJson(root);
                loadedConfig = result.config();
                shouldSave = result.changed();
            }
        } catch (Exception ignored) {
            loadedConfig = AppConfig.defaults();
            shouldSave = true;
        }
        // Atomic replacement also repairs malformed files; do not delete the old file first.
        if (shouldSave) {
            try {
                mStore.save(loadedConfig);
            } catch (IOException failure) {
                // Loading must remain usable in a read-only workspace. This is not an Apply:
                // keep an existing live configuration, or publish defaults on the first load.
                LOGGER.log(System.Logger.Level.WARNING, "Could not persist automatically repaired settings", failure);
                if (mCurrentConfig != null) return;
            }
        }
        mCurrentConfig = loadedConfig;
        enqueueNotification(loadedConfig);
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
        ensureLoaded();
        synchronized (this) {
            AppConfig updated = commit(draft.mergeOnto(mCurrentConfig));
            draft.acceptAppliedState(updated);
            enqueueNotification(updated);
        }
        drainNotifications();
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
        Objects.requireNonNull(mutator, "mutator");
        ensureLoaded();
        synchronized (this) {
            AppConfig candidate = mCurrentConfig.copy();
            mutator.accept(candidate);
            enqueueNotification(commit(candidate));
        }
        drainNotifications();
    }

    /** Caller holds this manager's lock through persistence and publication of the candidate. */
    private AppConfig commit(AppConfig candidate) {
        AppConfig updated = ConfigSanitizer.sanitize(candidate.copy()).config();
        persist(updated);
        mCurrentConfig = updated;
        return updated;
    }

    /** Persistence failures propagate to the caller without publishing a new configuration. */
    public void save() {
        ensureLoaded();
        synchronized (this) {
            commit(mCurrentConfig);
        }
    }

    private void persist(AppConfig config) {
        try {
            mStore.save(config);
        } catch (IOException failure) {
            throw new UncheckedIOException("Failed to save settings", failure);
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
            loadAndPublish();
        }
        drainNotifications();
    }

    /** Enqueued under the commit lock, including the listener set for that publication. */
    private void enqueueNotification(AppConfig config) {
        mNotifications.addLast(new Notification(config.copy(), List.copyOf(mListeners)));
    }

    /**
     * One caller drains complete listener batches in commit order, without holding the manager lock.
     * Concurrent or listener-reentrant commits enqueue and return; their callbacks follow this batch.
     */
    private void drainNotifications() {
        synchronized (this) {
            if (mDrainingNotifications) return;
            mDrainingNotifications = true;
        }
        try {
            while (true) {
                Notification notification;
                synchronized (this) {
                    notification = mNotifications.pollFirst();
                    if (notification == null) {
                        // Release ownership atomically with observing the empty queue.
                        mDrainingNotifications = false;
                        return;
                    }
                }
                for (ConfigChangeListener listener : notification.listeners()) {
                    try {
                        listener.onConfigChanged(notification.config().copy());
                    } catch (RuntimeException failure) {
                        // The commit is already durable; an observer must not turn it into a failed apply.
                        LOGGER.log(System.Logger.Level.WARNING, "Configuration listener failed", failure);
                    }
                }
            }
        } catch (RuntimeException | Error failure) {
            synchronized (this) {
                mDrainingNotifications = false;
            }
            throw failure;
        }
    }

    private record Notification(AppConfig config, List<ConfigChangeListener> listeners) {}
}
