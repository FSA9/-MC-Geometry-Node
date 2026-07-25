package com.mine.geometry_node.client.ui.area;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetBrowserPanel;
import com.mine.geometry_node.client.ui.bottom_window.console.TerminalConsolePanel;
import com.mine.geometry_node.client.ui.editor.graph.GraphEditorWindow;
import com.mine.geometry_node.client.ui.window.IToolWindow;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;

final class AreaEditorRegistry {
    IToolWindow create(Context context, AreaEditorType type) {
        return switch (type) {
            case GRAPH_EDITOR -> new GraphEditorWindow(context);
            case ASSET_BROWSER -> new AssetBrowserPanel(context);
            case TERMINAL -> new TerminalConsolePanel(context);
            case PERFORMANCE -> createPlaceholder(context, type.displayName());
        };
    }

    private IToolWindow createPlaceholder(Context context, String title) {
        FrameLayout layout = new FrameLayout(context);
        TextView label = new TextView(context);
        label.setText(title);
        label.setTextColor(0xFF777777);
        label.setGravity(Gravity.CENTER);
        layout.addView(label, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return new IToolWindow() {
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
