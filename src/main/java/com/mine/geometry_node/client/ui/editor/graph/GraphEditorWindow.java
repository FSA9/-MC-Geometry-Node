package com.mine.geometry_node.client.ui.editor.graph;

import com.mine.geometry_node.client.ui.editor.EditorLayoutPolicy;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.shortcut.UiInputBindings;
import com.mine.geometry_node.client.ui.components.base.layout.ThinResizableDivider;
import com.mine.geometry_node.client.ui.components.composite.sidebar.EditorSidebar;
import com.mine.geometry_node.client.ui.components.composite.sidebar.SidebarLayoutController;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelContext;
import com.mine.geometry_node.client.ui.components.composite.sidebar.api.SidebarPanelScope;
import com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.GraphPropertiesPanel;
import com.mine.geometry_node.client.ui.editor.graph.properties.session.GraphSessionPropertiesTarget;
import com.mine.geometry_node.client.config.AppConfig;
import com.mine.geometry_node.client.ui.workspace.state.EditorSidebarState;
import com.mine.geometry_node.client.ui.workspace.state.UiStateContext;
import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.input.binding.KeyBinding;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorWindow;
import com.mine.geometry_node.client.ui.workspace.area.SurfaceRegistrationAware;
import com.mine.geometry_node.client.ui.document.GraphSession;
import com.mine.geometry_node.client.ui.document.DocumentManager;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import com.mine.geometry_node.client.ui.workspace.surface.ViewportSurface;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import java.util.List;

public class GraphEditorWindow extends LinearLayout
        implements AreaEditorWindow, SurfaceRegistrationAware, ViewportSurface {
    private static final float INITIAL_WORKSPACE_WEIGHT = 0.8f;

    private final GraphViewportPanel mGraphViewportPanel;
    private final GraphPropertiesPanel mPropertiesPanel;
    private final EditorSidebar mRightSidebar;
    private final SidebarLayoutController mSidebarLayout;
    private final UiStateContext mUiStateContext;

    public GraphEditorWindow(Context context) {
        this(context, UiStateContext.ephemeral());
    }

    public GraphEditorWindow(Context context, UiStateContext uiStateContext) {
        super(context);
        mUiStateContext = java.util.Objects.requireNonNull(uiStateContext);
        setOrientation(LinearLayout.HORIZONTAL);
        setBackground(createColorDrawable(UiThemes.EDITOR.surface().root()));

        if (!mUiStateContext.contains(EditorSidebarState.GRAPH, EditorSidebarState.SCOPE)) {
            AppConfig.ViewportConfig viewportConfig = ConfigManager.INSTANCE.getConfig().viewport;
            mUiStateContext.write(EditorSidebarState.GRAPH, EditorSidebarState.SCOPE,
                    new EditorSidebarState(viewportConfig.rightSidebarVisible,
                            viewportConfig.rightSidebarWeight, viewportConfig.rightSidebarTab));
        }
        EditorSidebarState sidebarState = mUiStateContext.read(
                EditorSidebarState.GRAPH, EditorSidebarState.SCOPE);
        float sidebarWeight = sidebarState.weight();

        LinearLayout workspace = new LinearLayout(context);
        workspace.setOrientation(LinearLayout.HORIZONTAL);
        addView(workspace, createWeightParams(1.0f));

        mGraphViewportPanel = new GraphViewportPanel(context);
        workspace.addView(mGraphViewportPanel, createWeightParams(
                INITIAL_WORKSPACE_WEIGHT - sidebarWeight));

        View sidebarDivider = ThinResizableDivider.weighted(
                context, ThinResizableDivider.Orientation.HORIZONTAL, EditorLayoutPolicy.MIN_PANEL_WEIGHT, delta -> persistSidebarState());
        workspace.addView(sidebarDivider);

        mRightSidebar = new EditorSidebar(context);
        mRightSidebar.installRegisteredPanels(new SidebarPanelContext(
                context,
                SidebarPanelScope.GRAPH_EDITOR));
        mPropertiesPanel = mRightSidebar.requirePanel(
                GraphPropertiesPanel.PANEL_ID,
                GraphPropertiesPanel.class);
        mRightSidebar.restoreSelectedPanel(sidebarState.tab());
        workspace.addView(mRightSidebar, createWeightParams(sidebarWeight));

        mSidebarLayout = new SidebarLayoutController(
                workspace,
                mGraphViewportPanel,
                sidebarDivider,
                mRightSidebar,
                sidebarWeight,
                EditorLayoutPolicy.MIN_PANEL_WEIGHT,
                (visible, weight) -> mUiStateContext.write(
                        EditorSidebarState.GRAPH, EditorSidebarState.SCOPE,
                        new EditorSidebarState(visible, weight, mRightSidebar.getSelectedPanelId())));
        mRightSidebar.setOnCollapseRequested(() -> mSidebarLayout.setVisible(false, true));
        mRightSidebar.setOnSelectedPanelChanged(id -> persistSidebarState());
        mGraphViewportPanel.setSessionChangedListener(session -> mPropertiesPanel.bind(
                session != null ? new GraphSessionPropertiesTarget(session) : null));
        mGraphViewportPanel.setBeforeSessionSaveListener(session -> mPropertiesPanel.commitPendingEdits());
        mSidebarLayout.initialize(sidebarState.visible());
    }

    private void persistSidebarState() {
        mSidebarLayout.persistState();
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void bindSurfaceRegistration(UiSurfaceRegistry.Registration registration) {
        mGraphViewportPanel.setInteractionListener(registration::markInteracted);
    }

    @Override
    public GraphSession currentGraphSession() {
        return mGraphViewportPanel.currentSession();
    }

    public void openGraphSession(GraphSession session) {
        mGraphViewportPanel.openGraphSession(session);
    }

    @Override
    public List<GraphSession> openGraphSessions() {
        return DocumentManager.INSTANCE.getSessions().stream()
                .filter(session -> session != null && !session.fileReference().isDeleted())
                .toList();
    }

    @Override
    public void onShow() {
        mSidebarLayout.onOwnerShown();
        mGraphViewportPanel.activatePanel();
    }

    @Override
    public void onHide() {
        mSidebarLayout.onOwnerHidden();
        mSidebarLayout.persistState();
        mGraphViewportPanel.deactivatePanel();
    }

    @Override
    public void onDispose() {
        try {
            mPropertiesPanel.bind(null);
        } finally {
            try {
                onHide();
            } finally {
                mGraphViewportPanel.dispose();
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            KeyBinding saveBinding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_SAVE));
            if (saveBinding != null && UiInputBindings.matches(saveBinding, event)) {
                mPropertiesPanel.commitPendingEdits();
                mGraphViewportPanel.saveCurrentSession();
                return true;
            }

            if (findFocus() instanceof EditText) return super.dispatchKeyEvent(event);
            KeyBinding binding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.VIEWPORT_TOGGLE_SIDEBAR));
            if (binding != null && UiInputBindings.matches(binding, event)) {
                mSidebarLayout.toggle();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private LinearLayout.LayoutParams createWeightParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT);
        params.weight = weight;
        return params;
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(ShapeDrawable.RECTANGLE);
        drawable.setColor(color);
        return drawable;
    }

}
