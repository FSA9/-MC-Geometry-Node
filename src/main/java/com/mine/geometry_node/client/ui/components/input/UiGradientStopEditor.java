package com.mine.geometry_node.client.ui.components.input;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.value.color.ColorGradientValue;
import com.mine.geometry_node.core.node.value.color.ColorGradientValue.ColorStop;
import com.mine.geometry_node.core.node.value.color.ColorGradientValue.Interpolation;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;

import java.util.List;
import java.util.Objects;

/** Gradient display and stop dragging. Preview, persistence and undo belong to the supplied binding. */
public final class UiGradientStopEditor extends View {
    private static final int COLOR_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_STOP = 0xFF101010;

    private final Paint mPaint = new Paint();
    private boolean mDragging;
    private float mDragWidthPx;
    private Object mDragOldValue;

    /** currentCommitValue must return a detached snapshot or immutable revision token. */
    public interface Binding {
        ColorGradientValue currentValue();
        Object currentCommitValue();
        void preview(ColorGradientValue value);
        boolean commit(Object expectedValue, ColorGradientValue value);
    }

    public record Style(float trackHeightDp, float markerGapDp, float markerDiameterDp, float hitPaddingDp) {
        public static final Style DEFAULT = new Style(20, 2, 7, 1);
        public Style {
            if (!Float.isFinite(trackHeightDp) || trackHeightDp <= 0
                    || !Float.isFinite(markerGapDp) || markerGapDp < 0
                    || !Float.isFinite(markerDiameterDp) || markerDiameterDp <= 0
                    || !Float.isFinite(hitPaddingDp) || hitPaddingDp < 0)
                throw new IllegalArgumentException("Invalid gradient editor dimensions");
        }
        public float heightDp() { return trackHeightDp + markerGapDp + markerDiameterDp; }
    }

    private final Binding binding;
    private final Style style;
    private ColorGradientValue mGradient;

    public UiGradientStopEditor(Context context, Binding binding) {
        this(context, binding, Style.DEFAULT);
    }

