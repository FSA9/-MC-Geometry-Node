package com.mine.geometry_node.client.ui.area;

import com.mine.geometry_node.client.ui.editor.asset.AssetBrowserWindow;
import com.mine.geometry_node.client.ui.editor.terminal.TerminalWindow;
import com.mine.geometry_node.client.ui.editor.graph.GraphEditorWindow;
import com.mine.geometry_node.client.ui.persistence.session.EditorSessionState;
import com.mine.geometry_node.client.ui.surface.UiSurfaceId;
import com.mine.geometry_node.client.ui.surface.UiSurfaceRegistry;
import com.mine.geometry_node.client.ui.surface.UiSurfaceType;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;

final class AreaEditorRegistry {
    AreaEditorWindow create(
            Context context,
            AreaEditorType type,
            EditorSessionState.AreaState sessionState,
            Runnable sessionChanged) {
        AreaEditorWindow window = switch (type) {
            case GRAPH_EDITOR -> new GraphEditorWindow(context, sessionState.graphEditor, sessionChanged);
            case ASSET_BROWSER -> new AssetBrowserWindow(context, sessionState.assetBrowser, sessionChanged);
            case TERMINAL -> new TerminalWindow(context, sessionState.terminal, sessionChanged);
            case PERFORMANCE -> createPlaceholder(context, type.displayName());
        };
        return new RegisteredWindow(window, surfaceType(type));
    }

    private static UiSurfaceType surfaceType(AreaEditorType type) {
        return switch (type) {
            case GRAPH_EDITOR -> UiSurfaceType.VIEWPORT;
            case ASSET_BROWSER -> UiSurfaceType.ASSET_BROWSER;
            case TERMINAL -> UiSurfaceType.TERMINAL;
            case PERFORMANCE -> UiSurfaceType.PERFORMANCE;
        };
    }

    private AreaEditorWindow createPlaceholder(Context context, String title) {
        FrameLayout layout = new FrameLayout(context);
        TextView label = new TextView(context);
        label.setText(title);
        label.setTextColor(0xFF777777);
        label.setGravity(Gravity.CENTER);
        layout.addView(label, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return new AreaEditorWindow() {
            @Override
            public View getView() {
                return layout;
            }

            @Override
            public void onShow() {
            }

            @Override
            public void onHide() {
            }
        };
    }

    private static final class RegisteredWindow implements AreaEditorWindow {
        private final AreaEditorWindow delegate;
        private final UiSurfaceRegistry.Registration registration;

        private RegisteredWindow(AreaEditorWindow delegate, UiSurfaceType type) {
            this.delegate = delegate;
            this.registration = UiSurfaceRegistry.INSTANCE.register(type, delegate);
            if (delegate instanceof SurfaceRegistrationAware aware) aware.bindSurfaceRegistration(registration);
        }

        @Override public View getView() { return delegate.getView(); }
        @Override public UiSurfaceId surfaceId() { return registration.id(); }
        @Override public void onShow() { registration.setVisible(true); delegate.onShow(); }
        @Override public void onHide() { registration.setVisible(false); delegate.onHide(); }

        @Override
        public void onDispose() {
            try {
                delegate.onDispose();
            } finally {
                registration.close();
            }
        }
    }
}
