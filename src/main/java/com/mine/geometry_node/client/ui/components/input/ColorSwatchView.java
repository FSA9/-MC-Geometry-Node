package com.mine.geometry_node.client.ui.components.input;

import com.mine.geometry_node.client.ui.components.overlay.ColorPickerDialog;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.View;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Color preview with transparency checker and picker; the callback owns persistence and history. */
public final class ColorSwatchView extends View {
    private static final int COLOR_BG = 0xFF252525;
    private static final int COLOR_BG_HOVER = 0xFF30343B;
    private static final int COLOR_BG_ACTIVE = 0xFF343B45;
    private static final int COLOR_BORDER = 0xFF333333;
    private static final int COLOR_BORDER_HOVER = 0xFF566070;
    private static final int COLOR_CHECKER_LIGHT = 0xFFB7BDC7;
    private static final int COLOR_CHECKER_DARK = 0xFF6B7280;

    private static final float CHECKER_SIZE_DP = 4.0f;

    private final Supplier<ColorValue> mColorSupplier;
    private final Consumer<ColorValue> mOnColorSelected;
    private final Paint mPaint = new Paint();
    private final RectF mRect = new RectF();

    private ColorValue mColor = ColorValue.WHITE;

    public ColorSwatchView(Context context, Supplier<ColorValue> colorSupplier, Consumer<ColorValue> onColorSelected) {
        super(context);
        mColorSupplier = colorSupplier;
        mOnColorSelected = onColorSelected;

        setWillNotDraw(false);
        setOnClickListener(view -> openColorPicker());
        setClickable(onColorSelected != null);
        setFocusable(true);
        setFocusableInTouchMode(true);

        mPaint.setAntiAlias(true);
        syncFromSupplier();
    }

    /** Sets a presentation value; when a supplier is provided it remains authoritative. */
    public void setColor(ColorValue color) {
        mColor = color != null ? color : ColorValue.WHITE;
        invalidate();
    }

    public ColorValue getColor() {
        if (mColorSupplier != null) syncFromSupplier();
        return mColor;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mColorSupplier != null) syncFromSupplier();
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float radius = UIUtils.dp2px(2.0f);
        float stroke = UIUtils.dp2px(1.0f);

        mRect.set(0, 0, w, h);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(isPressed() ? COLOR_BG_ACTIVE : (isHovered() ? COLOR_BG_HOVER : COLOR_BG));
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        drawChecker(canvas, mRect);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mColor.toArgb());
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setColor(isEnabled() && (isHovered() || isPressed() || isFocused()) ? COLOR_BORDER_HOVER : COLOR_BORDER);
        mRect.set(stroke * 0.5f, stroke * 0.5f, w - stroke * 0.5f, h - stroke * 0.5f);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);
    }

    @Override
    public boolean performClick() {
        return isEnabled() && mOnColorSelected != null && super.performClick();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    public void openColorPicker() {
        if (!isEnabled() || mOnColorSelected == null) return;
        if (mColorSupplier != null) syncFromSupplier();
        ColorValue currentColor = mColor;
        ColorPickerDialog.show(getContext(), this, currentColor.toArgb(), argb -> {
            if (!isEnabled()) return;
            ColorValue selected = ColorValue.fromArgb(argb);
            ColorValue next = new ColorValue(selected.r(), selected.g(), selected.b(), currentColor.a());
            mOnColorSelected.accept(next);
            mColor = next;
            if (mColorSupplier != null) syncFromSupplier();
            invalidate();
            requestFocus();
        });
    }

    private void syncFromSupplier() {
        ColorValue supplied = mColorSupplier != null ? mColorSupplier.get() : null;
        mColor = supplied != null ? supplied : ColorValue.WHITE;
    }

    private void drawChecker(Canvas canvas, RectF rect) {
        float size = UIUtils.dp2px(CHECKER_SIZE_DP);
        mPaint.setStyle(Paint.Style.FILL);
        int yIndex = 0;
        for (float y = rect.top; y < rect.bottom; y += size, yIndex++) {
            int xIndex = 0;
            for (float x = rect.left; x < rect.right; x += size, xIndex++) {
                mPaint.setColor(((xIndex + yIndex) & 1) == 0 ? COLOR_CHECKER_LIGHT : COLOR_CHECKER_DARK);
                canvas.drawRect(x, y, Math.min(x + size, rect.right), Math.min(y + size, rect.bottom), mPaint);
            }
        }
    }

}
