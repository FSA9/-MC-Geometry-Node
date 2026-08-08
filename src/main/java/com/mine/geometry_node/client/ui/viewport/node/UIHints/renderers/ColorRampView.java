package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.interaction.InteractionContext;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.NumericInputSpec;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintValueBinder;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.nodes.functions.color.ColorRamp;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.value.color.ColorGradientValue;
import com.mine.geometry_node.core.node.value.color.ColorGradientValue.ColorMode;
import com.mine.geometry_node.core.node.value.color.ColorGradientValue.ColorStop;
import com.mine.geometry_node.core.node.value.color.ColorGradientValue.Interpolation;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class ColorRampView extends LinearLayout {
    private static final float GAP_DP = 3.0f;
    private static final float CONTROL_HEIGHT_DP = UIHintUtils.getStandardInputHeight();
    private static final float GRADIENT_MARKER_GAP_DP = 2.0f;
    private static final float GRADIENT_MARKER_DIAMETER_DP = 7.0f;
    private static final float GRADIENT_ROW_HEIGHT_DP = CONTROL_HEIGHT_DP + GRADIENT_MARKER_GAP_DP + GRADIENT_MARKER_DIAMETER_DP;
    static final float HEIGHT_DP = 5.0f * (CONTROL_HEIGHT_DP + GAP_DP) + (GRADIENT_ROW_HEIGHT_DP + GAP_DP);

    private static final float TOOL_BUTTON_WIDTH_DP = 20.0f;
    private static final float STOP_FIELD_WIDTH_DP = 35.0f;
    private static final int COLOR_FIELD = 0xFF252525;
    private static final int COLOR_FIELD_BORDER = 0xFF333333;
    private static final int COLOR_TEXT = UIConstants.CLR_GRAY_LABEL;

    private final NodeData mNodeData;
    private final EditorContext mEditorContext;

    private ColorGradientValue mGradient;
    private GradientBarView mGradientBar;
    private SelectHintRenderer.SelectButtonView mModeSelect;
    private SelectHintRenderer.SelectButtonView mInterpolationSelect;
    private NumericInputView mIndexInput;
    private NumericInputView mPositionInput;
    private ColorSwatchView mColorSwatch;

    ColorRampView(Context context, NodeData nodeData, EditorContext editorContext) {
        super(context);
        mNodeData = nodeData;
        mEditorContext = editorContext;
        mGradient = readGradient();

        setOrientation(VERTICAL);
        setWillNotDraw(true);
        setClipChildren(false);

        buildUi(context);
        refreshUi(false);
    }

    private void buildUi(Context context) {
        LinearLayout toolbar = row(context);
        toolbar.addView(button(context, "+", v -> commit(mGradient.addStopAt(0.5f))), fixed(TOOL_BUTTON_WIDTH_DP));
        toolbar.addView(button(context, "-", v -> commit(mGradient.removeSelectedStop())), fixed(TOOL_BUTTON_WIDTH_DP));
        toolbar.addView(button(context, "▼", v -> showActionMenu(v)), fixed(TOOL_BUTTON_WIDTH_DP));
        addView(toolbar, fullRow());

        mModeSelect = new SelectHintRenderer.SelectButtonView(context, mGradient.mode().id);
        mModeSelect.setOnClickListener(v -> showMenu(mModeSelect, "颜色模式", List.of("RGB", "HSV", "HSL"), value -> {
            ColorMode mode = ColorMode.from(value);
            ColorGradientValue next = mGradient.withMode(mode);
            commit(next.withInterpolation(defaultInterpolation(mode)));
        }));
        addView(mModeSelect, fullRow());

        mInterpolationSelect = new SelectHintRenderer.SelectButtonView(context, interpolationLabel(mGradient.interpolation()));
        mInterpolationSelect.setOnClickListener(v -> showMenu(mInterpolationSelect, "插值",
                interpolationOptions(mGradient.mode()), value -> commit(mGradient.withInterpolation(interpolationFromLabel(value)))));
        addView(mInterpolationSelect, fullRow());

        mGradientBar = new GradientBarView(context);
        addView(mGradientBar, rowLp(GRADIENT_ROW_HEIGHT_DP));

        LinearLayout fields = row(context);
        mIndexInput = new NumericInputView(context, new StopIndexBinding(), new NumericInputSpec(PortType.INTEGER, 0.0d, null, 1.0d, 0, true), null);
        mPositionInput = new NumericInputView(context, new StopPositionBinding(), new NumericInputSpec(PortType.FLOAT, 0.0d, 1.0d, 0.001d, 3, true), null);
        fields.addView(mIndexInput, fixed(STOP_FIELD_WIDTH_DP));
        fields.addView(mPositionInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
        addView(fields, fullRow());

        mColorSwatch = new ColorSwatchView(context, this::selectedColor, color -> commit(mGradient.withSelectedColor(color)));
        addView(mColorSwatch, rowLp(CONTROL_HEIGHT_DP));
    }

    private TextView button(Context context, String text, View.OnClickListener listener) {
        TextView button = new TextView(context);
        button.setText(text);
        UIUtils.setLockedTextSize(button, UIConstants.Node.TEXT_SIZE_LABEL);
        button.setTextColor(COLOR_TEXT);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rect(COLOR_FIELD, 2.0f, 1, COLOR_FIELD_BORDER));
        button.setOnClickListener(listener);
        return button;
    }

    private void showActionMenu(View anchor) {
        List<String> actions = List.of("重置", "反转", "均匀分布");
        showMenu(anchor, "颜色渐变", actions, action -> {
            if ("重置".equals(action)) {
                commit(ColorGradientValue.DEFAULT);
            } else if ("反转".equals(action)) {
                commit(mGradient.reversed());
            } else if ("均匀分布".equals(action)) {
                commit(mGradient.evenlyDistributed());
            }
        });
    }

    private void showMenu(View anchor, String title, List<String> options, Consumer<String> onSelect) {
        icyllis.modernui.view.ViewParent parent = anchor.getParent();
        while (parent != null && !(parent instanceof InteractionContext)) {
            parent = parent.getParent();
        }
        if (parent instanceof InteractionContext interactionContext) {
            SelectHintRenderer.DropdownSearchMenu menu = new SelectHintRenderer.DropdownSearchMenu(
                    getContext(), title, options, Map.of(), onSelect);
            menu.showAt(anchor, interactionContext);
        }
    }

    private ColorGradientValue readGradient() {
        Object raw = mNodeData != null ? mNodeData.inputs.get(ColorRamp.GRADIENT_INPUT) : null;
        return ColorGradientValue.from(raw);
    }

    private void commit(ColorGradientValue next) {
        mGradient = next != null ? next : ColorGradientValue.DEFAULT;
        Object oldValue = mNodeData != null ? mNodeData.inputs.get(ColorRamp.GRADIENT_INPUT) : null;
        UIHintValueBinder.commit(mEditorContext, mNodeData, ColorRamp.GRADIENT_INPUT, oldValue, mGradient.toMap());
        refreshUi(true);
    }

    private void previewDrag(ColorGradientValue next) {
        mGradient = next != null ? next : ColorGradientValue.DEFAULT;
        if (mNodeData != null) {
            mNodeData.inputs.put(ColorRamp.GRADIENT_INPUT, mGradient.toMap());
        }
        refreshUiFromCurrentGradient();
    }

    private void refreshUi(boolean invalidateOnly) {
        mGradient = readGradient();
        refreshUiFromCurrentGradient();
        if (invalidateOnly) {
            invalidate();
        }
    }

    private void refreshUiFromCurrentGradient() {
        if (mModeSelect != null) {
            mModeSelect.setValue(mGradient.mode().id);
        }
        if (mInterpolationSelect != null) {
            mInterpolationSelect.setValue(interpolationLabel(mGradient.interpolation()));
        }
        if (mIndexInput != null) {
            mIndexInput.refreshFromBinding();
        }
        if (mPositionInput != null) {
            mPositionInput.refreshFromBinding();
        }
        if (mColorSwatch != null) {
            mColorSwatch.setColor(selectedColor());
        }
        if (mGradientBar != null) {
            mGradientBar.invalidate();
        }
    }

    private ColorStop selectedStop() {
        int index = Math.max(0, Math.min(mGradient.selectedIndex(), mGradient.stops().size() - 1));
        return mGradient.stops().get(index);
    }

    private ColorValue selectedColor() {
        return selectedStop().color();
    }

    private static Interpolation defaultInterpolation(ColorMode mode) {
        return mode == ColorMode.RGB ? Interpolation.LINEAR : Interpolation.HUE_NEAR;
    }

    private static List<String> interpolationOptions(ColorMode mode) {
        if (mode == ColorMode.RGB) {
            return List.of("缓动", "原始", "线性", "B样条", "常值");
        }
        return List.of("近端", "远端", "顺时针", "逆时针");
    }

    private static String interpolationLabel(Interpolation interpolation) {
        return interpolation != null ? interpolation.displayName : "线性";
    }

    private static Interpolation interpolationFromLabel(String label) {
        return Interpolation.from(label);
    }

    private LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        return row;
    }

    private LinearLayout.LayoutParams fullRow() {
        return rowLp(CONTROL_HEIGHT_DP);
    }

    private LinearLayout.LayoutParams rowLp(float heightDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(heightDp));
        lp.setMargins(0, 0, 0, UIUtils.dp2pxInt(GAP_DP));
        return lp;
    }

    private LinearLayout.LayoutParams fixed(float widthDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(widthDp), ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(0, 0, UIUtils.dp2pxInt(GAP_DP), 0);
        return lp;
    }

    private static ShapeDrawable rect(int color, float radiusDp, int strokeDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(UIUtils.dp2pxInt(strokeDp), strokeColor);
        }
        return drawable;
    }

    private boolean commitGradient(Object oldValue, ColorGradientValue next) {
        mGradient = next != null ? next : ColorGradientValue.DEFAULT;
        boolean changed = UIHintValueBinder.commit(mEditorContext, mNodeData, ColorRamp.GRADIENT_INPUT, oldValue, mGradient.toMap());
        refreshUi(true);
        return changed;
    }

    private Object currentStoredGradientValue() {
        return mNodeData != null ? mNodeData.inputs.get(ColorRamp.GRADIENT_INPUT) : null;
    }

    private String numericFormatKey(String suffix) {
        String nodeId = mNodeData != null ? mNodeData.id : "";
        return nodeId + "#" + ColorRamp.GRADIENT_INPUT + suffix;
    }

    private final class StopIndexBinding implements NumericInputView.NumericValueBinding {
        @Override
        public Object currentValue() {
            return mGradient.selectedIndex();
        }

        @Override
        public Object currentCommitValue() {
            return currentStoredGradientValue();
        }

        @Override
        public Object defaultValue() {
            return 0;
        }

        @Override
        public boolean commit(Object oldValue, Object newValue) {
            if (!(newValue instanceof Number number)) {
                return false;
            }
            return commitGradient(oldValue, mGradient.withSelectedIndex(number.intValue()));
        }

        @Override
        public String formatKey() {
            return numericFormatKey("[stop_index]");
        }
    }

    private final class StopPositionBinding implements NumericInputView.NumericValueBinding {
        @Override
        public Object currentValue() {
            return selectedStop().position();
        }

        @Override
        public Object currentCommitValue() {
            return currentStoredGradientValue();
        }

        @Override
        public Object defaultValue() {
            return 0.0f;
        }

        @Override
        public boolean commit(Object oldValue, Object newValue) {
            if (!(newValue instanceof Number number)) {
                return false;
            }
            return commitGradient(oldValue, mGradient.withSelectedPosition(number.floatValue()));
        }

        @Override
        public String formatKey() {
            return numericFormatKey("[stop_position]");
        }
    }

    private final class GradientBarView extends View {
        private static final int COLOR_SELECTED = 0xFFFFFFFF;
        private static final int COLOR_STOP = 0xFF101010;
        private static final float MARKER_RADIUS_DP = GRADIENT_MARKER_DIAMETER_DP * 0.5f;
        private static final float MARKER_HIT_PADDING_DP = 1.0f;

        private final Paint mPaint = new Paint();
        private boolean mDragging;
        private float mDragWidthPx;
        private Object mDragOldValue;

        GradientBarView(Context context) {
            super(context);
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
            float radius = UIUtils.dp2px(MARKER_RADIUS_DP);

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
        public boolean dispatchTouchEvent(MotionEvent event) {
            return onTouchEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                requestFocus();
                mDragWidthPx = Math.max(1.0f, getWidth());
                float barBottom = gradientTrackBottom(getHeight());
                int hitIndex = hitStop(event.getX(), event.getY(), mDragWidthPx, barBottom);
                if (hitIndex < 0) {
                    mDragging = false;
                    return true;
                }
                requestDisallowParentIntercept(true);
                mDragging = true;
                mDragOldValue = mNodeData != null ? mNodeData.inputs.get(ColorRamp.GRADIENT_INPUT) : null;
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
                        restoreDragOldValue();
                    } else {
                        commitDragValue();
                    }
                }
                return true;
            }
            return true;
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
            float padding = UIUtils.dp2px(MARKER_HIT_PADDING_DP);
            float radius = UIUtils.dp2px(MARKER_RADIUS_DP) + padding;
            float dx = x - markerX;
            float dy = y - markerY;
            return dx * dx + dy * dy <= radius * radius;
        }

        private float gradientTrackBottom(float height) {
            return Math.min(height, UIUtils.dp2px(CONTROL_HEIGHT_DP));
        }

        private float markerCenterY(float barBottom) {
            return barBottom + UIUtils.dp2px(GRADIENT_MARKER_GAP_DP + MARKER_RADIUS_DP);
        }

        private void selectStop(int index) {
            mGradient = mGradient.withSelectedIndex(index);
            previewDrag(mGradient);
        }

        private void commitDragValue() {
            if (mNodeData == null) {
                return;
            }
            ColorGradientValue finalGradient = mGradient;
            if (mDragOldValue == null) {
                mNodeData.inputs.remove(ColorRamp.GRADIENT_INPUT);
            } else {
                mNodeData.inputs.put(ColorRamp.GRADIENT_INPUT, mDragOldValue);
            }
            UIHintValueBinder.commit(mEditorContext, mNodeData, ColorRamp.GRADIENT_INPUT, mDragOldValue, finalGradient.toMap());
            mGradient = finalGradient;
            refreshUi(false);
        }

        private void restoreDragOldValue() {
            if (mNodeData != null) {
                if (mDragOldValue == null) {
                    mNodeData.inputs.remove(ColorRamp.GRADIENT_INPUT);
                } else {
                    mNodeData.inputs.put(ColorRamp.GRADIENT_INPUT, mDragOldValue);
                }
            }
            refreshUi(false);
        }

        private void requestDisallowParentIntercept(boolean disallow) {
            icyllis.modernui.view.ViewParent parent = getParent();
            while (parent != null) {
                parent.requestDisallowInterceptTouchEvent(disallow);
                parent = parent.getParent();
            }
        }

    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
