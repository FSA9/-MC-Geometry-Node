package com.mine.geometry_node.client.ui.components.display;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.TextView;

/** Noninteractive text: hover tooltips work, while pointer clicks belong to the containing row. */
public final class UiDisplayText extends TextView {
    public UiDisplayText(Context context, String text, float sizeDp, int color) {
        super(context);
        setText(text);
        UIUtils.setLockedTextSize(this, sizeDp);
        setTextColor(color);
    }

    @Override public boolean onTouchEvent(MotionEvent event) { return false; }
}
