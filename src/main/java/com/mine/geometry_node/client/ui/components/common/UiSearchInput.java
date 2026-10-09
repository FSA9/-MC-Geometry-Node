package com.mine.geometry_node.client.ui.components.common;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.EditText;

import java.util.function.Consumer;

/** Shared visual style for search inputs; search execution remains owned by the caller. */
public final class UiSearchInput extends EditText {
    private Consumer<String> onQueryChanged;
    private final Style style;
    public static final Style DEFAULT_STYLE = new Style(
            UiTheme.Metrics.TEXT_SEARCH,
            10.0f,
            UiTheme.Metrics.RADIUS_INPUT,
            UiTheme.Metrics.BORDER,
            UiTheme.Text.NORMAL,
            UiTheme.Text.HINT,
            UiTheme.Control.INPUT,
            UiTheme.Control.INPUT_BORDER
    );

    public UiSearchInput(Context context) {
        this(context, DEFAULT_STYLE);
    }

    public UiSearchInput(Context context, Style style) {
        super(context);
        Style resolved = style != null ? style : DEFAULT_STYLE;
        this.style = resolved;
        setSingleLine(true);
        setGravity(Gravity.CENTER_VERTICAL);
        setTextColor(resolved.textColor());
        setHintTextColor(resolved.hintColor());
        setPadding(UIUtils.dp2pxInt(resolved.horizontalPaddingDp()), 0,
                UIUtils.dp2pxInt(resolved.horizontalPaddingDp()), 0);
        UIUtils.setLockedTextSize(this, resolved.textSizeDp());
        updateVisualState();
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Consumer<String> listener = onQueryChanged;
                if (listener != null) listener.accept(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /** Replaces the query listener; null clears it. Programmatic text changes still notify. */
    public void setOnQueryChanged(Consumer<String> listener) {
        onQueryChanged = listener;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (style != null) updateVisualState();
    }

    private void updateVisualState() {
        setAlpha(isEnabled() ? 1.0f : UiTheme.Metrics.DISABLED_ALPHA);
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(style.backgroundColor());
        drawable.setCornerRadius(UIUtils.dp2px(style.cornerRadiusDp()));
        if (isEnabled() && isFocused()) {
            drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(UiTheme.Metrics.BORDER)), UiTheme.Control.FOCUS);
        } else if (style.strokeWidthDp() > 0.0f) {
            drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(style.strokeWidthDp())), style.borderColor());
        }
        setBackground(drawable);
    }

    /** Visual parameters in density-independent units. */
    public record Style(
            float textSizeDp,
            float horizontalPaddingDp,
            float cornerRadiusDp,
            float strokeWidthDp,
            int textColor,
            int hintColor,
            int backgroundColor,
            int borderColor
    ) {
    }
}
