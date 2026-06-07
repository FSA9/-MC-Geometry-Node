package com.mine.geometry_node.core.node.group;

import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.MetaKey;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;
import java.util.LinkedHashMap;

public final class GroupNodeDefinitions {
    private GroupNodeDefinitions() {}

    @Nullable
    public static NodeDef resolve(NodeData node) {
        if (node == null || node.type == null) return null;
        return switch (node.type) {
            case GroupNodeTypes.NODE_GROUP -> buildGroupNodeDefinition(node);
            case GroupNodeTypes.GROUP_IN -> buildGroupInputDefinition(node.parentGroupNode);
            case GroupNodeTypes.GROUP_OUT -> buildGroupOutputDefinition(node.parentGroupNode);
            default -> null;
        };
    }

    public static NodeDef buildGroupNodeDefinition(NodeData groupNode) {
        NodeDef.Builder builder = NodeDef.builder(
                GroupNodeTypes.NODE_GROUP,
                NodeType.CUSTOM,
                getGroupDisplayName(groupNode)
        );
        if (groupNode.comment != null && !groupNode.comment.trim().isEmpty()) {
            builder.comment(groupNode.comment.trim());
        }

        NodeData.PortsConfig config = groupNode.ensurePortConfig();
        appendSideRows(builder, config.outputs, config.execOutputs, false);
        appendSideRows(builder, config.inputs, config.execInputs, true);
        return builder.build();
    }

    private static Component getGroupDisplayName(NodeData groupNode) {
        if (groupNode != null && groupNode.customName != null && !groupNode.customName.trim().isEmpty()) {
            return Component.literal(groupNode.customName.trim());
        }
        return Component.translatable("geometry_node.node.node_group");
    }

    private static NodeDef buildGroupInputDefinition(@Nullable NodeData groupNode) {
        NodeDef.Builder builder = NodeDef.builder(
                GroupNodeTypes.GROUP_IN,
                NodeType.CUSTOM,
                Component.translatable("geometry_node.node.group_in")
        );
        if (groupNode == null) return builder.build();

        NodeData.PortsConfig config = groupNode.ensurePortConfig();
        TreeMap<Integer, RowPorts> rows = new TreeMap<>();
        addPorts(rows, config.inputs, false);
        addPorts(rows, config.execInputs, false);
        appendRows(builder, rows, true);
        return builder.build();
    }

    private static void appendSideRows(
            NodeDef.Builder builder,
            Map<String, NodeData.PortConfig> dataPorts,
            Map<String, NodeData.PortConfig> execPorts,
            boolean inputSide
    ) {
        TreeMap<Integer, RowPorts> rows = new TreeMap<>();
        addPorts(rows, dataPorts, inputSide);
        addPorts(rows, execPorts, inputSide);
        appendRows(builder, rows, false);
    }

    private static NodeDef buildGroupOutputDefinition(@Nullable NodeData groupNode) {
        NodeDef.Builder builder = NodeDef.builder(
                GroupNodeTypes.GROUP_OUT,
                NodeType.CUSTOM,
                Component.translatable("geometry_node.node.group_out")
        );
        if (groupNode == null) return builder.build();

        NodeData.PortsConfig config = groupNode.ensurePortConfig();
        TreeMap<Integer, RowPorts> rows = new TreeMap<>();
        addPorts(rows, config.outputs, true);
        addPorts(rows, config.execOutputs, true);
        appendRows(builder, rows, true);
        return builder.build();
    }

    private static void addPorts(TreeMap<Integer, RowPorts> rows, Map<String, NodeData.PortConfig> ports, boolean inputSide) {
        if (ports == null) return;
        int fallbackOrder = rows.isEmpty() ? 0 : rows.lastKey() + 1;
        for (Map.Entry<String, NodeData.PortConfig> entry : ports.entrySet()) {
            NodeData.PortConfig config = entry.getValue();
            int order = config != null && config.order != null ? config.order : fallbackOrder++;
            order = nextAvailableRenderOrder(rows, order, inputSide);
            RowPorts row = rows.computeIfAbsent(order, ignored -> new RowPorts());
            PortDef port = toPortDef(entry.getKey(), config);
            if (inputSide) {
                row.left = port;
            } else {
                row.right = port;
            }
        }
    }

    private static int nextAvailableRenderOrder(TreeMap<Integer, RowPorts> rows, int preferredOrder, boolean inputSide) {
        int order = Math.max(0, preferredOrder);
        while (true) {
            RowPorts row = rows.get(order);
            if (row == null) return order;
            if (inputSide && row.left == null) return order;
            if (!inputSide && row.right == null) return order;
            order++;
        }
    }

    private static PortDef toPortDef(String portId, @Nullable NodeData.PortConfig config) {
        PortType type = config != null && config.type != null ? config.type : PortType.ANY;
        String name = config != null
                ? (config.customName != null ? config.customName : "")
                : portId;
        boolean hidden = config != null && Boolean.TRUE.equals(config.hidden);
        return new PortDef(portId, Component.literal(name), type, type.getDefaultValue(), hidden);
    }

    private static void appendRows(NodeDef.Builder builder, TreeMap<Integer, RowPorts> rows, boolean virtualDynamic) {
        for (Map.Entry<Integer, RowPorts> entry : rows.entrySet()) {
            RowPorts row = entry.getValue();
            builder.addRow(new PortRow(row.left, row.right, UIHint.DEFAULT, null, virtualDynamicParams(virtualDynamic, entry.getKey())));
        }
    }

    private static Map<MetaKey<?>, Object> virtualDynamicParams(boolean virtualDynamic, int order) {
        if (!virtualDynamic) return null;
        Map<MetaKey<?>, Object> params = new LinkedHashMap<>();
        params.put(PortMetaKeys.IS_DYNAMIC, true);
        params.put(PortMetaKeys.DYNAMIC_INDEX, order + 1);
        params.put(PortMetaKeys.IS_GROUP_VIRTUAL_DYNAMIC, true);
        return params;
    }

    private static final class RowPorts {
        private PortDef left;
        private PortDef right;
    }
}
