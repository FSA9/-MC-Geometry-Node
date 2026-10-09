package com.mine.geometry_node.client.ui.components.common;

import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.widget.EditText;

/** Shared text-field visuals. Callers own filtering, validation and commit/cancel timing. */
public final class UiTextInput extends EditText {
    public static final Style DEFAULT_STYLE = new Style(
            UiTheme.Metrics.TEXT_CONTROL, 7.0f, UiTheme.Metrics.RADIUS_INPUT,
            UiTheme.Metrics.BORDER, UiTheme.Text.NORMAL, UiTheme.Text.HINT,
            UiTheme.Control.INPUT, UiTheme.Control.INPUT_BORDER);

    private final Style style;
    private CharSequence errorMessage;
    private CharSequence ordinaryTooltip;

    public UiTextInput(Context context) {
        this(context, DEFAULT_STYLE);
    }

    public UiTextInput(Context context, Style style) {
        super(context);
        this.style = style != null ? style : DEFAULT_STYLE;
        setTextColor(this.style.textColor());
        setHintTextColor(this.style.hintColor());
        UIUtils.setLockedTextSize(this, this.style.textSizeDp());
        setPadding(UIUtils.dp2pxInt(this.style.horizontalPaddingDp()), 0,
                UIUtils.dp2pxInt(this.style.horizontalPaddingDp()), 0);
        updateVisualState();
    }

    /** The caller supplies localized text; showing an error never edits or submits the draft. */
    public void setErrorMessage(CharSequence message) {
        errorMessage = message == null || message.length() == 0 ? null : message;
        super.setTooltipText(errorMessage != null ? errorMessage : ordinaryTooltip);
        updateVisualState();
    }

    public CharSequence getErrorMessage() {
        return errorMessage;
    }

    @Override
    public void setTooltipText(CharSequence tooltip) {
        ordinaryTooltip = tooltip;
        super.setTooltipText(errorMessage != null ? errorMessage : tooltip);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (style != null) updateVisualState();
    }

    private void updateVisualState() {
        setAlpha(isEnabled() ? 1.0f : UiTheme.Metrics.DISABLED_ALPHA);
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(style.backgroundColor());
        background.setCornerRadius(UIUtils.dp2px(style.cornerRadiusDp()));
        int border = errorMessage != null ? UiTheme.Control.ERROR
                : isEnabled() && isFocused() ? UiTheme.Control.FOCUS : style.borderColor();
        if (style.strokeWidthDp() > 0 || errorMessage != null || isEnabled() && isFocused()) {
            background.setStroke(Math.max(1, UIUtils.dp2pxInt(style.strokeWidthDp())), border);
        }
        setBackground(background);
    }

    public record Style(float textSizeDp, float horizontalPaddingDp, float cornerRadiusDp,
                        float strokeWidthDp, int textColor, int hintColor,
                        int backgroundColor, int borderColor) {}
}
