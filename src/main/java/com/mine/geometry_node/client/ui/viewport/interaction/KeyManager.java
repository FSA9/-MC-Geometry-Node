package com.mine.geometry_node.client.ui.viewport.interaction;

import com.mine.geometry_node.client.ui.shortcut.KeyScope;
import com.mine.geometry_node.client.ui.shortcut.ScopedKeyManager;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionId;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionRegistry;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionRequest;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionSink;
import icyllis.modernui.view.KeyEvent;

public class KeyManager {
    private final InteractionContext mContext;
    private final ScopedKeyManager<ViewportActionId, InteractionContext> mScopedKeyManager;
    private ViewportActionSink mActionSink;

    public KeyManager(InteractionContext context) {
        this(context, KeyScope.VIEWPORT);
    }

    public KeyManager(InteractionContext context, KeyScope scope) {
        mContext = context;
        mScopedKeyManager = new ScopedKeyManager<>(
                context,
                scope,
                ViewportActionRegistry::all,
                this::performAction
        );
    }

    public void dispose() {
        mScopedKeyManager.dispose();
    }

    public void setActionSink(ViewportActionSink actionSink) {
        mActionSink = actionSink;
    }

    public boolean onKeyDown(KeyEvent event) {
        return mContext.isReady() && mScopedKeyManager.onKeyDown(event);
    }

    private void performAction(ViewportActionId id) {
        if (mActionSink != null) {
            mActionSink.performAction(id, requestFor(id));
        }
    }

    private ViewportActionRequest requestFor(ViewportActionId id) {
        if (id == ViewportActionId.PASTE) {
            return ViewportActionRequest.builder()
                    .ui(mContext.getLastMouseUiX(), mContext.getLastMouseUiY())
                    .build();
        }
        return ViewportActionRequest.EMPTY;
    }
}
