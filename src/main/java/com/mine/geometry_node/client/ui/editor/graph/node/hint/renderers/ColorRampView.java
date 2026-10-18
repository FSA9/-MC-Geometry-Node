package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;

import com.mine.geometry_node.client.ui.components.input.ColorSwatchView;
import com.mine.geometry_node.client.ui.components.input.UiGradientStopEditor;
import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.components.menu.UiSearchableMenu;
import com.mine.geometry_node.client.ui.components.input.UiSelectButton;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.interaction.InteractionContext;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.NumericInputSpec;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintUtils;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.InlineActionButton;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintValueBinder;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.functions.color.ColorRamp;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.value.color.ColorGradientValue;
import com.mine.geometry_node.core.node.value.color.ColorGradientValue.ColorMode;
import com.mine.geometry_node.core.node.value.color.ColorGradientValue.ColorStop;
import com.mine.geometry_node.core.node.value.color.ColorGradientValue.Interpolation;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final NodeData mNodeData;
    private final EditorContext mEditorContext;

    private ColorGradientValue mGradient;
    private UiGradientStopEditor mGradientBar;
    private UiSelectButton mModeSelect;
    private UiSelectButton mInterpolationSelect;
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

        mModeSelect = new UiSelectButton(context, mGradient.mode().id, UIConstants.Node.TEXT_SIZE_LABEL,
                () -> ConfigManager.INSTANCE.getConfig().node.cornerRadius);
        mModeSelect.setOnClickListener(v -> showMenu(mModeSelect, "颜色模式", List.of("RGB", "HSV", "HSL"), value -> {
            ColorMode mode = ColorMode.from(value);
            ColorGradientValue next = mGradient.withMode(mode);
            commit(next.withInterpolation(defaultInterpolation(mode)));
        }));
        addView(mModeSelect, fullRow());

        mInterpolationSelect = new UiSelectButton(context, interpolationLabel(mGradient.interpolation()),
                UIConstants.Node.TEXT_SIZE_LABEL, () -> ConfigManager.INSTANCE.getConfig().node.cornerRadius);
        mInterpolationSelect.setOnClickListener(v -> showMenu(mInterpolationSelect, "插值",
                interpolationOptions(mGradient.mode()), value -> commit(mGradient.withInterpolation(interpolationFromLabel(value)))));
        addView(mInterpolationSelect, fullRow());

        mGradientBar = new UiGradientStopEditor(context, new UiGradientStopEditor.Binding() {
            @Override public ColorGradientValue currentValue() { return readGradient(); }
            @Override public Object currentCommitValue() { return currentStoredGradientValue(); }
            @Override public void preview(ColorGradientValue value) { previewDrag(value); }
            @Override public boolean commit(Object expected, ColorGradientValue value) {
                return commitGradient(expected, value);
            }
        }, new UiGradientStopEditor.Style(CONTROL_HEIGHT_DP, GRADIENT_MARKER_GAP_DP,
                GRADIENT_MARKER_DIAMETER_DP, 1));
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

    private InlineActionButton button(Context context, String text, View.OnClickListener listener) {
        InlineActionButton button = new InlineActionButton(context, text);
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
            UiSearchableMenu menu = new UiSearchableMenu(
                    getContext(), title, options, Map.of(), onSelect);
            if (interactionContext instanceof ViewGroup host) {
                menu.showAt(anchor, host);
            }
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
        // Keep previews detached: saving during a drag must only serialize committed inputs.
        mGradient = next != null ? next : ColorGradientValue.DEFAULT;
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
            mGradientBar.setGradient(mGradient);
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

    private boolean commitGradient(Object oldValue, ColorGradientValue next) {
        // A command/MCP edit may have replaced the input while this control was dragging.
        if (!Objects.equals(oldValue, currentStoredGradientValue())) {
            refreshUi(true);
            return false;
        }
        mGradient = next != null ? next : ColorGradientValue.DEFAULT;
        boolean changed = UIHintValueBinder.commit(mEditorContext, mNodeData, ColorRamp.GRADIENT_INPUT, oldValue, mGradient.toMap());
        refreshUi(true);
        return changed;
    }

    private Object currentStoredGradientValue() {
        Object raw = mNodeData != null ? mNodeData.inputs.get(ColorRamp.GRADIENT_INPUT) : null;
        // Detach the comparison snapshot from the model's potentially mutable map/list values.
        return raw != null ? ColorGradientValue.from(raw).toMap() : null;
    }

    private NumericInputView.NumericControlKey numericControlKey(String controlId) {
        String nodeId = mNodeData != null ? mNodeData.id : "";
        return new NumericInputView.NumericControlKey(nodeId, ColorRamp.GRADIENT_INPUT, controlId);
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
        public NumericInputView.NumericControlKey controlKey() {
            return numericControlKey("stop_index");
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
        public NumericInputView.NumericControlKey controlKey() {
            return numericControlKey("stop_position");
        }
    }

}
