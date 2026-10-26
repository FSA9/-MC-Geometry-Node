package com.mine.geometry_node.client.ui.shell.registration;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.integration.terminal.McpTerminalRuntimeFactory;
import com.mine.geometry_node.client.ui.editor.asset.AssetBrowserWindow;
import com.mine.geometry_node.client.integration.asset.WorkspaceAssetBrowserBindings;
import com.mine.geometry_node.client.ui.editor.datalibrary.DatabaseWindow;
import com.mine.geometry_node.client.ui.editor.game.GameViewportWindow;
import com.mine.geometry_node.client.ui.editor.graph.GraphEditorWindow;
import com.mine.geometry_node.client.ui.editor.terminal.TerminalWindow;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorRegistry;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorType;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorWindow;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceType;
import com.mine.geometry_node.client.ui.workspace.state.UiStateContext;
import com.mine.geometry_node.client.ui.workspace.state.BuiltinUiStateKeys;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

/** Composes the concrete editors available in the main workspace. */
public final class BuiltinAreaEditors {
    private BuiltinAreaEditors() {}

    public static void register(AreaEditorRegistry registry) {
        registry.register(AreaEditorType.GRAPH_EDITOR, UiSurfaceType.VIEWPORT,
                (context, state, changed) -> new GraphEditorWindow(context, UiStateContext.forArea(state, changed)));
        registry.register(AreaEditorType.GAME_VIEWPORT, UiSurfaceType.GAME_VIEWPORT,
                (context, state, changed) -> new GameViewportWindow(context));
        registry.register(AreaEditorType.ASSET_BROWSER, UiSurfaceType.ASSET_BROWSER,
                (context, state, changed) -> {
                    var uiState = UiStateContext.forArea(state, changed);
                    var snapshot = uiState.read(BuiltinUiStateKeys.ASSET_BROWSER, BuiltinUiStateKeys.SCOPE);
                    return new AssetBrowserWindow(context, snapshot,
                            () -> uiState.write(BuiltinUiStateKeys.ASSET_BROWSER, BuiltinUiStateKeys.SCOPE, snapshot), uiState,
                            new WorkspaceAssetBrowserBindings());
                });
        registry.register(AreaEditorType.DATA_LIBRARY, UiSurfaceType.DATA_LIBRARY,
                (context, state, changed) -> new DatabaseWindow(context, UiStateContext.forArea(state, changed)));
        registry.register(AreaEditorType.TERMINAL, UiSurfaceType.TERMINAL,
                (context, state, changed) -> {
                    var uiState = UiStateContext.forArea(state, changed);
                    var snapshot = uiState.read(BuiltinUiStateKeys.TERMINAL, BuiltinUiStateKeys.SCOPE);
                    return new TerminalWindow(context, snapshot,
                            () -> uiState.write(BuiltinUiStateKeys.TERMINAL, BuiltinUiStateKeys.SCOPE, snapshot),
                            new McpTerminalRuntimeFactory());
                });
        registry.register(AreaEditorType.PERFORMANCE, UiSurfaceType.PERFORMANCE,
                (context, state, changed) -> createPlaceholder(context,
                        Component.translatable(AreaEditorType.PERFORMANCE.translationKey()).getString()));
    }

    private static AreaEditorWindow createPlaceholder(Context context, String title) {
        FrameLayout layout = new FrameLayout(context);
        TextView label = new TextView(context);
        label.setText(title);
        label.setTextColor(UiThemes.EDITOR.text().hint());
        label.setGravity(Gravity.CENTER);
        layout.addView(label, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return new AreaEditorWindow() {
            @Override public View getView() { return layout; }
            @Override public void onShow() {}
            @Override public void onHide() {}
        };
    }
}
