package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.NumericInputSpec;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintValueBinder;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortType;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.text.FontMetricsInt;
import icyllis.modernui.graphics.text.ShapedText;
import icyllis.modernui.text.TextDirectionHeuristics;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.text.TextShaper;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class NumericInputView extends FrameLayout {
    private static final Map<String, Object> DRAGGED_FLOAT_VALUES = new HashMap<>();
    private static final Map<String, Boolean> PERSISTED_HOVER = new HashMap<>();

    private static final int COLOR_BG = 0xFF252525;
    private static final int COLOR_BG_HOVER = 0xFF30343B;
    private static final int COLOR_BG_ACTIVE = 0xFF343B45;
    private static final int COLOR_BORDER = 0xFF333333;
    private static final int COLOR_BORDER_HOVER = 0xFF566070;
    private static final int COLOR_RANGE = 0x443D6EA8;
    private static final int COLOR_TEXT = UIConstants.CLR_GRAY_LABEL;
    private static final int COLOR_ARROW = 0xFF8C95A4;

    private static final float INTEGER_DRAG_STEP_DP = 8.0f;
    private static final float ARROW_HIT_WIDTH_DP = 18.0f;

    private final NumericValueBinding mBinding;
    private final NumericInputSpec mSpec;
    private final Paint mPaint = new Paint();
    private final TextPaint mTextPaint = new TextPaint();
    private final FontMetricsInt mTextMetrics = new FontMetricsInt();
    private final RectF mRect = new RectF();
    private final int mTouchSlop;

    private Object mCommittedValue;
    private Object mStoredValue;
    private Object mDisplayValue;
    private ShapedText mDisplayText;
    private boolean mHovered;
    private boolean mPressed;
    private boolean mDragging;
    private float mDownX;
    private float mDownY;
    private double mDragStartValue;
    private Object mDragOldValue;
    private EditText mEditor;

    NumericInputView(Context context, NodeData nodeData, PortDef port, NumericInputSpec spec, EditorContext editorContext) {
        this(context, new PortNumericBinding(nodeData, port, editorContext), spec);
    }

    static NumericInputView vectorComponent(Context context, NodeData nodeData, PortDef port, int componentIndex,
                                            NumericInputSpec spec, EditorContext editorContext) {
        return new NumericInputView(context, new VectorComponentBinding(nodeData, port, componentIndex, editorContext), spec);
    }

    private NumericInputView(Context context, NumericValueBinding binding, NumericInputSpec spec) {
        super(context);
        this.mBinding = binding;
        this.mSpec = spec;
        this.mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setWillNotDraw(false);
        setClipChildren(false);
        setFocusable(true);
        setFocusableInTouchMode(true);

        mPaint.setAntiAlias(true);
        mTextPaint.setTextAntiAlias(true);
        mTextPaint.setTextSize(UIUtils.dp2px(UIConstants.Node.TEXT_SIZE_LABEL));
        mTextPaint.setColor(COLOR_TEXT);

        syncFromNode();
        mHovered = PERSISTED_HOVER.getOrDefault(formatKey(), false);
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
        mPaint.setColor(mHovered || mPressed || mDragging ? COLOR_BORDER_HOVER : COLOR_BORDER);
        mRect.set(stroke * 0.5f, stroke * 0.5f, w - stroke * 0.5f, h - stroke * 0.5f);
        canvas.drawRoundRect(mRect, radius, radius, radius, radius, mPaint);

        if (mEditor == null) {
            drawValueText(canvas, w, h);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mEditor != null) {
            return super.dispatchTouchEvent(event);
        }
        return onTouchEvent(event);
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
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN -> {
                requestFocus();
                closeEditor(false);
                syncFromNode();
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
        double pixelsPerStep = mSpec.type() == PortType.INTEGER ? UIUtils.dp2px(INTEGER_DRAG_STEP_DP) : 1.0d;
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

        EditText editor = new EditText(getContext());
        editor.setText(mSpec.display(mDisplayValue));
        UIHintUtils.applyStandardInputStyle(editor, mSpec.type());
        editor.setGravity(Gravity.CENTER);
        editor.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                closeEditor(true);
            }
        });

        ShapeDrawable bg = new ShapeDrawable();
        bg.setColor(COLOR_BG_ACTIVE);
        bg.setCornerRadius(UIUtils.dp2px(2.0f));
        bg.setStroke(UIUtils.dp2pxInt(1.0f), COLOR_BORDER_HOVER);
        editor.setBackground(bg);

        mEditor = editor;
        addView(editor, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        editor.post(() -> {
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
        if (commit) {
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
            mStoredValue = mBinding.currentCommitValue();
            mCommittedValue = newValue;
            setDisplayValue(newValue, draggedFormat);
        } else {
            rememberDraggedFloatFormat(newValue, false);
        }
    }

    private void syncFromNode() {
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
        mTextPaint.setColor(COLOR_TEXT);
        mTextPaint.getFontMetricsInt(mTextMetrics);
        float textX = (w - mDisplayText.getAdvance()) * 0.5f;
        float baseline = h * 0.5f - (mTextMetrics.ascent + mTextMetrics.descent) * 0.5f;
        canvas.drawShapedText(mDisplayText, textX, baseline, mTextPaint);
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
        return mSpec.type() == PortType.FLOAT && Objects.equals(DRAGGED_FLOAT_VALUES.get(formatKey()), value);
    }

    private void rememberDraggedFloatFormat(Object value, boolean draggedFormat) {
        if (mSpec.type() != PortType.FLOAT) {
            return;
        }
        String key = formatKey();
        if (draggedFormat) {
            DRAGGED_FLOAT_VALUES.put(key, value);
        } else {
            DRAGGED_FLOAT_VALUES.remove(key);
        }
    }

    private String formatKey() {
        return mBinding.formatKey();
    }

    private void persistHover(boolean hovered) {
        String key = formatKey();
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

    private interface NumericValueBinding {
        Object currentValue();
        Object currentCommitValue();
        Object defaultValue();
        boolean commit(Object oldValue, Object newValue);
        String formatKey();
    }

    private record PortNumericBinding(NodeData nodeData, PortDef port, EditorContext editorContext) implements NumericValueBinding {
        @Override
        public Object currentValue() {
            if (port == null) {
                return null;
            }
            return hasStoredInput() ? nodeData.inputs.get(port.id()) : port.defaultValue();
        }

        @Override
        public Object currentCommitValue() {
            return hasStoredInput() ? nodeData.inputs.get(port.id()) : null;
        }

        @Override
        public Object defaultValue() {
            return port != null ? port.defaultValue() : null;
        }

        @Override
        public boolean commit(Object oldValue, Object newValue) {
            return port != null && UIHintValueBinder.commit(editorContext, nodeData, port.id(), oldValue, newValue);
        }

        @Override
        public String formatKey() {
            String nodeId = nodeData != null ? nodeData.id : "";
            String portId = port != null ? port.id() : "";
            return nodeId + "#" + portId;
        }

        private boolean hasStoredInput() {
            return nodeData != null && nodeData.inputs != null && port != null && nodeData.inputs.containsKey(port.id());
        }
    }

    private record VectorComponentBinding(NodeData nodeData, PortDef port, int componentIndex,
                                          EditorContext editorContext) implements NumericValueBinding {
        @Override
        public Object currentValue() {
            return UIHintUtils.getSafeVectorComponent(effectiveValue(), componentIndex);
        }

        @Override
        public Object currentCommitValue() {
            return hasStoredInput() ? nodeData.inputs.get(port.id()) : null;
        }

        @Override
        public Object defaultValue() {
            return 0.0f;
        }

        @Override
        public boolean commit(Object oldValue, Object newValue) {
            if (!(newValue instanceof Number number) || port == null) {
                return false;
            }

            Object currentRaw = effectiveValue();
            List<Float> newVector = new ArrayList<>(3);
            for (int i = 0; i < 3; i++) {
                newVector.add(i == componentIndex ? number.floatValue() : UIHintUtils.getSafeVectorComponent(currentRaw, i));
            }
            return UIHintValueBinder.commit(editorContext, nodeData, port.id(), oldValue, newVector);
        }

        @Override
        public String formatKey() {
            String nodeId = nodeData != null ? nodeData.id : "";
            String portId = port != null ? port.id() : "";
            return nodeId + "#" + portId + "[" + componentIndex + "]";
        }

        private Object effectiveValue() {
            Object stored = currentCommitValue();
            return stored != null || hasStoredInput() ? stored : (port != null ? port.defaultValue() : null);
        }

        private boolean hasStoredInput() {
            return nodeData != null && nodeData.inputs != null && port != null && nodeData.inputs.containsKey(port.id());
        }
    }
}
