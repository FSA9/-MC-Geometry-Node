package com.mine.geometry_node.client.ui.editor.graph;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.common.CollapsibleSidebar;
import com.mine.geometry_node.client.ui.common.ResizableDivider;
import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.ViewportPanel;
import com.mine.geometry_node.client.ui.window.IToolWindow;
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

public class GraphEditorWindow extends LinearLayout implements IToolWindow {
    private final ViewportPanel mViewportPanel;
    private final GraphPropertiesPanel mPropertiesPanel;
    private final View mRightDivider;
    private final CollapsibleSidebar mRightSidebar;
    private float mLastRightSidebarWeight;

    public GraphEditorWindow(Context context) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        setBackground(createColorDrawable(UIConstants.MainUI.BG_ROOT));

        View leftPanel = createPanel(context, "Outliner", UIConstants.MainUI.BG_OUTLINER);
        addView(leftPanel, createWeightParams(UIConstants.MainUI.WEIGHT_LEFT));

        addView(ResizableDivider.weighted(context, ResizableDivider.Orientation.HORIZONTAL));

        AppConfig.ViewportConfig viewportConfig = ConfigManager.INSTANCE.getConfig().viewport;
        mLastRightSidebarWeight = viewportConfig.rightSidebarWeight;

        mViewportPanel = new ViewportPanel(context);
        addView(mViewportPanel, createWeightParams(
                UIConstants.MainUI.WEIGHT_CENTER + UIConstants.MainUI.WEIGHT_RIGHT - mLastRightSidebarWeight));

        mRightDivider = ResizableDivider.weighted(context, ResizableDivider.Orientation.HORIZONTAL);
        addView(mRightDivider);

        mPropertiesPanel = new GraphPropertiesPanel(context);
        mRightSidebar = new CollapsibleSidebar(
                context,
                tr("geometry_node.graph_properties.title"),
                () -> setRightSidebarVisible(false, true));
        mRightSidebar.setContent(mPropertiesPanel);
        addView(mRightSidebar, createWeightParams(mLastRightSidebarWeight));
        mViewportPanel.setSessionChangedListener(mPropertiesPanel::bindSession);
        mViewportPanel.setBeforeSessionSaveListener(mPropertiesPanel::commitPendingEdits);
        setRightSidebarVisible(viewportConfig.rightSidebarVisible, false);
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onShow() {
        mViewportPanel.activatePanel();
    }

    @Override
    public void onHide() {
        mPropertiesPanel.commitPendingEdits();
        persistSidebarState();
        mViewportPanel.deactivatePanel();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            KeyBinding saveBinding = KeyBinding.parse(
                    ConfigManager.INSTANCE.getConfig().keyBindings.global.save);
            if (saveBinding != null && saveBinding.matches(event)) {
                mPropertiesPanel.commitPendingEdits();
                mViewportPanel.saveCurrentSession();
                return true;
            }

            if (findFocus() instanceof EditText) return super.dispatchKeyEvent(event);
            KeyBinding binding = KeyBinding.parse(
                    ConfigManager.INSTANCE.getConfig().keyBindings.viewport.toggleRightSidebar);
            if (binding != null && binding.matches(event)) {
                setRightSidebarVisible(mRightSidebar.getVisibility() != View.VISIBLE, true);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void setRightSidebarVisible(boolean visible, boolean persist) {
        boolean currentlyVisible = mRightSidebar.getVisibility() == View.VISIBLE;
        if (currentlyVisible == visible) {
            if (persist) persistSidebarState();
            return;
        }

        if (!visible) {
            mPropertiesPanel.commitPendingEdits();
            rememberRightSidebarWeight();
            transferSidebarWeightToViewport(mLastRightSidebarWeight);
        } else {
            transferSidebarWeightToViewport(-mLastRightSidebarWeight);
        }
        mRightDivider.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        mRightSidebar.setVisibility(visible ? View.VISIBLE : View.GONE);
        requestLayout();
        if (persist) persistSidebarState();
    }

    private void transferSidebarWeightToViewport(float sidebarWeightDelta) {
        if (!(mViewportPanel.getLayoutParams() instanceof LinearLayout.LayoutParams viewportParams)) return;
        viewportParams.weight = Math.max(
                UIConstants.MainUI.WEIGHT_MIN,
                viewportParams.weight + sidebarWeightDelta);
        mViewportPanel.setLayoutParams(viewportParams);
    }

    private void rememberRightSidebarWeight() {
        if (mRightSidebar.getLayoutParams() instanceof LinearLayout.LayoutParams params && params.weight > 0.0f) {
            mLastRightSidebarWeight = params.weight;
        }
    }

    private void persistSidebarState() {
        rememberRightSidebarWeight();
        boolean visible = mRightSidebar.getVisibility() == View.VISIBLE;
        float weight = mLastRightSidebarWeight;
        ConfigManager.INSTANCE.update(config -> {
            config.viewport.rightSidebarVisible = visible;
            config.viewport.rightSidebarWeight = weight;
        });
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
