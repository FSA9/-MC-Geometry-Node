package com.mine.geometry_node.client.ui.components.display;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import java.util.List;

/** Read-only tags for fixed-height table rows; excess tags clip and remain available in the tooltip. */
public final class UiTagStrip extends LinearLayout {
    private List<String> tags = List.of();

    public UiTagStrip(Context context) {
        super(context);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(px(6), 0, px(4), 0);
    }

    public void setTags(List<String> values) {
        if (tags.equals(values)) return;
        tags = List.copyOf(values);
        removeAllViews();
        setTooltipText(String.join(", ", tags));
        for (String tag : tags) {
            TextView chip = UIUtils.createLockedTextView(getContext(), tag, 10, UiTheme.Text.SECONDARY);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(px(5), 0, px(5), 0);
            ShapeDrawable fill = new ShapeDrawable();
            fill.setColor(UiTheme.Surface.HEADER);
            fill.setStroke(Math.max(1, px(1)), UiTheme.Control.INPUT_BORDER);
            chip.setBackground(fill);
            LayoutParams params = new LayoutParams(-2, px(21));
            params.rightMargin = px(4);
            addView(chip, params);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) { return false; }

    private static int px(float value) { return UIUtils.dp2pxInt(value); }
}
