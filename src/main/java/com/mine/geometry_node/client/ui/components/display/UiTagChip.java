package com.mine.geometry_node.client.ui.components.display;

import com.mine.geometry_node.client.ui.components.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.icon.SvgIconView;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.LinearLayout;
import java.util.Objects;

/** Shared tag presentation. Read-only by default; callbacks belong to the owning editor. */
public final class UiTagChip extends LinearLayout {
    public static final int HEIGHT_DP = 24;
    private final String tag;
    private final UiDisplayText label;
    private final ShapeDrawable fill = new ShapeDrawable();
    private UiIconButton removeButton;
    private Runnable onRemove;

    public UiTagChip(Context context, String tag) {
        super(context);
        this.tag = Objects.requireNonNull(tag, "tag");
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(px(HEIGHT_DP));
        setPadding(px(6), 0, px(6), 0);
        fill.setCornerRadius(UIUtils.dp2px(UiTheme.Metrics.RADIUS_SMALL));
        setBackground(fill);
        label = new UiDisplayText(context, tag, UiTheme.Metrics.TEXT_COMPACT, UiTheme.Text.SECONDARY);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        // Shrink the text first in narrow containers, retaining the fixed remove action.
        addView(label, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, 1));
        setTooltipText(tag);
        setContentDescription(tag);
        refreshState();
    }

    public String getTagValue() { return tag; }

    /** A visual prefix only; no tag normalization or mutation occurs here. */
    public void setPrefix(String prefix) {
        label.setText(Objects.requireNonNullElse(prefix, "") + tag);
    }

    /** Null action removes the delete affordance. The chip never edits the owner's collection. */
    public void setOnRemove(CharSequence description, Runnable action) {
        onRemove = action;
        if (action != null && removeButton == null) {
            var style = new UiIconButton.Style(1, 0, 0, UiTheme.Control.ICON_HOVER,
                    UiTheme.Control.ICON_PRESSED, 0, 0, 0, UiTheme.Text.SECONDARY,
                    UiTheme.Text.DISABLED, 20, 20, 12, UiTheme.Metrics.DISABLED_ALPHA);
            removeButton = new UiIconButton(getContext(),
                    new SvgIconView(getContext(), SvgIconView.Icon.CLOSE, UiTheme.Text.SECONDARY), style);
            removeButton.setOnClickListener(view -> {
                if (isEnabled() && onRemove != null) onRemove.run();
            });
            LayoutParams params = new LayoutParams(px(20), px(20));
            params.leftMargin = px(3);
            addView(removeButton, params);
        }
        if (removeButton != null) {
            removeButton.setContentDescription(description);
            removeButton.setVisibility(action == null ? GONE : VISIBLE);
            removeButton.setEnabled(isEnabled() && action != null);
        }
        setPadding(px(6), 0, px(action == null ? 6 : 2), 0);
    }

    @Override public void setOnClickListener(OnClickListener listener) {
        super.setOnClickListener(listener);
        setClickable(listener != null);
        setFocusable(listener != null);
        refreshState();
    }
    @Override public boolean performClick() { return isEnabled() && isClickable() && super.performClick(); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        return isClickable() && isEnabled() && super.onTouchEvent(event);
    }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (removeButton != null) removeButton.setEnabled(enabled && onRemove != null);
        refreshState();
    }
    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        refreshState();
    }
    private void refreshState() {
        if (fill == null || label == null) return;
        boolean interactive = isEnabled() && isClickable();
        fill.setColor(interactive && isPressed() ? UiTheme.Control.ICON_PRESSED
                : interactive && isHovered() ? UiTheme.Control.ICON_HOVER : UiTheme.Surface.HEADER);
        fill.setStroke(Math.max(1, px(1)), interactive && isFocused() ? UiTheme.Control.FOCUS : UiTheme.Control.INPUT_BORDER);
        label.setTextColor(isEnabled() ? UiTheme.Text.SECONDARY : UiTheme.Text.DISABLED);
    }
    private static int px(float value) { return UIUtils.dp2pxInt(value); }
}
