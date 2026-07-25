package com.mine.geometry_node.client.ui.viewport.toolbar;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.ConfigChangeListener;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionId;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionRegistry;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionRequest;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionSink;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;

public final class ViewportToolbar extends FrameLayout implements ViewportToolButton.TooltipHost {
    private static final int TOOL_BUTTON_SIZE_DP = 15;

    private ViewportActionSink mActionSink;
    private final ViewportToolStrip mToolStrip;
    private final ViewportToggleButton mSnapToggleButton;
    private final ViewportToggleButton mGridVisibilityButton;
    private final TextView mTooltip;
    private final ConfigChangeListener mConfigChangeListener = this::applyConfig;
    private View mTooltipAnchor;
    private boolean mConfigListenerRegistered = true;

    public ViewportToolbar(Context context, ViewportActionSink actionSink) {
        super(context);
        this.mActionSink = actionSink;
        setClipChildren(false);
        setWillNotDraw(true);

        mToolStrip = new ViewportToolStrip(context);
        LayoutParams stripLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        stripLp.gravity = Gravity.RIGHT | Gravity.TOP;
        addView(mToolStrip, stripLp);

        mSnapToggleButton = ViewportToggleButton.createSnap(context, this);
        mSnapToggleButton.setOnClickListener(v -> performAction(ViewportActionId.TOGGLE_SNAP_TO_GRID));
        mToolStrip.addTool(mSnapToggleButton, TOOL_BUTTON_SIZE_DP);

        mGridVisibilityButton = ViewportToggleButton.createGridVisibility(context, this);
        mGridVisibilityButton.setOnClickListener(v -> performAction(ViewportActionId.TOGGLE_GRID_AND_AXIS));
        mToolStrip.addTool(mGridVisibilityButton, TOOL_BUTTON_SIZE_DP);

        mTooltip = UIUtils.createLockedTextView(context, "", 10.0f, UIConstants.CLR_WHITE);
        mTooltip.setGravity(Gravity.LEFT | Gravity.TOP);
        mTooltip.setSingleLine(false);
        mTooltip.setHorizontallyScrolling(false);
        mTooltip.setMinLines(1);
        mTooltip.setPadding(UIUtils.dp2pxInt(7), UIUtils.dp2pxInt(5), UIUtils.dp2pxInt(7), UIUtils.dp2pxInt(5));
        mTooltip.setBackground(createRectDrawable(0xF0222222, 4.0f, 1, 0xFF555555));
        mTooltip.setVisibility(View.GONE);
        mTooltip.setEnabled(false);
        LayoutParams tooltipLp = new LayoutParams(UIUtils.dp2pxInt(120), LayoutParams.WRAP_CONTENT);
        tooltipLp.gravity = Gravity.RIGHT | Gravity.TOP;
        tooltipLp.topMargin = UIUtils.dp2pxInt(19);
        addView(mTooltip, tooltipLp);

        applyConfig(ConfigManager.INSTANCE.getConfig());
        ConfigManager.INSTANCE.addChangeListener(mConfigChangeListener);
    }

    public void setActionSink(ViewportActionSink actionSink) {
        mActionSink = actionSink;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        activateLifecycle();
    }

    @Override
    protected void onDetachedFromWindow() {
        deactivateLifecycle();
        super.onDetachedFromWindow();
    }

    public void activateLifecycle() {
        if (!mConfigListenerRegistered) {
            ConfigManager.INSTANCE.addChangeListener(mConfigChangeListener);
            applyConfig(ConfigManager.INSTANCE.getConfig());
            mConfigListenerRegistered = true;
        }
    }

    public void deactivateLifecycle() {
        if (mConfigListenerRegistered) {
            ConfigManager.INSTANCE.removeChangeListener(mConfigChangeListener);
            mConfigListenerRegistered = false;
        }
        hideTooltip();
    }

    public void setSnapToGridEnabled(boolean enabled) {
        setSnapToGridEnabled(enabled, true);
    }

    public void setSnapToGridEnabled(boolean enabled, boolean notifyListener) {
        if (mSnapToggleButton.isChecked() == enabled) return;
        mSnapToggleButton.setChecked(enabled);
    }

    public void setGridAndAxisVisible(boolean visible) {
        setGridAndAxisVisible(visible, true);
    }

    public void setGridAndAxisVisible(boolean visible, boolean notifyListener) {
        if (mGridVisibilityButton.isChecked() == visible) return;
        mGridVisibilityButton.setChecked(visible);
    }

    public void hideTooltip() {
        hideToolTooltip(mTooltipAnchor);
    }

    @Override
    public void showToolTooltip(View anchor, String text) {
        if (text == null || text.isBlank()) return;
        mTooltipAnchor = anchor;
        mTooltip.setText(text);
        mTooltip.setVisibility(View.VISIBLE);
        mTooltip.requestLayout();
        requestLayout();
    }

    @Override
    public void hideToolTooltip(View anchor) {
        if (anchor != null && mTooltipAnchor != anchor) return;
        mTooltipAnchor = null;
        mTooltip.setVisibility(View.GONE);
        requestLayout();
    }

    private static ShapeDrawable createRectDrawable(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) {
            drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private void applyConfig(AppConfig config) {
        if (config == null || config.keyBindings == null) return;
        mSnapToggleButton.setTooltipText(ViewportActionRegistry.label(ViewportActionId.TOGGLE_SNAP_TO_GRID)
                + " - " + ViewportActionRegistry.shortcutText(ViewportActionId.TOGGLE_SNAP_TO_GRID, config)
                + "\n开启后节点和图框会对齐网格");
        mGridVisibilityButton.setTooltipText(ViewportActionRegistry.label(ViewportActionId.TOGGLE_GRID_AND_AXIS)
                + " - " + ViewportActionRegistry.shortcutText(ViewportActionId.TOGGLE_GRID_AND_AXIS, config)
                + "\n点击显示或隐藏栅格和坐标轴");
    }

    private void performAction(ViewportActionId id) {
        if (mActionSink != null) {
            mActionSink.performAction(id, ViewportActionRequest.EMPTY);
        }
    }
}
