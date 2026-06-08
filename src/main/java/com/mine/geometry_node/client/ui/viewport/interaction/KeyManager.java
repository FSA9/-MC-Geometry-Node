package com.mine.geometry_node.client.ui.viewport.interaction;

import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.ConfigChangeListener;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;
import com.mine.geometry_node.client.ui.viewport.action.ViewportAction;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionId;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionRegistry;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionRequest;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionSink;
import icyllis.modernui.view.KeyEvent;

public class KeyManager {

    private final InteractionContext mContext;
    private final ConfigChangeListener mConfigChangeListener = this::applyConfig;
    private ViewportActionSink mActionSink;
    private AppConfig mConfig;

    public KeyManager(InteractionContext context) {
        this.mContext = context;
        applyConfig(ConfigManager.INSTANCE.getConfig());
        ConfigManager.INSTANCE.addChangeListener(mConfigChangeListener);
    }

    public void dispose() {
        ConfigManager.INSTANCE.removeChangeListener(mConfigChangeListener);
    }

    public void setActionSink(ViewportActionSink actionSink) {
        this.mActionSink = actionSink;
    }

    public boolean onKeyDown(KeyEvent event) {
        if (!mContext.isReady()) return false;
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;

        for (ViewportAction action : ViewportActionRegistry.all()) {
            KeyBinding binding = action.keyBinding(mConfig);
            if (!matches(binding, event)) continue;
            if (!action.isEnabled(mContext)) return true;
            if (mActionSink != null) {
                mActionSink.performAction(action.id(), requestFor(action.id()));
            }
            return true;
        }
        return false;
    }

    private void applyConfig(AppConfig config) {
        mConfig = config;
    }

    private ViewportActionRequest requestFor(ViewportActionId id) {
        if (id == ViewportActionId.PASTE) {
            return ViewportActionRequest.builder()
                    .ui(mContext.getLastMouseUiX(), mContext.getLastMouseUiY())
                    .build();
        }
        return ViewportActionRequest.EMPTY;
    }

    private static boolean matches(KeyBinding binding, KeyEvent event) {
        return binding != null && binding.matches(event);
    }
}
