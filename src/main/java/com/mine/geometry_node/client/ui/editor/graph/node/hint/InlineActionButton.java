package com.mine.geometry_node.client.ui.editor.graph.node.hint;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.TextView;

public final class InlineActionButton extends TextView {
    public static final float WIDTH_DP = 16.0f;

    public InlineActionButton(Context context, CharSequence text) {
        super(context);
        setText(text);
        setGravity(Gravity.CENTER);
        setTextColor(0xFFBFC7D5);
        UIUtils.setLockedTextSize(this, UIConstants.Node.TEXT_SIZE_LABEL);

        ShapeDrawable background = new ShapeDrawable();
        background.setColor(0xFF30343B);
        background.setCornerRadius(UIUtils.dp2px(2.0f));
        background.setStroke(UIUtils.dp2pxInt(1), 0xFF424956);
        setBackground(background);
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
        return UIUtils.dp2pxInt(UIConstants.Node.LABEL_MARGIN_PORT);
    }

    public static int rightColumnLeftPx(int nodeWidthDp) {
        int contentWidthPx = UIUtils.dp2pxInt(nodeWidthDp - 2.0f * UIConstants.Node.LABEL_MARGIN_PORT);
        return leftColumnLeftPx() + contentWidthPx - widthPx();
    }

    public static int centerColumnLeftPx(int nodeWidthDp) {
        return (UIUtils.dp2pxInt(nodeWidthDp) - widthPx()) / 2;
    }
}
