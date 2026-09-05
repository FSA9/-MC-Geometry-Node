package com.mine.geometry_node.client.ui.editor.graph;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.components.common.ResizableDivider;
import com.mine.geometry_node.client.ui.components.sidebar.EditorSidebar;
import com.mine.geometry_node.client.ui.components.sidebar.SidebarLayoutController;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelContext;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelScope;
import com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.GraphPropertiesPanel;
import com.mine.geometry_node.client.ui.editor.graph.properties.session.GraphSessionPropertiesTarget;
import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;
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
    private final GraphViewportPanel mGraphViewportPanel;
    private final GraphPropertiesPanel mPropertiesPanel;
    private final EditorSidebar mRightSidebar;
    private final SidebarLayoutController mSidebarLayout;

    public GraphEditorWindow(Context context) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        setBackground(createColorDrawable(UIConstants.MainUI.BG_ROOT));

        AppConfig.ViewportConfig viewportConfig = ConfigManager.INSTANCE.getConfig().viewport;
        float sidebarWeight = viewportConfig.rightSidebarWeight;

        LinearLayout workspace = new LinearLayout(context);
        workspace.setOrientation(LinearLayout.HORIZONTAL);
        addView(workspace, createWeightParams(1.0f));

        mGraphViewportPanel = new GraphViewportPanel(context);
        workspace.addView(mGraphViewportPanel, createWeightParams(
                UIConstants.MainUI.WEIGHT_CENTER + UIConstants.MainUI.WEIGHT_RIGHT - sidebarWeight));

        View sidebarDivider = ResizableDivider.weighted(context, ResizableDivider.Orientation.HORIZONTAL);
        workspace.addView(sidebarDivider);

        mRightSidebar = new EditorSidebar(context);
        mRightSidebar.installRegisteredPanels(new SidebarPanelContext(
                context,
                SidebarPanelScope.GRAPH_EDITOR));
        mPropertiesPanel = mRightSidebar.requirePanel(
                GraphPropertiesPanel.PANEL_ID,
                GraphPropertiesPanel.class);
        mRightSidebar.restoreSelectedPanel(viewportConfig.rightSidebarTab);
        workspace.addView(mRightSidebar, createWeightParams(sidebarWeight));

        mSidebarLayout = new SidebarLayoutController(
                workspace,
                mGraphViewportPanel,
                sidebarDivider,
                mRightSidebar,
                sidebarWeight,
                (visible, weight) -> ConfigManager.INSTANCE.update(config -> {
                    config.viewport.rightSidebarVisible = visible;
                    config.viewport.rightSidebarWeight = weight;
                    config.viewport.rightSidebarTab = mRightSidebar.getSelectedPanelId();
                }));
        mRightSidebar.setOnCollapseRequested(() -> mSidebarLayout.setVisible(false, true));
        mRightSidebar.setOnSelectedPanelChanged(id -> ConfigManager.INSTANCE.update(
                config -> config.viewport.rightSidebarTab = id));
        mGraphViewportPanel.setSessionChangedListener(session -> mPropertiesPanel.bind(
                session != null ? new GraphSessionPropertiesTarget(session) : null));
        mGraphViewportPanel.setBeforeSessionSaveListener(session -> mPropertiesPanel.commitPendingEdits());
        mSidebarLayout.initialize(viewportConfig.rightSidebarVisible);
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
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            KeyBinding saveBinding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_SAVE));
            if (saveBinding != null && saveBinding.matches(event)) {
                mPropertiesPanel.commitPendingEdits();
                mGraphViewportPanel.saveCurrentSession();
                return true;
            }

            if (findFocus() instanceof EditText) return super.dispatchKeyEvent(event);
            KeyBinding binding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.VIEWPORT_TOGGLE_SIDEBAR));
            if (binding != null && binding.matches(event)) {
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
