package com.mine.geometry_node.client.ui.area;

import com.mine.geometry_node.client.ui.editor.asset.AssetBrowserWindow;
import com.mine.geometry_node.client.ui.editor.terminal.TerminalWindow;
import com.mine.geometry_node.client.ui.editor.graph.GraphEditorWindow;
import com.mine.geometry_node.client.ui.persistence.session.EditorSessionState;
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
        return switch (type) {
            case GRAPH_EDITOR -> new GraphEditorWindow(context, sessionState.graphEditor, sessionChanged);
            case ASSET_BROWSER -> new AssetBrowserWindow(context, sessionState.assetBrowser, sessionChanged);
            case TERMINAL -> new TerminalWindow(context, sessionState.terminal, sessionChanged);
            case PERFORMANCE -> createPlaceholder(context, type.displayName());
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
}
