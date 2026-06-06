package com.mine.geometry_node.client.ui.viewport.toolbar;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.ConfigChangeListener;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;

public final class ViewportToolbar extends FrameLayout implements ViewportToolButton.TooltipHost {
    private static final int TOOL_BUTTON_SIZE_DP = 15;

    public interface Listener {
        void onSnapToGridChanged(boolean enabled);
        void onGridAndAxisVisibilityChanged(boolean visible);
    }

    private final Listener mListener;
    private final ViewportToolStrip mToolStrip;
    private final SnapToggleView mSnapToggleView;
    private final GridVisibilityToggleView mGridVisibilityToggleView;
    private final TextView mTooltip;
    private final ConfigChangeListener mConfigChangeListener = this::applyConfig;
    private View mTooltipAnchor;

    public ViewportToolbar(Context context, Listener listener) {
        super(context);
        this.mListener = listener;
        setClipChildren(false);
        setWillNotDraw(true);

        mToolStrip = new ViewportToolStrip(context);
        LayoutParams stripLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        stripLp.gravity = Gravity.RIGHT | Gravity.TOP;
        addView(mToolStrip, stripLp);

        mSnapToggleView = new SnapToggleView(context, this);
        mSnapToggleView.setOnClickListener(v -> setSnapToGridEnabled(!mSnapToggleView.isSnapEnabled()));
        mToolStrip.addTool(mSnapToggleView, TOOL_BUTTON_SIZE_DP);

        mGridVisibilityToggleView = new GridVisibilityToggleView(context, this);
        mGridVisibilityToggleView.setOnClickListener(v -> setGridAndAxisVisible(!mGridVisibilityToggleView.isGridVisible()));
        mToolStrip.addTool(mGridVisibilityToggleView, TOOL_BUTTON_SIZE_DP);

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

    @Override
    protected void onDetachedFromWindow() {
        ConfigManager.INSTANCE.removeChangeListener(mConfigChangeListener);
        super.onDetachedFromWindow();
    }

    public void setSnapToGridEnabled(boolean enabled) {
        setSnapToGridEnabled(enabled, true);
    }

    public void setSnapToGridEnabled(boolean enabled, boolean notifyListener) {
        if (mSnapToggleView.isSnapEnabled() == enabled) return;
        mSnapToggleView.setSnapEnabled(enabled);
        if (notifyListener && mListener != null) mListener.onSnapToGridChanged(enabled);
    }

    public void setGridAndAxisVisible(boolean visible) {
        setGridAndAxisVisible(visible, true);
    }

    public void setGridAndAxisVisible(boolean visible, boolean notifyListener) {
        if (mGridVisibilityToggleView.isGridVisible() == visible) return;
        mGridVisibilityToggleView.setGridVisible(visible);
        if (notifyListener && mListener != null) mListener.onGridAndAxisVisibilityChanged(visible);
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
        mSnapToggleView.setTooltipText("吸附 - " + config.keyBindings.toggleSnapToGrid + "\n开启后节点和图框会对齐网格");
        mGridVisibilityToggleView.setTooltipText("坐标轴 - " + config.keyBindings.toggleGridAndAxis + "\n点击显示或隐藏栅格和坐标轴");
    }
}
