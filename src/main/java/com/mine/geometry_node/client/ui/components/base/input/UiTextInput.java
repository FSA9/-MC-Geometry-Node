package com.mine.geometry_node.client.ui.components.base.input;

import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.widget.EditText;

/** Shared text-field visuals. Callers own filtering, validation and commit/cancel timing. */
public class UiTextInput extends EditText {
    public static final Style DEFAULT_STYLE = new Style(
            UiTheme.Metrics.TEXT_CONTROL, 7.0f, UiTheme.Metrics.RADIUS_INPUT,
            UiTheme.Metrics.BORDER, UiTheme.Text.NORMAL, UiTheme.Text.HINT,
            UiTheme.Control.INPUT, UiTheme.Control.INPUT_BORDER);

    private final Style style;
    private final ShapeDrawable background = new ShapeDrawable();
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
        background.setColor(this.style.backgroundColor());
        background.setCornerRadius(UIUtils.dp2px(this.style.cornerRadiusDp()));
        setBackground(background);
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

    /** Cancels native hover callbacks without changing validation or the caller's ordinary hint. */
    public final void clearTooltipPresentation() {
        super.setTooltipText(null);
        super.setTooltipText(errorMessage != null ? errorMessage : ordinaryTooltip);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (style != null) updateVisualState();
    }

    private void updateVisualState() {
        setAlpha(isEnabled() ? 1.0f : UiTheme.Metrics.DISABLED_ALPHA);
        int border = errorMessage != null ? UiTheme.Control.ERROR
                : isEnabled() && isFocused() ? UiTheme.Control.FOCUS : style.borderColor();
        int strokeWidth = style.strokeWidthDp() > 0 || errorMessage != null || isEnabled() && isFocused()
                ? Math.max(1, UIUtils.dp2pxInt(style.strokeWidthDp())) : 0;
        background.setStroke(strokeWidth, border);
    }

    /** Shared visual parameters in project dp; also available as UiSearchInput.Style. */
    public record Style(float textSizeDp, float horizontalPaddingDp, float cornerRadiusDp,
                        float strokeWidthDp, int textColor, int hintColor,
                        int backgroundColor, int borderColor) {}
}
