package com.mine.geometry_node.client.ui.shortcut;

import com.mine.geometry_node.client.input.binding.KeyScope;
import com.mine.geometry_node.client.config.AppConfig;
import com.mine.geometry_node.client.config.ConfigChangeListener;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.input.binding.InputBinding;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ScopedKeyManager<T, S> {
    public interface ActionExecutor<T> {
        void execute(T id);
    }

    private final S mState;
    private final KeyScope mScope;
    private final Supplier<? extends Collection<? extends ScopedAction<T>>> mActions;
    private final ActionExecutor<T> mExecutor;
    private final ConfigManager mConfigManager;
    private ConfigChangeListener mConfigChangeListener;
    private long mListenerGeneration;
    private volatile AppConfig mConfig;
    private volatile boolean mAttached;

    public ScopedKeyManager(S state,
                            KeyScope scope,
                            Supplier<? extends Collection<? extends ScopedAction<T>>> actions,
                            ActionExecutor<T> executor) {
        this(state, scope, actions, executor, ConfigManager.INSTANCE);
    }

    ScopedKeyManager(S state,
                     KeyScope scope,
                     Supplier<? extends Collection<? extends ScopedAction<T>>> actions,
                     ActionExecutor<T> executor,
                     ConfigManager configManager) {
        mState = state;
        mScope = scope != null ? scope : KeyScope.GLOBAL;
        mActions = actions;
        mExecutor = executor;
        mConfigManager = configManager;
        attach();
    }

    public void attach() {
        if (mAttached) {
            return;
        }
        // Lazy loading can notify other observers; complete it before taking the publication lock.
        mConfigManager.getConfig();
        // Take the initial snapshot and subscribe under the publication lock so no commit is missed.
        synchronized (mConfigManager) {
            if (mAttached) return;
            long generation = ++mListenerGeneration;
            mConfigChangeListener = config -> applyConfig(generation, config);
            mConfig = mConfigManager.getConfig();
            mConfigManager.addChangeListener(mConfigChangeListener);
            mAttached = true;
        }
    }

    public void dispose() {
        synchronized (mConfigManager) {
            if (!mAttached) return;
            mAttached = false;
            mConfigManager.removeChangeListener(mConfigChangeListener);
            mConfigChangeListener = null;
        }
    }

    public boolean onKeyDown(KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN || event.isCanceled()) return false;
        return handleInput(binding -> UiInputBindings.matches(binding, event));
    }

    public boolean onMouseDown(MotionEvent event) {
        if (event == null || (event.getActionMasked() != MotionEvent.ACTION_DOWN
                && event.getActionMasked() != MotionEvent.ACTION_BUTTON_PRESS)) return false;
        return handleInput(binding -> UiInputBindings.matches(binding, event));
    }

    private boolean handleInput(Predicate<InputBinding> matches) {
        if (!mAttached || mExecutor == null) return false;
        AppConfig config = mConfig;
        Collection<? extends ScopedAction<T>> actions = mActions != null ? mActions.get() : null;
        if (actions == null || actions.isEmpty()) return false;
        return handleScope(mScope, actions, config, matches)
                || (mScope != KeyScope.GLOBAL && handleScope(KeyScope.GLOBAL, actions, config, matches));
    }

    private boolean handleScope(KeyScope scope, Collection<? extends ScopedAction<T>> actions,
                                AppConfig config, Predicate<InputBinding> matches) {
        for (ScopedAction<T> action : actions) {
            if (action == null || action.scope() != scope) continue;
            if (!matches.test(action.inputBinding(config))) continue;
            // A disabled contextual command still reserves its shortcut against global fallback.
            if (!action.isEnabled(mState)) return true;
            mExecutor.execute(action.id());
            return true;
        }
        return false;
    }

    private void applyConfig(long generation, AppConfig config) {
        synchronized (mConfigManager) {
            // ConfigManager snapshots listeners when queuing notifications. A removed subscription
            // may still receive a queued callback, including after this manager has reattached.
            if (mAttached && generation == mListenerGeneration) mConfig = config;
        }
    }

}
