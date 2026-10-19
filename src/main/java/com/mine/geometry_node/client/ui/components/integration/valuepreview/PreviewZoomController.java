package com.mine.geometry_node.client.ui.components.integration.valuepreview;

import icyllis.modernui.view.MotionEvent;

import java.util.Objects;

/**
 * Bounded content zoom, independent of viewport transforms and editing permissions.
 * Configure on the UI thread; the render thread may read {@link #getFactor()}.
 */
public final class PreviewZoomController {
    public enum WheelMode { DISABLED, CTRL_WHEEL, WHEEL }

    private static final double FACTOR_PER_NOTCH = 1.1;
    private final Runnable changed;
    private volatile float factor = 1.0f;
    private float minimum = 0.5f;
    private float maximum = 2.0f;
    private WheelMode wheelMode = WheelMode.DISABLED;

    public PreviewZoomController(Runnable changed) {
        this.changed = Objects.requireNonNull(changed);
    }

    public float getFactor() { return factor; }
    public float getMinimum() { return minimum; }
    public float getMaximum() { return maximum; }
    public WheelMode getWheelMode() { return wheelMode; }

    /** Does not enable wheel input; suitable for a slider or a fixed presentation scale. */
    public void setFactor(float value) {
        requirePositiveFinite(value);
        update(value);
    }

    /** Updates both limits atomically after validation, then clamps the current factor. */
    public void setRange(float minimum, float maximum) {
        requirePositiveFinite(minimum);
        requirePositiveFinite(maximum);
        if (minimum > maximum) throw new IllegalArgumentException("Minimum zoom exceeds maximum zoom");
        this.minimum = minimum;
        this.maximum = maximum;
        update(factor);
    }

    public void setWheelMode(WheelMode mode) {
        wheelMode = Objects.requireNonNull(mode);
    }

    /** Returns to the original appearance, clamped to the configured range. */
    public void reset() { update(1.0f); }

    /** The host checks its enabled state before offering events. Disabled zoom never consumes input. */
    boolean handleScroll(MotionEvent event) {
        if (wheelMode == WheelMode.DISABLED || event.getActionMasked() != MotionEvent.ACTION_SCROLL
                || (wheelMode == WheelMode.CTRL_WHEEL && !event.isCtrlPressed())) return false;
        float delta = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (!Float.isFinite(delta) || delta == 0.0f) return false;
        update(factor * Math.pow(FACTOR_PER_NOTCH, delta));
        // Keep the gesture owned by the preview even at its limits; do not suddenly scroll the list.
        return true;
    }

    private void update(double value) {
        float next = (float) Math.max(minimum, Math.min(maximum, value));
        if (next == factor) return;
        factor = next;
        changed.run();
    }

    private static void requirePositiveFinite(float value) {
        if (!Float.isFinite(value) || value <= 0.0f)
            throw new IllegalArgumentException("Zoom factors must be positive and finite");
    }
}
