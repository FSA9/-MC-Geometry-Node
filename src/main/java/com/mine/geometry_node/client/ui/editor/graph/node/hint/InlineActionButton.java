package com.mine.geometry_node.client.ui.editor.graph.node.hint;

import com.mine.geometry_node.client.ui.editor.graph.node.NodeUiMetrics;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;

public final class InlineActionButton extends UiActionButton {
    public static final float WIDTH_DP = 16.0f;

    public InlineActionButton(Context context, CharSequence text) {
        super(context, text, Role.INLINE, Density.COMPACT,
                new Style(2.0f, 1.0f, UiThemes.EDITOR.border().subtle(), UiMetrics.DISABLED_ALPHA));
        setMinWidth(0);
        setMinHeight(0);
        setMinimumWidth(0);
        setMinimumHeight(0);
        setPadding(0, 0, 0, 0);
        setSingleLine(true);
        UIUtils.setLockedTextSize(this, NodeUiMetrics.TEXT_SIZE_LABEL);
    }

    public static float heightDp() {
        return UIHintUtils.getStandardInputHeight();
    }

    public static int widthPx() {
        return UIUtils.dp2pxInt(WIDTH_DP);
    }

    public static int heightPx() {
        return UIUtils.dp2pxInt(heightDp());
    }

    public static int leftColumnLeftPx() {
        return UIUtils.dp2pxInt(NodeUiMetrics.LABEL_MARGIN_PORT);
    }

    public static int rightColumnLeftPx(int nodeWidthDp) {
        int contentWidthPx = UIUtils.dp2pxInt(nodeWidthDp - 2.0f * NodeUiMetrics.LABEL_MARGIN_PORT);
        return leftColumnLeftPx() + contentWidthPx - widthPx();
    }

    public static int centerColumnLeftPx(int nodeWidthDp) {
        return (UIUtils.dp2pxInt(nodeWidthDp) - widthPx()) / 2;
    }
}
