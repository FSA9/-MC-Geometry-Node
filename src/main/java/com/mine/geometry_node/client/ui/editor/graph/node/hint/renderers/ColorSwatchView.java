package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;

import com.mine.geometry_node.client.ui.common.ColorPickerDialog;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;

import java.util.function.Consumer;
import java.util.function.Supplier;

class ColorSwatchView extends View {
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
    private boolean mHovered;
    private boolean mPressed;

    ColorSwatchView(Context context, Supplier<ColorValue> colorSupplier, Consumer<ColorValue> onColorSelected) {
        super(context);
        mColorSupplier = colorSupplier;
        mOnColorSelected = onColorSelected;

        setWillNotDraw(false);
        setFocusable(true);
        setFocusableInTouchMode(true);

        mPaint.setAntiAlias(true);
        syncFromSupplier();
    }

    void setColor(ColorValue color) {
        mColor = color != null ? color : ColorValue.WHITE;
        invalidate();
    }

    ColorValue getColor() {
        return mColor;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float radius = UIUtils.dp2px(2.0f);
        float stroke = UIUtils.dp2px(1.0f);

        mRect.set(0, 0, w, h);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mPressed ? COLOR_BG_ACTIVE : (mHovered ? COLOR_BG_HOVER : COLOR_BG));
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        drawChecker(canvas, mRect);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mColor.toArgb());
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setColor(mHovered || mPressed ? COLOR_BORDER_HOVER : COLOR_BORDER);
        mRect.set(stroke * 0.5f, stroke * 0.5f, w - stroke * 0.5f, h - stroke * 0.5f);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return onTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            requestFocus();
            syncFromSupplier();
            mPressed = true;
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            boolean openPicker = mPressed;
            mPressed = false;
            invalidate();
            if (openPicker) {
                openColorPicker();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            mPressed = false;
            invalidate();
            return true;
        }
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
            setControlHovered(true);
            return true;
        }
        if (action == MotionEvent.ACTION_HOVER_EXIT) {
            setControlHovered(false);
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private void openColorPicker() {
        ColorValue currentColor = mColor;
        ColorPickerDialog.show(getContext(), this, currentColor.toArgb(), argb -> {
            ColorValue selected = ColorValue.fromArgb(argb);
            ColorValue next = new ColorValue(selected.r(), selected.g(), selected.b(), currentColor.a());
            if (mOnColorSelected != null) {
                mOnColorSelected.accept(next);
            }
            mColor = next;
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

    private void setControlHovered(boolean hovered) {
        if (mHovered == hovered) {
            return;
        }
        mHovered = hovered;
        invalidate();
    }
}
