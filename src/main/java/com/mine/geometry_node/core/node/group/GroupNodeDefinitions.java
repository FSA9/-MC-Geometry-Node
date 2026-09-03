package com.mine.geometry_node.core.node.group;

import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.MetaKey;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.UIHint;
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
        appendGroupOutputs(builder, config.execOutputs);
        appendGroupOutputs(builder, config.outputs);
        appendGroupInputs(builder, config.execInputs, false);
        appendGroupInputs(builder, config.inputs, true);
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
        addPorts(rows, config.execInputs, false);
        addPorts(rows, config.inputs, false);
        appendRows(builder, rows, true);
        return builder.build();
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
        addPorts(rows, config.execOutputs, true);
        addPorts(rows, config.outputs, true);
        appendRows(builder, rows, true);
        return builder.build();
    }

    private static void addPorts(TreeMap<Integer, RowPorts> rows, Map<String, NodeData.PortConfig> ports, boolean inputSide) {
        if (ports == null) return;
        int fallbackOrder = nextFallbackOrder(rows, inputSide);
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

    private static int nextFallbackOrder(TreeMap<Integer, RowPorts> rows, boolean inputSide) {
        int order = 0;
        for (Map.Entry<Integer, RowPorts> entry : rows.entrySet()) {
            RowPorts row = entry.getValue();
            boolean occupied = inputSide ? row.left != null : row.right != null;
            if (occupied) order = Math.max(order, entry.getKey() + 1);
        }
        return order;
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

    private static void appendGroupOutputs(NodeDef.Builder builder,
                                           Map<String, NodeData.PortConfig> ports) {
        orderedPorts(ports).forEach(entry -> builder.addRow(new PortRow(
                null, toPortDef(entry.getKey(), entry.getValue()),
                UIHint.DEFAULT, null, null)));
    }

    private static void appendGroupInputs(NodeDef.Builder builder,
                                          Map<String, NodeData.PortConfig> ports,
                                          boolean passthrough) {
        orderedPorts(ports).forEach(entry -> {
            PortDef input = toPortDef(entry.getKey(), entry.getValue());
            if (passthrough) {
                builder.addPassthroughInput(input, UIHint.DEFAULT);
            } else {
                builder.addRow(new PortRow(input, null, UIHint.DEFAULT, null, null));
            }
        });
    }

    private static java.util.List<Map.Entry<String, NodeData.PortConfig>> orderedPorts(
            Map<String, NodeData.PortConfig> ports) {
        if (ports == null || ports.isEmpty()) return java.util.List.of();
        return ports.entrySet().stream()
                .sorted(java.util.Comparator
                        .comparingInt((Map.Entry<String, NodeData.PortConfig> entry) ->
                                entry.getValue() != null && entry.getValue().order != null
                                        ? entry.getValue().order : Integer.MAX_VALUE)
                        .thenComparing(Map.Entry::getKey))
                .toList();
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
