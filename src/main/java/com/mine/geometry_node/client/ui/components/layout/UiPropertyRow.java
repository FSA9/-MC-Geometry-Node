package com.mine.geometry_node.client.ui.components.layout;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;

/** Only lays out a key, a value/control and an optional trailing copy/action slot. */
public final class UiPropertyRow extends LinearLayout {
    private static final float MIN_VALUE_WIDTH_DP = 40;
    private final Layout layout;
    private final View value;
    private final LinearLayout.LayoutParams keyParams;
    private final LinearLayout.LayoutParams actionParams;

    public UiPropertyRow(Context context, View key, View value, View action, Layout layout) {
        this(context, key, value, action, layout,
                new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(layout.heightDp()), 1));
    }

    public UiPropertyRow(Context context, View key, View value, View action, Layout layout,
                         LinearLayout.LayoutParams valueParams) {
        super(context);
        this.layout = layout;
        this.value = value;
        setOrientation(HORIZONTAL);
        setBaselineAligned(false);
        setGravity(Gravity.CENTER_VERTICAL);
        keyParams = new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(layout.keyWidthDp()), UIUtils.dp2pxInt(layout.heightDp()));
        keyParams.rightMargin = UIUtils.dp2pxInt(layout.valueGapDp());
        addView(key, keyParams);
        addView(value, valueParams);
        if (valueParams.weight == 0) {
            addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1));
        }
        if (action != null) {
            actionParams = new LinearLayout.LayoutParams(
                    UIUtils.dp2pxInt(layout.actionSizeDp()), UIUtils.dp2pxInt(layout.actionSizeDp()));
            actionParams.leftMargin = UIUtils.dp2pxInt(layout.actionGapDp());
            addView(action, actionParams);
        } else {
            actionParams = null;
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int keyWidth = UIUtils.dp2pxInt(layout.keyWidthDp());
        int valueGap = UIUtils.dp2pxInt(layout.valueGapDp());
        int actionGap = actionParams == null ? 0 : UIUtils.dp2pxInt(layout.actionGapDp());
        if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
            int available = Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
            // Budget for the copy slot even while it is GONE so checkbox and text values align.
            int actionWidth = actionParams == null ? 0 : actionParams.width;
            LinearLayout.LayoutParams valueParams = (LinearLayout.LayoutParams) value.getLayoutParams();
            int minimumValue = Math.max(UIUtils.dp2pxInt(MIN_VALUE_WIDTH_DP), Math.max(0, valueParams.width))
                    + valueParams.leftMargin + valueParams.rightMargin;
            int spare = Math.max(0, available - actionWidth - minimumValue);
            actionGap = Math.min(actionGap, spare);
            valueGap = Math.min(valueGap, spare - actionGap);
            keyWidth = Math.min(keyWidth, spare - actionGap - valueGap);
        }
        // Update the native layout's inputs without scheduling another measurement from onMeasure.
        keyParams.width = keyWidth;
        keyParams.rightMargin = valueGap;
        if (actionParams != null) actionParams.leftMargin = actionGap;
        super.onMeasure(widthSpec, heightSpec);
    }

    /** All dimensions follow the project's UIUtils density. */
    public record Layout(float keyWidthDp, float heightDp, float valueGapDp,
                         float actionSizeDp, float actionGapDp) {}
}
