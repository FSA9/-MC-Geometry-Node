package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;

import com.mine.geometry_node.client.ui.editor.graph.node.NodeUiMetrics;

import com.mine.geometry_node.client.feature.graph.edit.EditorContext;
import com.mine.geometry_node.client.ui.components.base.input.UiNumericInput;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.NumericInputSpec;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintUtils;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintValueBinder;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.port.PortDef;

import icyllis.modernui.core.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Node-specific values and command bindings for the reusable numeric control. */
final class NumericInputView extends UiNumericInput {
    NumericInputView(Context context, NodeData nodeData, PortDef port, NumericInputSpec spec, EditorContext editorContext) {
        this(context, new PortNumericBinding(nodeData, port, editorContext), spec, inlineLabel(nodeData, port));
    }

    static NumericInputView vectorComponent(Context context, NodeData nodeData, PortDef port, int componentIndex,
                                            NumericInputSpec spec, EditorContext editorContext) {
        return new NumericInputView(context, new VectorComponentBinding(nodeData, port, componentIndex, editorContext), spec, null);
    }

    NumericInputView(Context context, NumericValueBinding binding, NumericInputSpec spec, String inlineLabel) {
        super(context, binding, spec, inlineLabel, NodeUiMetrics.TEXT_SIZE_LABEL,
                editor -> UIHintUtils.applyStandardInputStyle(editor, spec.type()));
    }

    private static String inlineLabel(NodeData nodeData, PortDef port) {
        if (port == null || port.displayName() == null) {
            return "";
        }
        String defaultName = port.displayName().getString();
        return nodeData != null ? nodeData.getEffectivePortName("inputs", port.id(), defaultName) : defaultName;
    }

    interface NumericValueBinding extends UiNumericInput.Binding {}

    record NumericControlKey(String nodeId, String portId, String controlId) {
        NumericControlKey {
            nodeId = Objects.requireNonNullElse(nodeId, "");
            portId = Objects.requireNonNullElse(portId, "");
            controlId = Objects.requireNonNullElse(controlId, "");
        }
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
        public NumericControlKey controlKey() {
            String nodeId = nodeData != null ? nodeData.id : "";
            String portId = port != null ? port.id() : "";
            return new NumericControlKey(nodeId, portId, "value");
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
        public NumericControlKey controlKey() {
            String nodeId = nodeData != null ? nodeData.id : "";
            String portId = port != null ? port.id() : "";
            return new NumericControlKey(nodeId, portId, "vector_component_" + componentIndex);
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
