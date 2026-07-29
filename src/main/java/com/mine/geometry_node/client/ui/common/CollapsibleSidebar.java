package com.mine.geometry_node.client.ui.common;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

/**
 * 通用的可收起侧栏外壳，只负责标题栏和内容容器，不包含具体业务状态。
 */
public final class CollapsibleSidebar extends LinearLayout {
    private static final int COLOR_BACKGROUND = 0xFF303030;
    private static final int COLOR_HEADER = 0xFF292929;
    private static final int COLOR_HEADER_BORDER = 0xFF181818;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_BUTTON_HOVER = 0xFF454545;

    private final FrameLayout mContent;

    public CollapsibleSidebar(Context context, String title, Runnable onCollapse) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(rect(COLOR_BACKGROUND, 0.0f, 0, 0));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(5), 0);
        header.setBackground(rect(COLOR_HEADER, 0.0f, 1, COLOR_HEADER_BORDER));

        TextView titleView = label(context, title, 12.0f, COLOR_TEXT);
        header.addView(titleView, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView collapse = label(context, ">", 14.0f, 0xFFB8B8B8);
        collapse.setGravity(Gravity.CENTER);
        collapse.setBackground(rect(0x00000000, 3.0f, 0, 0));
        collapse.setOnHoverListener((v, event) -> {
            collapse.setBackground(rect(
                    event.getAction() == MotionEvent.ACTION_HOVER_ENTER ? COLOR_BUTTON_HOVER : 0x00000000,
                    3.0f, 0, 0));
            return false;
        });
        collapse.setOnClickListener(v -> {
            if (onCollapse != null) onCollapse.run();
        });
        header.addView(collapse, new LayoutParams(UIUtils.dp2pxInt(26), UIUtils.dp2pxInt(24)));
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

        mContent = new FrameLayout(context);
        addView(mContent, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
    }

    public void setContent(View content) {
        mContent.removeAllViews();
        if (content != null) {
            mContent.addView(content, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private static TextView label(Context context, String text, float sizeDp, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(0, UIUtils.dp2px(sizeDp));
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        return view;
    }

    private static ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        return drawable;
    }
}
