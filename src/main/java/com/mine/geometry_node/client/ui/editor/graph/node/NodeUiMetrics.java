package com.mine.geometry_node.client.ui.editor.graph.node;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortRow;

public final class NodeUiMetrics {
    private static final int MIN_NODE_WIDTH_DP = 4 * UIConstants.GRID_SIZE;

    private NodeUiMetrics() {}

    public static int width(NodeDef nodeDef) {
        int width = nodeDef != null
                ? nodeDef.getMetaOrDefault(SchemaKeys.UI_WIDTH, UIConstants.Node.NODE_WIDTH)
                : UIConstants.Node.NODE_WIDTH;
        return Math.max(MIN_NODE_WIDTH_DP, width);
    }

    public static int width(NodeData nodeData) {
        NodeDef nodeDef = NodeRegistry.INSTANCE.resolveDefinition(nodeData);
        return width(nodeDef);
    }

    public static int height(NodeData nodeData) {
        NodeDef nodeDef = NodeRegistry.INSTANCE.resolveDefinition(nodeData);
        return height(nodeData, nodeDef);
    }

    public static int height(NodeData nodeData, NodeDef nodeDef) {
        if (nodeData == null || nodeDef == null) {
            return UIConstants.Node.HEADER_HEIGHT;
        }

        float currentY = UIConstants.Node.HEADER_HEIGHT;
        NodeLayoutEngine layoutEngine = new NodeLayoutEngine();
        for (PortRow row : nodeDef.rows()) {
            currentY += layoutEngine.calculateRowHeight(nodeData, row);
        }

        boolean isInputDynamic = nodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_INPUT).isPresent();
        boolean isOutputDynamic = nodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_OUTPUT).isPresent();
        boolean isGroupVirtualDynamic = nodeData.isGroupInputNode() || nodeData.isGroupOutputNode();
        if (isInputDynamic || isOutputDynamic || isGroupVirtualDynamic) {
            currentY += UIConstants.Node.ROW_HEIGHT;
        }
        return Math.max(UIConstants.Node.HEADER_HEIGHT, (int) currentY);
    }
}