    public UiGradientStopEditor(Context context, Binding binding, Style style) {
        super(context);
        this.binding = Objects.requireNonNull(binding);
        this.style = Objects.requireNonNull(style);
        setGradient(binding.currentValue());
        setWillNotDraw(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        mPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float barBottom = gradientTrackBottom(h);
        drawGradientTrack(canvas, w, barBottom);
        drawStopGuideLines(canvas, w, barBottom);

        for (int i = 0; i < mGradient.stops().size(); i++) {
            drawStop(canvas, i, w, barBottom);
        }
    }

    private void drawStopGuideLines(Canvas canvas, float width, float barBottom) {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAntiAlias(false);
        mPaint.setStrokeWidth(UIUtils.dp2px(1.0f));
        for (int i = 0; i < mGradient.stops().size(); i++) {
            float x = mGradient.stops().get(i).position() * width;
            mPaint.setColor(i == mGradient.selectedIndex() ? COLOR_SELECTED : COLOR_STOP);
            canvas.drawLine(x, 0, x, barBottom, mPaint);
        }
    }

    private void drawStop(Canvas canvas, int index, float width, float barBottom) {
        ColorStop stop = mGradient.stops().get(index);
        float x = stop.position() * width;
        float y = markerCenterY(barBottom);
        float radius = UIUtils.dp2px(style.markerDiameterDp() * 0.5f);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(true);
        mPaint.setColor(stop.color().toArgb());
        canvas.drawCircle(x, y, radius, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(UIUtils.dp2px(1.0f));
        mPaint.setColor(index == mGradient.selectedIndex() ? COLOR_SELECTED : COLOR_STOP);
        canvas.drawCircle(x, y, radius - UIUtils.dp2px(0.5f), mPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            requestFocus();
            refreshFromBinding();
            mDragWidthPx = Math.max(1.0f, getWidth());
            float barBottom = gradientTrackBottom(getHeight());
            int hitIndex = hitStop(event.getX(), event.getY(), mDragWidthPx, barBottom);
            if (hitIndex < 0) {
                mDragging = false;
                return true;
            }
            requestDisallowParentIntercept(true);
            mDragging = true;
            mDragOldValue = binding.currentCommitValue();
            selectStop(hitIndex);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (mDragging) {
                float position = clamp01(event.getX() / mDragWidthPx);
                previewDrag(mGradient.withSelectedPosition(position));
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean wasDragging = mDragging;
            mDragging = false;
            requestDisallowParentIntercept(false);
            if (wasDragging) {
                if (action == MotionEvent.ACTION_CANCEL) {
                    refreshFromBinding();
                } else {
                    binding.commit(mDragOldValue, mGradient);
                    refreshFromBinding();
                }
            }
            mDragOldValue = null;
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void drawGradientTrack(Canvas canvas, float width, float barBottom) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(false);
        if (mGradient.interpolation() == Interpolation.CONSTANT) {
            drawConstantGradient(canvas, width, barBottom);
            return;
        }

        int columns = Math.max(1, (int) Math.ceil(width));
        float overlap = 1.0f;
        for (int x = 0; x < columns; x++) {
            float position = columns <= 1 ? 0.0f : x / (float) (columns - 1);
            mPaint.setColor(mGradient.sample(position).toArgb());
            canvas.drawRect(x, 0, Math.min(width, x + 1.0f + overlap), barBottom, mPaint);
        }
    }

    private void drawConstantGradient(Canvas canvas, float width, float barBottom) {
        List<ColorStop> stops = mGradient.stops();
        float overlap = 1.0f;
        for (int i = 0; i < stops.size(); i++) {
            ColorStop stop = stops.get(i);
            float left = i == 0 ? 0.0f : stop.position() * width;
            float right = i + 1 < stops.size() ? stops.get(i + 1).position() * width : width;
            mPaint.setColor(stop.color().toArgb());
            canvas.drawRect(left, 0, Math.min(width, right + overlap), barBottom, mPaint);
        }
    }

    private int hitStop(float x, float y, float width, float barBottom) {
        for (int i = mGradient.stops().size() - 1; i >= 0; i--) {
            float markerX = mGradient.stops().get(i).position() * width;
            if (isInsideMarker(x, y, markerX, markerCenterY(barBottom))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isInsideMarker(float x, float y, float markerX, float markerY) {
        float padding = UIUtils.dp2px(style.hitPaddingDp());
        float radius = UIUtils.dp2px(style.markerDiameterDp() * 0.5f) + padding;
        float dx = x - markerX;
        float dy = y - markerY;
        return dx * dx + dy * dy <= radius * radius;
    }

    private float gradientTrackBottom(float height) {
        return Math.min(height, UIUtils.dp2px(style.trackHeightDp()));
    }

    private float markerCenterY(float barBottom) {
        return barBottom + UIUtils.dp2px(style.markerGapDp() + style.markerDiameterDp() * 0.5f);
    }

    private void selectStop(int index) {
        mGradient = mGradient.withSelectedIndex(index);
        previewDrag(mGradient);
    }

    private void requestDisallowParentIntercept(boolean disallow) {
        icyllis.modernui.view.ViewParent parent = getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }

    /** Synchronizes the presentation only; never commits or notifies the binding. */
    public void setGradient(ColorGradientValue value) {
        mGradient = value != null ? value : ColorGradientValue.DEFAULT;
        invalidate();
    }

    public ColorGradientValue getGradient() { return mGradient; }

    public void refreshFromBinding() {
        setGradient(binding.currentValue());
        binding.preview(mGradient);
    }

    private void previewDrag(ColorGradientValue value) {
        setGradient(value);
        binding.preview(mGradient);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled) cancelDrag();
        super.setEnabled(enabled);
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelDrag();
        super.onDetachedFromWindow();
    }

    private void cancelDrag() {
        if (!mDragging) return;
        mDragging = false;
        mDragOldValue = null;
        requestDisallowParentIntercept(false);
        refreshFromBinding();
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
