package com.mine.geometry_node.client.ui.editor.graph.node.hint;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortType;

import java.util.Objects;

public final class UIHintValueBinder {
    private UIHintValueBinder() {
    }

    public static Object getValue(NodeData nodeData, PortDef port) {
        if (nodeData == null || port == null) return null;
        String portId = port.id();
        return nodeData.inputs.containsKey(portId) ? nodeData.inputs.get(portId) : port.defaultValue();
    }

    public static boolean commit(EditorContext editorContext, NodeData nodeData, String portId, Object newValue) {
        if (nodeData == null || portId == null) return false;
        Object oldValue = nodeData.inputs.get(portId);
        return commit(editorContext, nodeData, portId, oldValue, newValue);
    }

    public static boolean commit(EditorContext editorContext, NodeData nodeData, String portId, Object oldValue, Object newValue) {
        if (nodeData == null || portId == null) return false;
        if (Objects.equals(newValue, oldValue)) {
            return false;
        }
        if (editorContext != null) {
            CmdChangeInputValue cmd = new CmdChangeInputValue(editorContext.getGraphController(), nodeData.id, portId, oldValue, newValue);
            editorContext.getCommandManager().execute(cmd);
        } else if (newValue == null) {
            nodeData.inputs.remove(portId);
        } else {
            nodeData.inputs.put(portId, newValue);
        }
        return true;
    }

    public static Object parseText(String text, PortType expectedType) {
        String safeText = text != null ? text : "";
        try {
            if (expectedType == PortType.INTEGER) {
                return Integer.parseInt(safeText);
            }
            if (expectedType == PortType.LONG) {
                return Long.parseLong(safeText);
            }
            if (expectedType == PortType.FLOAT) {
                return Float.parseFloat(safeText);
            }
            return safeText;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean requiresNumericValue(PortType expectedType) {
        return expectedType == PortType.INTEGER || expectedType == PortType.LONG || expectedType == PortType.FLOAT;
    }
}
