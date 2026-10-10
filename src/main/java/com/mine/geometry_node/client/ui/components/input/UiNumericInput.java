package com.mine.geometry_node.client.ui.components.input;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.common.UiTextInput;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.text.FontMetricsInt;
import icyllis.modernui.graphics.text.ShapedText;
import icyllis.modernui.text.TextDirectionHeuristics;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.TextShaper;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Numeric gesture/painting mechanics. The binding owns persistence and undo boundaries. */
public class UiNumericInput extends FrameLayout {
    private static final int PRESENTATION_CACHE_LIMIT = 4096;
    private static final Map<Object, Object> DRAGGED_FLOAT_VALUES = presentationCache();
    private static final Map<Object, Boolean> PERSISTED_HOVER = presentationCache();

    private static <V> Map<Object, V> presentationCache() {
        return new LinkedHashMap<>(64, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Object, V> eldest) {
                // Only presentation survives a rebuilt control; bindings remain authoritative.
                return size() > PRESENTATION_CACHE_LIMIT;
            }
        };
    }

    private static final int COLOR_BG = UiTheme.Surface.PANEL;
    private static final int COLOR_BG_HOVER = UiTheme.INLINE.normal();
    private static final int COLOR_BG_ACTIVE = UiTheme.INLINE.hover();
    private static final int COLOR_BORDER = UiTheme.Control.INPUT_BORDER;
    private static final int COLOR_BORDER_HOVER = UiTheme.Control.FOCUS;
    private static final int COLOR_RANGE = ((UiTheme.Control.FOCUS & 0x00FFFFFF) | 0x88000000);
    private static final int COLOR_TEXT = UiTheme.Text.SECONDARY;
    private static final int COLOR_LABEL = UiTheme.Text.MUTED;
    private static final int COLOR_ARROW = UiTheme.Text.HINT;

    private static final float INTEGER_DRAG_STEP_DP = 8.0f;
    private static final float ARROW_HIT_WIDTH_DP = 18.0f;
    private static final float TEXT_PADDING_DP = 8.0f;
    private static final float ARROW_TEXT_GAP_DP = 10.0f;

    private final Binding mBinding;
    private final Spec mSpec;
    private final float textSizeDp;
    private final java.util.function.Consumer<EditText> configureEditor;
    private final Paint mPaint = new Paint();
    private final TextPaint mTextPaint = new TextPaint();
    private final FontMetricsInt mTextMetrics = new FontMetricsInt();
    private final RectF mRect = new RectF();
    private final int mTouchSlop;

    private Object mCommittedValue;
    private Object mStoredValue;
    private Object mDisplayValue;
    private ShapedText mDisplayText;
    private ShapedText mLabelText;
    private String mLabel = "";
    private boolean mHovered;
    private boolean mPressed;
    private boolean mDragging;
    private float mDownX;
    private float mDownY;
    private double mDragStartValue;
    private Object mDragOldValue;
    private EditText mEditor;

    public UiNumericInput(Context context, Binding binding, Spec spec, String inlineLabel,
                          float textSizeDp, java.util.function.Consumer<EditText> configureEditor) {
        super(context);
        this.mBinding = binding;
        this.mSpec = spec;
        this.textSizeDp = textSizeDp;
        this.configureEditor = configureEditor;
        this.mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setWillNotDraw(false);
        setClipChildren(false);
        setFocusable(true);
        setFocusableInTouchMode(true);

        mPaint.setAntiAlias(true);
        mTextPaint.setTextAntiAlias(true);
        mTextPaint.setTextSize(UIUtils.dp2px(textSizeDp));
        mTextPaint.setColor(COLOR_TEXT);

        syncFromBinding();
        setInlineLabelText(inlineLabel);
        mHovered = PERSISTED_HOVER.getOrDefault(controlKey(), false);
    }

    public void refreshFromBinding() {
        if (mEditor == null) {
            syncFromBinding();
        }
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

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mPressed || mDragging ? COLOR_BG_ACTIVE : (mHovered ? COLOR_BG_HOVER : COLOR_BG));
        mRect.set(0, 0, w, h);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        if (mSpec.hasRange()) {
            float progress = clamp01(mSpec.progress(mDisplayValue));
            if (progress > 0.0f) {
                mPaint.setColor(COLOR_RANGE);
                mRect.set(stroke, stroke, stroke + (w - stroke * 2.0f) * progress, h - stroke);
                canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);
            }
        }

        if (mHovered && mSpec.showArrows() && mEditor == null) {
            drawArrow(canvas, true, w, h);
            drawArrow(canvas, false, w, h);
        }

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setColor(isEnabled() && (isFocused() || mHovered || mPressed || mDragging)
                ? COLOR_BORDER_HOVER : COLOR_BORDER);
        mRect.set(stroke * 0.5f, stroke * 0.5f, w - stroke * 0.5f, h - stroke * 0.5f);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        if (mEditor == null) {
            drawValueText(canvas, w, h);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        if (mEditor != null) {
            return super.dispatchTouchEvent(event);
        }
        return onTouchEvent(event);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        setAlpha(isEnabled() ? 1 : UiTheme.Metrics.DISABLED_ALPHA);
        if (mSpec != null && !isEnabled()) {
            mPressed = false;
            mDragging = false;
            setDisplayValue(mCommittedValue, false);
        }
        invalidate();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (mEditor != null) {
            return super.dispatchGenericMotionEvent(event);
        }
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN -> {
                requestFocus();
                closeEditor(false);
                syncFromBinding();
                mPressed = true;
                mDragging = false;
                mDownX = event.getX();
                mDownY = event.getY();
                mDragStartValue = mSpec.valueOrDefault(mDisplayValue, mBinding.defaultValue());
                mDragOldValue = mStoredValue;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!mPressed) {
                    return true;
                }
                float dx = event.getX() - mDownX;
                float dy = event.getY() - mDownY;
                if (!mDragging && Math.hypot(dx, dy) > mTouchSlop) {
                    mDragging = true;
                }
                if (mDragging) {
                    updateDragValue(dx);
                }
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                boolean wasDragging = mDragging;
                boolean wasPressed = mPressed;
                mPressed = false;
                mDragging = false;
                invalidate();
                if (wasDragging) {
                    commitIfChanged(mDragOldValue, mDisplayValue, true);
                } else if (wasPressed) {
                    handleClick(event.getX());
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                mPressed = false;
                mDragging = false;
                setDisplayValue(mCommittedValue, false);
                invalidate();
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private void handleClick(float x) {
        if (mSpec.showArrows() && mHovered) {
            float arrowWidth = UIUtils.dp2px(ARROW_HIT_WIDTH_DP);
            if (x <= arrowWidth) {
                Object newValue = mSpec.stepValue(mSpec.valueOrDefault(mDisplayValue, mBinding.defaultValue()), -1);
                persistHover(true);
                commitIfChanged(mStoredValue, newValue);
                return;
            }
            if (x >= getWidth() - arrowWidth) {
                Object newValue = mSpec.stepValue(mSpec.valueOrDefault(mDisplayValue, mBinding.defaultValue()), 1);
                persistHover(true);
                commitIfChanged(mStoredValue, newValue);
                return;
            }
        }
        openEditor();
    }

    private void updateDragValue(float dx) {
        double pixelsPerStep = mSpec.integerDragStep() ? UIUtils.dp2px(INTEGER_DRAG_STEP_DP) : 1.0d;
        double steps = dx / pixelsPerStep;
        double raw = mDragStartValue + steps * mSpec.step();
        Object next = mSpec.coerceDragged(raw);
        if (!Objects.equals(next, mDisplayValue)) {
            setDisplayValue(next, true);
        }
    }

    private void openEditor() {
        if (mEditor != null) {
            return;
        }

        EditText editor = new UiTextInput(getContext(), new UiTextInput.Style(textSizeDp, TEXT_PADDING_DP,
                UiTheme.Metrics.RADIUS_SMALL, UiTheme.Metrics.BORDER, COLOR_TEXT, UiTheme.Text.HINT,
                COLOR_BG_ACTIVE, COLOR_BORDER_HOVER));
        editor.setText(mSpec.display(mDisplayValue));
        if (configureEditor != null) configureEditor.accept(editor);
        editor.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        editor.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                closeEditor(true);
            }
        });

        mEditor = editor;
        addView(editor, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        editor.post(() -> {
            if (mEditor != editor) return;
            editor.requestFocus();
            editor.selectAll();
        });
    }

    private void closeEditor(boolean commit) {
        if (mEditor == null) {
            return;
        }
        EditText editor = mEditor;
        mEditor = null;
        Object oldValue = mStoredValue;
        clearInteractionState();
        if (commit && isEnabled()) {
            Object parsed = mSpec.parseManual(editor.getText().toString());
            if (parsed != null) {
                commitIfChanged(oldValue, parsed);
            } else {
                setDisplayValue(mCommittedValue, false);
            }
        }
        removeView(editor);
        invalidate();
    }

    private void commitIfChanged(Object oldValue, Object newValue) {
        commitIfChanged(oldValue, newValue, false);
    }

    private void commitIfChanged(Object oldValue, Object newValue, boolean draggedFormat) {
        if (Objects.equals(mCommittedValue, newValue)) {
            rememberDraggedFloatFormat(newValue, draggedFormat);
            setDisplayValue(newValue, draggedFormat);
            return;
        }
        rememberDraggedFloatFormat(newValue, draggedFormat);
        boolean changed = mBinding.commit(oldValue, newValue);
        if (changed) {
            syncFromBinding();
        } else {
            rememberDraggedFloatFormat(newValue, false);
            syncFromBinding();
        }
    }

    private void syncFromBinding() {
        mStoredValue = mBinding.currentCommitValue();
        Object value = mBinding.currentValue();
        mCommittedValue = value;
        setDisplayValue(value, shouldUseDraggedFloatFormat(value));
    }

    private void setDisplayValue(Object value, boolean draggedFormat) {
        mDisplayValue = value;
        String text = draggedFormat ? mSpec.displayDragged(value) : mSpec.display(value);
        mDisplayText = TextShaper.shapeText(text, 0, text.length(), TextDirectionHeuristics.FIRSTSTRONG_LTR, mTextPaint);
        invalidate();
    }

    private void drawValueText(Canvas canvas, float w, float h) {
        if (mDisplayText == null) {
            return;
        }
        mTextPaint.getFontMetricsInt(mTextMetrics);
        float baseline = h * 0.5f - (mTextMetrics.ascent + mTextMetrics.descent) * 0.5f;

        float padding = UIUtils.dp2px(TEXT_PADDING_DP);
        float arrowInset = mHovered && mSpec.showArrows() ? UIUtils.dp2px(ARROW_HIT_WIDTH_DP + ARROW_TEXT_GAP_DP) : padding;
        float leftTextX = Math.max(padding, arrowInset);
        float rightBound = Math.max(leftTextX, w - arrowInset);
        float valueTextWidth = mDisplayText.getAdvance();
        float valueTextX = mHovered
                ? leftTextX + Math.max(0.0f, rightBound - leftTextX - valueTextWidth) * 0.5f
                : rightBound - valueTextWidth;
        valueTextX = Math.max(leftTextX, Math.min(valueTextX, rightBound - valueTextWidth));

        float minGap = UIUtils.dp2px(8.0f);
        if (mLabelText != null) {
            float availableWidth = Math.max(UIUtils.dp2px(1.0f), valueTextX - minGap - leftTextX);
            ShapedText labelText = labelTextForWidth(availableWidth);
            mTextPaint.setColor(COLOR_LABEL);
            canvas.drawShapedText(labelText, leftTextX, baseline, mTextPaint);
        }

        mTextPaint.setColor(COLOR_TEXT);
        canvas.drawShapedText(mDisplayText, valueTextX, baseline, mTextPaint);
    }

    private void setInlineLabelText(String label) {
        if (label == null || label.isBlank()) {
            mLabel = "";
            mLabelText = null;
            return;
        }
        mLabel = label.trim();
        mLabelText = shapeLabel(mLabel);
    }

    private ShapedText labelTextForWidth(float availableWidth) {
        if (mLabelText == null || mLabelText.getAdvance() <= availableWidth) {
            return mLabelText;
        }
        float originalSize = mTextPaint.getTextSize();
        float fittedSize = Math.max(UIUtils.dp2px(1.0f), originalSize * availableWidth / mLabelText.getAdvance());
        mTextPaint.setTextSize(fittedSize);
        ShapedText fitted = shapeLabel(mLabel);
        mTextPaint.setTextSize(originalSize);
        return fitted;
    }

    private ShapedText shapeLabel(String label) {
        return TextShaper.shapeText(label, 0, label.length(), TextDirectionHeuristics.FIRSTSTRONG_LTR, mTextPaint);
    }

    private void drawArrow(Canvas canvas, boolean left, float w, float h) {
        float centerX = left ? UIUtils.dp2px(8.0f) : w - UIUtils.dp2px(8.0f);
        float centerY = h * 0.5f;
        float size = UIUtils.dp2px(3.2f);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(UIUtils.dp2px(1.2f));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setColor(COLOR_ARROW);
        if (left) {
            canvas.drawLine(centerX + size * 0.5f, centerY - size, centerX - size * 0.5f, centerY, mPaint);
            canvas.drawLine(centerX - size * 0.5f, centerY, centerX + size * 0.5f, centerY + size, mPaint);
        } else {
            canvas.drawLine(centerX - size * 0.5f, centerY - size, centerX + size * 0.5f, centerY, mPaint);
            canvas.drawLine(centerX + size * 0.5f, centerY, centerX - size * 0.5f, centerY + size, mPaint);
        }
        mPaint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void setControlHovered(boolean hovered) {
        if (mHovered == hovered) {
            return;
        }
        mHovered = hovered;
        persistHover(hovered);
        invalidate();
    }

    private void clearInteractionState() {
        mHovered = false;
        mPressed = false;
        mDragging = false;
        persistHover(false);
    }

    private boolean shouldUseDraggedFloatFormat(Object value) {
        return mSpec.fractionalDisplay() && Objects.equals(DRAGGED_FLOAT_VALUES.get(controlKey()), value);
    }

    private void rememberDraggedFloatFormat(Object value, boolean draggedFormat) {
        if (!mSpec.fractionalDisplay()) {
            return;
        }
        Object key = controlKey();
        if (draggedFormat) {
            DRAGGED_FLOAT_VALUES.put(key, value);
        } else {
            DRAGGED_FLOAT_VALUES.remove(key);
        }
    }

    private Object controlKey() {
        return mBinding.controlKey();
    }

    private void persistHover(boolean hovered) {
        Object key = controlKey();
        if (hovered) {
            PERSISTED_HOVER.put(key, true);
        } else {
            PERSISTED_HOVER.remove(key);
        }
    }

    private static float clamp01(float value) {
        if (value < 0.0f) {
            return 0.0f;
        }
        if (value > 1.0f) {
            return 1.0f;
        }
        return value;
    }

    public interface Binding {
        Object currentValue();
        Object currentCommitValue();
        Object defaultValue();
        boolean commit(Object oldValue, Object newValue);
        Object controlKey();
    }

    public interface Spec {
        boolean hasRange();
        boolean showArrows();
        boolean integerDragStep();
        boolean fractionalDisplay();
        double step();
        double valueOrDefault(Object value, Object fallback);
        Object parseManual(String text);
        Object coerceDragged(double value);
        Object stepValue(double value, int direction);
        String display(Object value);
        String displayDragged(Object value);
        float progress(Object value);
    }
}
