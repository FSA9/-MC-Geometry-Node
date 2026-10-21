package com.mine.geometry_node.client.ui.components.base.input;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;

/** Stateful checkbox whose drawn and interactive bounds are exactly the View bounds. */
public final class UiCheckBox extends View {
    public static final Style DEFAULT_STYLE = Style.forTheme(UiThemes.EDITOR);

    private final Paint paint = new Paint();
    private final Style style;
    private boolean checked;
    private boolean indeterminate;
    private OnCheckedChangeListener listener;

    public UiCheckBox(Context context) {
        this(context, DEFAULT_STYLE);
    }

    public static UiCheckBox createThemed(Context context, UiTheme theme) {
        return new UiCheckBox(context, Style.forTheme(theme));
    }

    public UiCheckBox(Context context, Style style) {
        super(context);
        this.style = style != null ? style : DEFAULT_STYLE;
        paint.setAntiAlias(true);
        setClickable(true);
        setFocusable(true);
        setWillNotDraw(false);
    }

    public boolean isChecked() {
        return checked;
    }

    /** Synchronizes state without notifying the user-change listener. */
    public void setChecked(boolean checked) {
        if (this.checked == checked) return;
        this.checked = checked;
        invalidate();
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }

    /** Synchronizes a partial selection without changing checked state or notifying listeners. */
    public void setIndeterminate(boolean indeterminate) {
        if (this.indeterminate == indeterminate) return;
        this.indeterminate = indeterminate;
        invalidate();
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean performClick() {
        if (!isEnabled()) return false;
        super.performClick();
        boolean next = indeterminate || !checked;
        setIndeterminate(false);
        setChecked(next);
        if (listener != null) listener.onCheckedChanged(this, checked);
        return true;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        boolean focused = isEnabled() && isFocused();
        float strokeWidth = UIUtils.dp2px(focused
                ? Math.max(style.strokeWidthDp(), 2.0f) : style.strokeWidthDp());
        float offset = strokeWidth * 0.5f;
        int radius = UIUtils.dp2pxInt(style.cornerRadiusDp());

        paint.setStyle(Paint.Style.FILL);
        boolean selected = checked || indeterminate;
        paint.setColor(stateColor(selected ? style.checkedBackground() : style.uncheckedBackground()));
        canvas.drawRoundRect(offset, offset, width - offset, height - offset, radius, radius, paint);

        if (isEnabled() && (isHovered() || isPressed())) {
            paint.setColor(isPressed() ? style.pressedOverlay() : style.hoverOverlay());
            canvas.drawRoundRect(offset, offset, width - offset, height - offset, radius, radius, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(stateColor(focused ? style.focusColor()
                : selected ? style.checkedBorder() : style.uncheckedBorder()));
        canvas.drawRoundRect(offset, offset, width - offset, height - offset, radius, radius, paint);

        if (!selected) return;
        paint.setColor(stateColor(style.checkColor()));
        paint.setStrokeWidth(UIUtils.dp2px(style.checkStrokeWidthDp()));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        if (indeterminate) {
            canvas.drawLine(width * 0.27f, height * 0.5f, width * 0.73f, height * 0.5f, paint);
            return;
        }
        canvas.drawLine(width * 0.27f, height * 0.52f, width * 0.43f, height * 0.68f, paint);
        canvas.drawLine(width * 0.43f, height * 0.68f, width * 0.74f, height * 0.32f, paint);
    }

    private int stateColor(int color) {
        if (isEnabled()) return color;
        int alpha = Math.round((color >>> 24) * style.disabledAlpha());
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** Visual parameters in density-independent units; layout controls the component bounds. */
    public record Style(
            float cornerRadiusDp,
            float strokeWidthDp,
            float checkStrokeWidthDp,
            int uncheckedBackground,
            int checkedBackground,
            int uncheckedBorder,
            int checkedBorder,
            int checkColor,
            int focusColor,
            int hoverOverlay,
            int pressedOverlay,
            float disabledAlpha
    ) {
        public Style(float cornerRadiusDp, float strokeWidthDp, float checkStrokeWidthDp,
                     int uncheckedBackground, int checkedBackground, int uncheckedBorder,
                     int checkedBorder, int checkColor) {
            this(cornerRadiusDp, strokeWidthDp, checkStrokeWidthDp, uncheckedBackground,
                    checkedBackground, uncheckedBorder, checkedBorder, checkColor,
                    UiThemes.EDITOR.border().focus(), UiThemes.EDITOR.control().hoverOverlay(),
                    UiThemes.EDITOR.control().pressedOverlay(), UiMetrics.DISABLED_ALPHA);
        }

        public static Style forTheme(UiTheme theme) {
            return new Style(UiMetrics.RADIUS_SMALL, UiMetrics.BORDER, 1.6f,
                    theme.surface().panel(), theme.selection().fill(), theme.border().subtle(),
                    theme.selection().outline(), theme.text().strong(), theme.border().focus(),
                    theme.control().hoverOverlay(), theme.control().pressedOverlay(), UiMetrics.DISABLED_ALPHA);
        }
    }

    @FunctionalInterface
    public interface OnCheckedChangeListener {
        void onCheckedChanged(UiCheckBox checkBox, boolean checked);
    }
}
