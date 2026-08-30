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
import com.mine.geometry_node.client.ui.persistence.session.EditorSessionState;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorWindow;
import com.mine.geometry_node.client.ui.workspace.area.SurfaceRegistrationAware;
import com.mine.geometry_node.client.ui.document.GraphSession;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import com.mine.geometry_node.client.ui.workspace.surface.ViewportSurface;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.RelativeLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

public class GraphEditorWindow extends LinearLayout
        implements AreaEditorWindow, SurfaceRegistrationAware, ViewportSurface {
    private final GraphViewportPanel mGraphViewportPanel;
    private final GraphPropertiesPanel mPropertiesPanel;
    private final EditorSidebar mRightSidebar;
    private final SidebarLayoutController mSidebarLayout;
    private final EditorSessionState.GraphEditorState mSessionState;
    private final Runnable mSessionChanged;
    private final View mLeftPanel;

    public GraphEditorWindow(Context context) {
        this(context, new EditorSessionState.GraphEditorState(), null);
    }

    public GraphEditorWindow(
            Context context,
            EditorSessionState.GraphEditorState sessionState,
            Runnable sessionChanged) {
        super(context);
        mSessionState = sessionState == null ? new EditorSessionState.GraphEditorState() : sessionState;
        mSessionChanged = sessionChanged;
        setOrientation(LinearLayout.HORIZONTAL);
        setBackground(createColorDrawable(UIConstants.MainUI.BG_ROOT));

        float outlinerWeight = sanitizeOutlinerWeight(mSessionState.outlinerWeight);
        mLeftPanel = createPanel(context, "Outliner", UIConstants.MainUI.BG_OUTLINER);
        addView(mLeftPanel, createWeightParams(outlinerWeight));

        addView(ResizableDivider.weighted(
                context, ResizableDivider.Orientation.HORIZONTAL, delta -> captureOutlinerWeight()));

        AppConfig.ViewportConfig viewportConfig = ConfigManager.INSTANCE.getConfig().viewport;
        float sidebarWeight = viewportConfig.rightSidebarWeight;

        LinearLayout workspace = new LinearLayout(context);
        workspace.setOrientation(LinearLayout.HORIZONTAL);
        addView(workspace, createWeightParams(1.0f - outlinerWeight));

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

    private RelativeLayout createPanel(Context context, String title, int colorHex) {
        RelativeLayout panel = new RelativeLayout(context);
        panel.setBackground(createColorDrawable(colorHex));

        TextView textView = new TextView(context);
        textView.setText(title);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, UIUtils.dp2px(UIConstants.MainUI.TEXT_SIZE));
        textView.setTextColor(UIConstants.MainUI.TEXT_COLOR);

        RelativeLayout.LayoutParams textParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        panel.addView(textView, textParams);
        return panel;
    }

    private LinearLayout.LayoutParams createWeightParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT);
        params.weight = weight;
        return params;
    }

    private void captureOutlinerWeight() {
        if (mLeftPanel.getLayoutParams() instanceof LinearLayout.LayoutParams params) {
            mSessionState.outlinerWeight = sanitizeOutlinerWeight(params.weight);
            if (mSessionChanged != null) {
                mSessionChanged.run();
            }
        }
    }

    private static float sanitizeOutlinerWeight(float weight) {
        return Float.isFinite(weight) ? Math.max(0.05f, Math.min(0.45f, weight)) : 0.2f;
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(ShapeDrawable.RECTANGLE);
        drawable.setColor(color);
        return drawable;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
