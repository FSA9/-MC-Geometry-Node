package com.mine.geometry_node.client.ui.components.input;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.text.FontMetricsInt;
import icyllis.modernui.graphics.text.ShapedText;
import icyllis.modernui.text.TextDirectionHeuristics;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.TextShaper;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;


/** Compact select trigger; options and persistence belong to its caller. */
public final class UiSelectButton extends View {
    private static final int COLOR_BUTTON_BG = UiTheme.Surface.PANEL;
    private static final int COLOR_BUTTON_BG_HOVER = UiTheme.INLINE.normal();
    private static final int COLOR_BUTTON_BG_ACTIVE = UiTheme.INLINE.hover();
    private static final int COLOR_BUTTON_BORDER = UiTheme.Control.INPUT_BORDER;
    private static final int COLOR_BUTTON_BORDER_HOVER = UiTheme.Control.FOCUS;


    private final java.util.function.DoubleSupplier cornerRadius;
    private static final float TEXT_PADDING_DP = 8.0f;

    private final Paint mPaint = new Paint();
    private final RectF mRect = new RectF();
    private final TextPaint mTextPaint = new TextPaint();
    private final FontMetricsInt mMetrics = new FontMetricsInt();
    private ShapedText mValueText;
    private ShapedText mArrowText;

    public UiSelectButton(Context context, String value, float textSizeDp,
                          java.util.function.DoubleSupplier cornerRadius) {
        super(context);
        this.cornerRadius = cornerRadius;
        setWillNotDraw(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        mPaint.setAntiAlias(true);
        mTextPaint.setTextAntiAlias(true);
        mTextPaint.setTextSize(UIUtils.dp2px(textSizeDp));
        mArrowText = shape("▼");
        setValue(value);
    }

    public void setValue(String value) {
        String safeValue = value == null ? "" : value;
        mValueText = shape(safeValue);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float radius = UIUtils.dp2px((float) cornerRadius.getAsDouble());
        float stroke = UIUtils.dp2px(1.0f);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(isEnabled() && isPressed() ? COLOR_BUTTON_BG_ACTIVE
                : isEnabled() && isHovered() ? COLOR_BUTTON_BG_HOVER : COLOR_BUTTON_BG);
        mRect.set(0, 0, w, h);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setColor(isEnabled() && (isFocused() || isHovered()) ? COLOR_BUTTON_BORDER_HOVER : COLOR_BUTTON_BORDER);
        mRect.set(stroke * 0.5f, stroke * 0.5f, w - stroke * 0.5f, h - stroke * 0.5f);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        mTextPaint.getFontMetricsInt(mMetrics);
        float baseline = h * 0.5f - (mMetrics.ascent + mMetrics.descent) * 0.5f;
        float padding = UIUtils.dp2px(TEXT_PADDING_DP);
        float arrowX = Math.max(padding, w - padding - mArrowText.getAdvance());

        if (mValueText != null) {
            mTextPaint.setColor(UiTheme.Text.SECONDARY);
            int saved = canvas.save();
            canvas.clipRect(padding, 0, Math.max(padding, arrowX - padding), h);
            canvas.drawShapedText(mValueText, padding, baseline, mTextPaint);
            canvas.restoreToCount(saved);
        }

        mTextPaint.setColor(UiTheme.Text.HINT);
        canvas.drawShapedText(mArrowText, arrowX, baseline, mTextPaint);
    }

    @Override
    public boolean performClick() {
        return isEnabled() && super.performClick();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (!isEnabled() && isPressed()) setPressed(false);
        setAlpha(isEnabled() ? 1 : UiTheme.Metrics.DISABLED_ALPHA);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            requestFocus();
            setPressed(true);
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            boolean wasPressed = isPressed();
            setPressed(false);
            if (wasPressed && containsPointer(event)) {
                performClick();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            setPressed(false);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && !containsPointer(event)) setPressed(false);
        return true;
    }

    private boolean containsPointer(MotionEvent event) {
        return event.getX() >= 0 && event.getX() < getWidth()
                && event.getY() >= 0 && event.getY() < getHeight();
    }

    private ShapedText shape(String text) {
        return TextShaper.shapeText(text, 0, text.length(), TextDirectionHeuristics.FIRSTSTRONG_LTR, mTextPaint);
    }
}
