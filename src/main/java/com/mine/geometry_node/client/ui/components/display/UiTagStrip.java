package com.mine.geometry_node.client.ui.components.display;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.LinearLayout;
import java.util.List;

/** Read-only tags for fixed-height table rows; excess tags clip and remain available in the tooltip. */
public final class UiTagStrip extends LinearLayout {
    private List<String> tags = List.of();
    private String prefix = "";

    public UiTagStrip(Context context) {
        super(context);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(px(6), 0, px(4), 0);
    }

    public void setPrefix(String value) {
        prefix = java.util.Objects.requireNonNullElse(value, "");
        for (int i = 0; i < getChildCount(); i++) ((UiTagChip) getChildAt(i)).setPrefix(prefix);
    }

    public void setTags(List<String> values) {
        if (tags.equals(values)) return;
        tags = List.copyOf(values);
        removeAllViews();
        setTooltipText(String.join(", ", tags));
        for (String tag : tags) {
            UiTagChip chip = new UiTagChip(getContext(), tag);
            // The strip owns overflow disclosure, including tags clipped out of view.
            chip.setTooltipText(null);
            chip.setPrefix(prefix);
            chip.setEnabled(isEnabled());
            LayoutParams params = new LayoutParams(-2, px(UiTagChip.HEIGHT_DP));
            params.rightMargin = px(4);
            addView(chip, params);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) { return false; }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setEnabled(enabled);
    }

    private static int px(float value) { return UIUtils.dp2pxInt(value); }
}
