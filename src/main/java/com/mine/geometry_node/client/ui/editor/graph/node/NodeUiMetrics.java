package com.mine.geometry_node.client.ui.editor.graph.node;

import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;

/** Fixed node geometry and definition-aware layout measurements. */
public final class NodeUiMetrics {
    /** Fixed graph-node design unit, in logical UI pixels (dp). */
    public static final int GRID_UNIT_DP = 15;
    public static final int NODE_WIDTH = 6 * GRID_UNIT_DP;
    public static final int ROW_HEIGHT = GRID_UNIT_DP;
    public static final int HEADER_HEIGHT = GRID_UNIT_DP;
    public static final float STROKE_WIDTH_NORMAL = 1.5f;
    public static final float STROKE_WIDTH_SELECTED = 2.5f;
    public static final int LABEL_MARGIN_PORT = 5;
    public static final int MARGIN_CHECKBOX_GAP = 3;
    public static final int CHECKBOX_DEFAULT_WIDTH = 13;
    public static final int TEXT_SIZE_HEADER = 9;
    public static final int TEXT_SIZE_LABEL = 9;
    public static final float PORT_VISUAL_RADIUS = ROW_HEIGHT / 4.0f;

    private static final int MIN_NODE_WIDTH_DP = 4 * NodeUiMetrics.GRID_UNIT_DP;

    private NodeUiMetrics() {}

    public static int width(NodeDef nodeDef) {
        int width = nodeDef != null
                ? nodeDef.getMetaOrDefault(SchemaKeys.UI_WIDTH, NodeUiMetrics.NODE_WIDTH)
                : NodeUiMetrics.NODE_WIDTH;
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
            return NodeUiMetrics.HEADER_HEIGHT;
        }

        float currentY = NodeUiMetrics.HEADER_HEIGHT;
        NodeLayoutEngine layoutEngine = new NodeLayoutEngine();
        for (PortRow row : nodeDef.rows()) {
            currentY += layoutEngine.calculateRowHeight(nodeData, row);
        }

        boolean isInputDynamic = nodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_INPUT).isPresent();
        boolean isOutputDynamic = nodeDef.getMeta(SchemaKeys.MAX_DYNAMIC_OUTPUT).isPresent();
        boolean isGroupVirtualDynamic = nodeData.isGroupInputNode() || nodeData.isGroupOutputNode();
        if (isInputDynamic || isOutputDynamic || isGroupVirtualDynamic) {
            currentY += NodeUiMetrics.ROW_HEIGHT;
        }
        return Math.max(NodeUiMetrics.HEADER_HEIGHT, (int) currentY);
    }
}
