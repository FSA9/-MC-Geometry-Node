package com.mine.geometry_node.client.ui.components.base.input;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.ViewParent;
import icyllis.modernui.widget.EditText;

/** Shared text-field visuals and gesture ownership. Callers own validation and edit actions. */
public class UiTextInput extends EditText {
    public static final Style DEFAULT_STYLE = Style.forTheme(UiThemes.EDITOR);

    private final Style style;
    private final ShapeDrawable background = new ShapeDrawable();
    private CharSequence errorMessage;
    private CharSequence ordinaryTooltip;
    private ViewParent pointerParent;
    private Runnable confirmEdit;
    private Runnable cancelEdit;
    private boolean singleLineInput;
    private int finishingKey = -1;

    public UiTextInput(Context context) {
        this(context, DEFAULT_STYLE);
    }

    public static UiTextInput createThemed(Context context, UiTheme theme) {
        return new UiTextInput(context, Style.forTheme(theme));
    }

    public UiTextInput(Context context, Style style) {
        super(context);
        this.style = style != null ? style : DEFAULT_STYLE;
        setTextColor(this.style.textColor());
        setHintTextColor(this.style.hintColor());
        setHighlightColor(this.style.selectionColor());
        UIUtils.setLockedTextSize(this, this.style.textSizeDp());
        setPadding(UIUtils.dp2pxInt(this.style.horizontalPaddingDp()), 0,
                UIUtils.dp2pxInt(this.style.horizontalPaddingDp()), 0);
        background.setColor(this.style.backgroundColor());
        background.setCornerRadius(UIUtils.dp2px(this.style.cornerRadiusDp()));
        setBackground(background);
        updateVisualState();
    }

    /** Optional edit actions; ordinary key listeners (search, tags, overlays) retain first priority. */
    public void setEditActions(Runnable confirm, Runnable cancel) {
        confirmEdit = confirm;
        cancelEdit = cancel;
    }

    @Override
    public void setSingleLine(boolean singleLine) {
        singleLineInput = singleLine;
        super.setSingleLine(singleLine);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean enter = singleLineInput && (keyCode == KeyEvent.KEY_ENTER || keyCode == KeyEvent.KEY_KP_ENTER);
        Runnable action = keyCode == KeyEvent.KEY_ESCAPE ? cancelEdit : enter ? confirmEdit : null;
        if (isEnabled() && isFocused() && action != null) {
            finishingKey = keyCode;
            if (event.getRepeatCount() == 0) action.run();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == finishingKey) {
            finishingKey = -1;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            releasePointer();
            if (isEnabled() && getParent() != null) {
                pointerParent = getParent();
                pointerParent.requestDisallowInterceptTouchEvent(true);
            }
        }
        boolean handled = false;
        try {
            handled = super.dispatchTouchEvent(event);
            return handled;
        } finally {
            int action = event.getActionMasked();
            if (!handled || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) releasePointer();
        }
    }

    private void releasePointer() {
        ViewParent parent = pointerParent;
        pointerParent = null;
        if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        if (!focused) releasePointer();
        super.onWindowFocusChanged(focused);
    }

    @Override
    protected void onDetachedFromWindow() {
        releasePointer();
        finishingKey = -1;
        super.onDetachedFromWindow();
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
        if (!isEnabled()) releasePointer();
        if (style != null) updateVisualState();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        if (!focused) releasePointer();
        super.onFocusChanged(focused, direction, previous);
    }

    @Override
    protected void onVisibilityChanged(icyllis.modernui.view.View changedView, int visibility) {
        if (visibility != VISIBLE) releasePointer();
        super.onVisibilityChanged(changedView, visibility);
    }

    private void updateVisualState() {
        setAlpha(isEnabled() ? 1.0f : style.disabledAlpha());
        int border = errorMessage != null ? style.errorColor()
                : isEnabled() && isFocused() ? style.focusColor() : style.borderColor();
        int strokeWidth = style.strokeWidthDp() > 0 || errorMessage != null || isEnabled() && isFocused()
                ? Math.max(1, UIUtils.dp2pxInt(style.strokeWidthDp())) : 0;
        background.setStroke(strokeWidth, border);
    }

    /** Shared visual parameters in project dp; also available as UiSearchInput.Style. */
    public record Style(float textSizeDp, float horizontalPaddingDp, float cornerRadiusDp,
                        float strokeWidthDp, int textColor, int hintColor,
                        int backgroundColor, int borderColor, int focusColor, int errorColor,
                        int selectionColor, float disabledAlpha) {
        public static Style forTheme(UiTheme theme) {
            return forTheme(theme, UiMetrics.TEXT_CONTROL);
        }

        public static Style forTheme(UiTheme theme, float textSizeDp) {
            return forTheme(theme, textSizeDp, 7.0f, UiMetrics.RADIUS_INPUT, UiMetrics.BORDER);
        }

        public static Style forTheme(UiTheme theme, float textSizeDp, float horizontalPaddingDp,
                                     float cornerRadiusDp, float strokeWidthDp) {
            return new Style(textSizeDp, horizontalPaddingDp, cornerRadiusDp, strokeWidthDp,
                    theme.text().normal(), theme.text().hint(), theme.control().input(), theme.border().subtle(),
                    theme.border().focus(), theme.status().errorBorder(), theme.selection().text(), UiMetrics.DISABLED_ALPHA);
        }

        /** Existing callers retain theme defaults for interaction colors. Zero is a valid color. */
        public Style(float textSizeDp, float horizontalPaddingDp, float cornerRadiusDp,
                     float strokeWidthDp, int textColor, int hintColor,
                     int backgroundColor, int borderColor) {
            this(textSizeDp, horizontalPaddingDp, cornerRadiusDp, strokeWidthDp, textColor, hintColor,
                    backgroundColor, borderColor, UiThemes.EDITOR.border().focus(), UiThemes.EDITOR.status().errorBorder(),
                    UiThemes.EDITOR.selection().text(), UiMetrics.DISABLED_ALPHA);
        }
    }
}
