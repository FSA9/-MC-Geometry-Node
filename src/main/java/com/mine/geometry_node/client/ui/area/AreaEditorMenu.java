package com.mine.geometry_node.client.ui.area;

import com.mine.geometry_node.client.ui.common.SvgIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.function.Consumer;

final class AreaEditorMenu extends FrameLayout {
    private static final float MENU_WIDTH_DP = 205.0f;
    private static final float MENU_EDGE_MARGIN_DP = 6.0f;
    private static final float MENU_PADDING_DP = 8.0f;
    private static final float ROW_HEIGHT_DP = 24.0f;
    private static final float ROW_RADIUS_DP = 4.0f;
    private static final float ICON_SIZE_DP = 18.0f;
    private static final float SECTION_HEIGHT_DP = 18.0f;
    private static final float DIVIDER_MARGIN_Y_DP = 6.0f;

    private final LinearLayout mPanel;
    private final AreaEditorType mCurrentType;
    private final Consumer<AreaEditorType> mOnSelect;
    private final Runnable mOnDismiss;

    AreaEditorMenu(Context context, AreaEditorType currentType, Consumer<AreaEditorType> onSelect, Runnable onDismiss) {
        super(context);
        mCurrentType = currentType;
        mOnSelect = onSelect;
        mOnDismiss = onDismiss;
        setZ(20.0f);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setOnClickListener(v -> dismiss());

        mPanel = new LinearLayout(context);
        mPanel.setOrientation(LinearLayout.VERTICAL);
        int padding = UIUtils.dp2pxInt(MENU_PADDING_DP);
        mPanel.setPadding(
                padding,
                padding,
                padding,
                padding
        );
        mPanel.setBackground(AreaStyle.rounded(AreaStyle.COLOR_MENU_BG, 4.0f, 1, AreaStyle.COLOR_MENU_BORDER));
        mPanel.setOnClickListener(v -> {
        });

        addSectionLabel(context, "窗口类型");
        addDivider(context);
        for (AreaEditorType type : AreaEditorType.values()) {
            mPanel.addView(createRow(context, type), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    UIUtils.dp2pxInt(ROW_HEIGHT_DP)));
        }
    }

    void showBelow(View anchor, ViewGroup host) {
        if (anchor == null || host == null) {
            return;
        }
        dismissExistingParent();

        host.addView(this, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        int[] anchorLoc = new int[2];
        int[] hostLoc = new int[2];
        anchor.getLocationOnScreen(anchorLoc);
        host.getLocationOnScreen(hostLoc);

        int width = UIUtils.dp2pxInt(MENU_WIDTH_DP);
        int edge = UIUtils.dp2pxInt(MENU_EDGE_MARGIN_DP);
        int maxX = Math.max(edge, host.getWidth() - width - edge);
        int localX = Math.max(edge, Math.min(maxX, anchorLoc[0] - hostLoc[0]));
        int localY = Math.max(edge, anchorLoc[1] - hostLoc[1] + anchor.getHeight());

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.TOP | Gravity.LEFT;
        panelParams.setMargins(localX, localY, 0, 0);
        addView(mPanel, panelParams);

        mPanel.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        int measuredHeight = Math.max(mPanel.getMeasuredHeight(), UIUtils.dp2pxInt(ROW_HEIGHT_DP));
        if (host.getHeight() > 0 && localY + measuredHeight + edge > host.getHeight()) {
            int aboveY = anchorLoc[1] - hostLoc[1] - measuredHeight;
            panelParams.topMargin = Math.max(edge, aboveY);
            mPanel.setLayoutParams(panelParams);
        }
    }

    void dismiss() {
        dismissExistingParent();
        if (mOnDismiss != null) {
            mOnDismiss.run();
        }
    }

    private LinearLayout createRow(Context context, AreaEditorType type) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UIUtils.dp2pxInt(10.0f), 0, UIUtils.dp2pxInt(10.0f), 0);
        row.setBackground(type == mCurrentType
                ? AreaStyle.rounded(AreaStyle.COLOR_MENU_SELECTED, ROW_RADIUS_DP, 0, 0)
                : null);

        SvgIconView icon = new SvgIconView(context, type.icon(),
                type == mCurrentType ? AreaStyle.COLOR_ICON_SELECTED : AreaStyle.COLOR_ICON);
        row.addView(icon, new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(ICON_SIZE_DP),
                UIUtils.dp2pxInt(ICON_SIZE_DP)));

        TextView label = UIUtils.createLockedTextView(context, type.displayName(), 12.0f,
                type == mCurrentType ? AreaStyle.COLOR_ICON_SELECTED : AreaStyle.COLOR_TEXT);
        label.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.0f);
        labelParams.leftMargin = UIUtils.dp2pxInt(8.0f);
        row.addView(label, labelParams);

        if (type == mCurrentType) {
            TextView marker = UIUtils.createLockedTextView(context, "ON", 9.0f, AreaStyle.COLOR_ACCENT);
            marker.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            row.addView(marker, new LinearLayout.LayoutParams(
                    UIUtils.dp2pxInt(20.0f),
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        row.setOnClickListener(v -> {
            if (mOnSelect != null) {
                mOnSelect.accept(type);
            }
            dismiss();
        });
        row.setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                row.setBackground(AreaStyle.rounded(type == mCurrentType
                        ? AreaStyle.COLOR_MENU_SELECTED
                        : AreaStyle.COLOR_BUTTON_BG_HOVER,
                        ROW_RADIUS_DP,
                        0,
                        0));
                label.setTextColor(AreaStyle.COLOR_ICON_SELECTED);
                icon.setIconColor(AreaStyle.COLOR_ICON_SELECTED);
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                row.setBackground(type == mCurrentType
                        ? AreaStyle.rounded(AreaStyle.COLOR_MENU_SELECTED, ROW_RADIUS_DP, 0, 0)
                        : null);
                label.setTextColor(type == mCurrentType ? AreaStyle.COLOR_ICON_SELECTED : AreaStyle.COLOR_TEXT);
                icon.setIconColor(type == mCurrentType ? AreaStyle.COLOR_ICON_SELECTED : AreaStyle.COLOR_ICON);
            }
            return false;
        });
        return row;
    }

    private void addSectionLabel(Context context, String text) {
        TextView label = UIUtils.createLockedTextView(context, text, 10.0f, AreaStyle.COLOR_MENU_SECTION_TEXT);
        label.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        label.setPadding(UIUtils.dp2pxInt(10.0f), 0, UIUtils.dp2pxInt(10.0f), 0);
        mPanel.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(SECTION_HEIGHT_DP)));
    }

    private void addDivider(Context context) {
        View divider = new View(context);
        divider.setBackground(AreaStyle.rect(AreaStyle.COLOR_MENU_DIVIDER));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, UIUtils.dp2pxInt(1.0f)));
        params.setMargins(0, UIUtils.dp2pxInt(DIVIDER_MARGIN_Y_DP), 0, UIUtils.dp2pxInt(DIVIDER_MARGIN_Y_DP));
        mPanel.addView(divider, params);
    }

    private void dismissExistingParent() {
        if (getParent() instanceof ViewGroup parent) {
            parent.removeView(this);
        }
    }
}
