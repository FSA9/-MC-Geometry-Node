package com.mine.geometry_node.client.ui.viewport.toolbar;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;

final class ViewportToolStrip extends LinearLayout {
    private static final float GAP_DP = 2.0f;

    ViewportToolStrip(Context context) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER);
        setBackground(null);
        setClipChildren(false);
    }

    void addTool(View toolView, int sizeDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(sizeDp), UIUtils.dp2pxInt(sizeDp));
        if (getChildCount() > 0) {
            lp.leftMargin = UIUtils.dp2pxInt(GAP_DP);
        }
        addView(toolView, lp);
    }
}
