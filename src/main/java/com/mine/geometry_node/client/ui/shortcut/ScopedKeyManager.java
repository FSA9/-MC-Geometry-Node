package com.mine.geometry_node.client.ui.shortcut;

import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.ConfigChangeListener;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;
import icyllis.modernui.view.KeyEvent;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class ScopedKeyManager<T, S> {
    public interface ActionExecutor<T> {
        void execute(T id);
    }

    private final S mState;
    private final KeyScope mScope;
    private final Supplier<? extends Collection<? extends ScopedAction<T>>> mActions;
    private final ActionExecutor<T> mExecutor;
    private final ConfigChangeListener mConfigChangeListener = this::applyConfig;
    private AppConfig mConfig;
    private boolean mAttached;

    public ScopedKeyManager(S state,
                            KeyScope scope,
                            Supplier<? extends Collection<? extends ScopedAction<T>>> actions,
                            ActionExecutor<T> executor) {
        mState = state;
        mScope = scope != null ? scope : KeyScope.GLOBAL;
        mActions = actions;
        mExecutor = executor;
        attach();
    }

    public void attach() {
        if (mAttached) {
            return;
        }
        applyConfig(ConfigManager.INSTANCE.getConfig());
        ConfigManager.INSTANCE.addChangeListener(mConfigChangeListener);
        mAttached = true;
    }

    public void dispose() {
        if (!mAttached) {
            return;
        }
        ConfigManager.INSTANCE.removeChangeListener(mConfigChangeListener);
        mAttached = false;
    }

    public boolean onKeyDown(KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;

        List<KeyScope> scopes = mScope == KeyScope.GLOBAL ? List.of(KeyScope.GLOBAL) : List.of(mScope, KeyScope.GLOBAL);
        for (KeyScope scope : scopes) {
            if (handleScope(scope, event)) return true;
        }
        return false;
    }

    private boolean handleScope(KeyScope scope, KeyEvent event) {
        Collection<? extends ScopedAction<T>> actions = mActions != null ? mActions.get() : null;
        if (actions == null || actions.isEmpty()) return false;

        for (ScopedAction<T> action : actions) {
            if (action == null || action.scope() != scope) continue;
            KeyBinding binding = action.keyBinding(mConfig);
            if (!matches(binding, event)) continue;
            if (!action.isEnabled(mState)) return true;
            if (mExecutor != null) {
                mExecutor.execute(action.id());
            }
            return true;
        }
        return false;
    }

    private void applyConfig(AppConfig config) {
        mConfig = config;
    }

    private static boolean matches(KeyBinding binding, KeyEvent event) {
        return binding != null && binding.matches(event);
    }
}
