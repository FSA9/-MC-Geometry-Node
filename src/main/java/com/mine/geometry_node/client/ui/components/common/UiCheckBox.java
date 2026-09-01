package com.mine.geometry_node.client.ui.components.common;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;

/** Stateful checkbox whose drawn and interactive bounds are exactly the View bounds. */
public final class UiCheckBox extends View {
    public static final Style DEFAULT_STYLE = new Style(
            2.0f,
            1.0f,
            1.6f,
            0xFF252525,
            0xFF3D6EA8,
            0xFF3A3A3A,
            0xFF6FA2DD,
            0xFFFFFFFF
    );

    private final Paint paint = new Paint();
    private final Style style;
    private boolean checked;
    private OnCheckedChangeListener listener;

    public UiCheckBox(Context context) {
        this(context, DEFAULT_STYLE);
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

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean performClick() {
        if (!isEnabled()) return false;
        super.performClick();
        setChecked(!checked);
        if (listener != null) listener.onCheckedChanged(this, checked);
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        float strokeWidth = UIUtils.dp2px(style.strokeWidthDp());
        float offset = strokeWidth * 0.5f;
        int radius = UIUtils.dp2pxInt(style.cornerRadiusDp());

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(checked ? style.checkedBackground() : style.uncheckedBackground());
        canvas.drawRoundRect(offset, offset, width - offset, height - offset, radius, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(checked ? style.checkedBorder() : style.uncheckedBorder());
        canvas.drawRoundRect(offset, offset, width - offset, height - offset, radius, radius, paint);

        if (!checked) return;
        paint.setColor(style.checkColor());
        paint.setStrokeWidth(UIUtils.dp2px(style.checkStrokeWidthDp()));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawLine(width * 0.27f, height * 0.52f, width * 0.43f, height * 0.68f, paint);
        canvas.drawLine(width * 0.43f, height * 0.68f, width * 0.74f, height * 0.32f, paint);
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
            int checkColor
    ) {
    }

    @FunctionalInterface
    public interface OnCheckedChangeListener {
        void onCheckedChanged(UiCheckBox checkBox, boolean checked);
    }
}
