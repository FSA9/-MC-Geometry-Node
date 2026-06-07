package com.mine.geometry_node.core.node.group;

import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class GroupNodeFactory {
    private GroupNodeFactory() {}

    public static NodeData createGroupNode(String id, float x, float y) {
        NodeData group = new NodeData(id, GroupNodeTypes.NODE_GROUP, x, y);
        group.ensurePortConfig();
        ensureBoundaryNodes(group);
        return group;
    }

    public static void ensureBoundaryNodes(NodeData groupNode) {
        validateGroupNode(groupNode);
        if (!groupNode.ensureSubNodes().containsKey(GroupNodeTypes.GROUP_IN_ID)) {
            groupNode.attachSubNode(GroupNodeTypes.GROUP_IN_ID, new NodeData(GroupNodeTypes.GROUP_IN_ID, GroupNodeTypes.GROUP_IN, 80.0f, 120.0f));
        } else {
            groupNode.attachSubNode(GroupNodeTypes.GROUP_IN_ID, groupNode.subNodes.get(GroupNodeTypes.GROUP_IN_ID));
        }
        if (!groupNode.ensureSubNodes().containsKey(GroupNodeTypes.GROUP_OUT_ID)) {
            groupNode.attachSubNode(GroupNodeTypes.GROUP_OUT_ID, new NodeData(GroupNodeTypes.GROUP_OUT_ID, GroupNodeTypes.GROUP_OUT, 520.0f, 120.0f));
        } else {
            groupNode.attachSubNode(GroupNodeTypes.GROUP_OUT_ID, groupNode.subNodes.get(GroupNodeTypes.GROUP_OUT_ID));
        }
    }

    public static String addPort(NodeData groupNode, String category, String preferredPrefix, PortType type, String customName) {
        validateGroupNode(groupNode);
        validateCategory(category);
        return addPort(groupNode, category, preferredPrefix, type, customName, nextFreeOrder(groupNode, isInputSide(category)));
    }

    public static String addPort(NodeData groupNode, String category, String preferredPrefix, PortType type, String customName, int order) {
        validateGroupNode(groupNode);
        if (type == null) {
            throw new IllegalArgumentException("Group port type cannot be null");
        }

        Map<String, NodeData.PortConfig> ports = groupNode.getPortConfigMap(category);
        if (ports == null) {
            throw new IllegalArgumentException("Unknown group port category: " + category);
        }
        validateCategoryType(category, type);
        if (order < 0) {
            throw new IllegalArgumentException("Group port order cannot be negative");
        }
        if (isOrderUsed(groupNode, isInputSide(category), order)) {
            throw new IllegalStateException("Group port order already used on this side: " + order);
        }

        String portId = nextUniquePortId(groupNode, preferredPrefix);
        NodeData.PortConfig config = new NodeData.PortConfig();
        config.type = type;
        config.order = order;
        config.customName = customName;
        ports.put(portId, config);

        if (GroupNodeTypes.CATEGORY_INPUTS.equals(category)) {
            if (groupNode.inputs == null) {
                groupNode.inputs = new java.util.HashMap<>();
            }
            groupNode.inputs.put(portId, type.getDefaultValue());
        }
        return portId;
    }

    public static String addVirtualPort(NodeData boundaryNode) {
        NodeData groupNode = getBoundaryOwner(boundaryNode);
        String category = getDefaultBoundaryAddCategory(boundaryNode);
        if (groupNode == null || category == null) {
            throw new IllegalArgumentException("Virtual group ports can only be added on group_in/group_out");
        }

        int order = nextFreeOrder(groupNode, isInputSide(category));
        return addPort(groupNode, category, "port", PortType.ANY, "", order);
    }

    public static void restorePort(NodeData groupNode, String category, String portId, NodeData.PortConfig backupConfig) {
        validateGroupNode(groupNode);
        validateCategory(category);
        if (portId == null || backupConfig == null || backupConfig.type == null) return;

        validateCategoryType(category, backupConfig.type);
        String existingCategory = findPortCategory(groupNode, portId);
        if (existingCategory != null) {
            removePort(groupNode, existingCategory, portId);
        }

        NodeData.PortConfig config = copyPortConfig(backupConfig);
        if (config.order != null) {
            makeOrderAvailable(groupNode, isInputSide(category), config.order, portId);
        }
        groupNode.getPortConfigMap(category).put(portId, config);
        if (GroupNodeTypes.CATEGORY_INPUTS.equals(category)) {
            if (groupNode.inputs == null) {
                groupNode.inputs = new java.util.HashMap<>();
            }
            groupNode.inputs.putIfAbsent(portId, config.type.getDefaultValue());
        } else if (GroupNodeTypes.CATEGORY_EXEC_INPUTS.equals(category) && groupNode.inputs != null) {
            groupNode.inputs.remove(portId);
        }
    }

    public static void removePort(NodeData groupNode, String category, String portId) {
        validateGroupNode(groupNode);
        validateCategory(category);
        Map<String, NodeData.PortConfig> ports = groupNode.getPortConfigMap(category);
        if (ports != null) {
            ports.remove(portId);
        }
        if (GroupNodeTypes.CATEGORY_INPUTS.equals(category) && groupNode.inputs != null) {
            groupNode.inputs.remove(portId);
        }
    }

    public static void setPortType(NodeData groupNode, String preferredCategory, String portId, PortType type) {
        setPortTypeAndName(groupNode, preferredCategory, portId, type, null, false);
    }

    public static void setPortBinding(NodeData groupNode, String preferredCategory, String portId, PortType type, @Nullable String customName) {
        setPortTypeAndName(groupNode, preferredCategory, portId, type, customName, true);
    }

    private static void setPortTypeAndName(NodeData groupNode, String preferredCategory, String portId, PortType type, @Nullable String customName, boolean updateName) {
        validateGroupNode(groupNode);
        validateCategory(preferredCategory);
        if (portId == null || type == null) return;

        String sourceCategory = findPortCategory(groupNode, portId);
        String source = sourceCategory != null ? sourceCategory : preferredCategory;
        NodeData.PortConfig config = getPortConfig(groupNode, source, portId);
        if (config == null) {
            config = new NodeData.PortConfig();
            config.order = nextFreeOrder(groupNode, isInputSide(preferredCategory));
            config.customName = portId;
        }

        boolean inputSide = isInputSide(source);
        String target = categoryFor(inputSide, type);
        if (!source.equals(target)) {
            removePort(groupNode, source, portId);
            groupNode.getPortConfigMap(target).put(portId, config);
        }

        config.type = type;
        if (updateName) {
            config.customName = customName;
        }
        if (GroupNodeTypes.CATEGORY_INPUTS.equals(target)) {
            if (groupNode.inputs == null) {
                groupNode.inputs = new java.util.HashMap<>();
            }
            groupNode.inputs.putIfAbsent(portId, type.getDefaultValue());
        } else if (GroupNodeTypes.CATEGORY_EXEC_INPUTS.equals(target) && groupNode.inputs != null) {
            groupNode.inputs.remove(portId);
        }
    }

    @Nullable
    public static String findPortCategory(NodeData groupNode, String portId) {
        if (groupNode == null || portId == null) return null;
        NodeData.PortsConfig config = groupNode.ensurePortConfig();
        if (containsKey(config.inputs, portId)) return GroupNodeTypes.CATEGORY_INPUTS;
        if (containsKey(config.execInputs, portId)) return GroupNodeTypes.CATEGORY_EXEC_INPUTS;
        if (containsKey(config.outputs, portId)) return GroupNodeTypes.CATEGORY_OUTPUTS;
        if (containsKey(config.execOutputs, portId)) return GroupNodeTypes.CATEGORY_EXEC_OUTPUTS;
        return null;
    }

    @Nullable
    public static NodeData.PortConfig getPortConfig(NodeData groupNode, String category, String portId) {
        if (groupNode == null || category == null || portId == null) return null;
        Map<String, NodeData.PortConfig> ports = groupNode.getPortConfigMap(category);
        return ports != null ? ports.get(portId) : null;
    }

    @Nullable
    public static String findBoundaryPortCategory(NodeData boundaryNode, String portId) {
        NodeData groupNode = getBoundaryOwner(boundaryNode);
        if (groupNode == null || portId == null) return null;

        NodeData.PortsConfig config = groupNode.ensurePortConfig();
        if (boundaryNode.isGroupInputNode()) {
            if (containsKey(config.inputs, portId)) return GroupNodeTypes.CATEGORY_INPUTS;
            if (containsKey(config.execInputs, portId)) return GroupNodeTypes.CATEGORY_EXEC_INPUTS;
        } else if (boundaryNode.isGroupOutputNode()) {
            if (containsKey(config.outputs, portId)) return GroupNodeTypes.CATEGORY_OUTPUTS;
            if (containsKey(config.execOutputs, portId)) return GroupNodeTypes.CATEGORY_EXEC_OUTPUTS;
        }
        return null;
    }

    public static boolean isBoundaryNode(NodeData node) {
        return node != null && (node.isGroupInputNode() || node.isGroupOutputNode());
    }

    @Nullable
    public static NodeData getBoundaryOwner(NodeData node) {
        return isBoundaryNode(node) ? node.parentGroupNode : null;
    }

    @Nullable
    public static String mapBoundaryCategory(NodeData boundaryNode, String visualCategory) {
        if (boundaryNode == null || visualCategory == null) return null;
        if (boundaryNode.isGroupInputNode()) {
            return switch (visualCategory) {
                case GroupNodeTypes.CATEGORY_OUTPUTS -> GroupNodeTypes.CATEGORY_INPUTS;
                case GroupNodeTypes.CATEGORY_EXEC_OUTPUTS -> GroupNodeTypes.CATEGORY_EXEC_INPUTS;
                default -> null;
            };
        }
        if (boundaryNode.isGroupOutputNode()) {
            return switch (visualCategory) {
                case GroupNodeTypes.CATEGORY_INPUTS -> GroupNodeTypes.CATEGORY_OUTPUTS;
                case GroupNodeTypes.CATEGORY_EXEC_INPUTS -> GroupNodeTypes.CATEGORY_EXEC_OUTPUTS;
                default -> null;
            };
        }
        return null;
    }

    @Nullable
    public static String getDefaultBoundaryAddCategory(NodeData boundaryNode) {
        if (boundaryNode == null) return null;
        if (boundaryNode.isGroupInputNode()) return GroupNodeTypes.CATEGORY_INPUTS;
        if (boundaryNode.isGroupOutputNode()) return GroupNodeTypes.CATEGORY_OUTPUTS;
        return null;
    }

    public static String nextUniquePortId(NodeData groupNode, String preferredPrefix) {
        String base = sanitizePortId(preferredPrefix);
        if (base.isBlank()) {
            base = "port";
        }

        String candidate = base;
        int index = 1;
        while (hasPortId(groupNode, candidate)) {
            candidate = base + "_" + index++;
        }
        return candidate;
    }

    public static int nextFreeOrder(NodeData groupNode, boolean inputSide) {
        int order = 0;
        while (isOrderUsed(groupNode, inputSide, order)) {
            order++;
        }
        return order;
    }

    public static boolean hasPortId(NodeData groupNode, String portId) {
        if (groupNode == null || portId == null) return false;
        NodeData.PortsConfig config = groupNode.ensurePortConfig();
        return containsKey(config.inputs, portId)
                || containsKey(config.execInputs, portId)
                || containsKey(config.outputs, portId)
                || containsKey(config.execOutputs, portId);
    }

    public static boolean isOrderUsed(NodeData groupNode, boolean inputSide, int order) {
        if (groupNode == null) return false;
        NodeData.PortsConfig config = groupNode.ensurePortConfig();
        return inputSide
                ? hasOrder(config.inputs, order) || hasOrder(config.execInputs, order)
                : hasOrder(config.outputs, order) || hasOrder(config.execOutputs, order);
    }

    public static boolean isInputSide(String category) {
        return GroupNodeTypes.CATEGORY_INPUTS.equals(category)
                || GroupNodeTypes.CATEGORY_EXEC_INPUTS.equals(category);
    }

    public static void compactOrdersAfterRemoval(NodeData groupNode, boolean inputSide, int removedOrder) {
        validateGroupNode(groupNode);
        compactOrdersAfterRemoval(inputSide ? groupNode.ensurePortConfig().inputs : groupNode.ensurePortConfig().outputs, removedOrder);
        compactOrdersAfterRemoval(inputSide ? groupNode.ensurePortConfig().execInputs : groupNode.ensurePortConfig().execOutputs, removedOrder);
    }

    public static String categoryFor(boolean inputSide, PortType type) {
        boolean exec = type == PortType.EXECUTION;
        if (inputSide) {
            return exec ? GroupNodeTypes.CATEGORY_EXEC_INPUTS : GroupNodeTypes.CATEGORY_INPUTS;
        }
        return exec ? GroupNodeTypes.CATEGORY_EXEC_OUTPUTS : GroupNodeTypes.CATEGORY_OUTPUTS;
    }

    private static void validateGroupNode(NodeData groupNode) {
        if (groupNode == null || !groupNode.isGroupNode()) {
            throw new IllegalArgumentException("Group port can only be added to node_group");
        }
    }

    private static void validateCategory(String category) {
        if (!GroupNodeTypes.CATEGORY_INPUTS.equals(category)
                && !GroupNodeTypes.CATEGORY_EXEC_INPUTS.equals(category)
                && !GroupNodeTypes.CATEGORY_OUTPUTS.equals(category)
                && !GroupNodeTypes.CATEGORY_EXEC_OUTPUTS.equals(category)) {
            throw new IllegalArgumentException("Unknown group port category: " + category);
        }
    }

    private static void validateCategoryType(String category, PortType type) {
        boolean execCategory = GroupNodeTypes.CATEGORY_EXEC_INPUTS.equals(category)
                || GroupNodeTypes.CATEGORY_EXEC_OUTPUTS.equals(category);
        if (execCategory && type != PortType.EXECUTION) {
            throw new IllegalArgumentException("Execution group ports must use EXECUTION type");
        }
        if (!execCategory && type == PortType.EXECUTION) {
            throw new IllegalArgumentException("Data group ports cannot use EXECUTION type");
        }
    }

    private static boolean containsKey(Map<String, NodeData.PortConfig> map, String key) {
        return map != null && map.containsKey(key);
    }

    private static boolean hasOrder(Map<String, NodeData.PortConfig> ports, int order) {
        if (ports == null) return false;
        for (NodeData.PortConfig config : ports.values()) {
            if (config != null && config.order != null && config.order == order) {
                return true;
            }
        }
        return false;
    }

    private static void makeOrderAvailable(NodeData groupNode, boolean inputSide, int order, String ignoredPortId) {
        NodeData.PortsConfig config = groupNode.ensurePortConfig();
        makeOrderAvailable(inputSide ? config.inputs : config.outputs, order, ignoredPortId);
        makeOrderAvailable(inputSide ? config.execInputs : config.execOutputs, order, ignoredPortId);
    }

    private static void makeOrderAvailable(Map<String, NodeData.PortConfig> ports, int order, String ignoredPortId) {
        if (ports == null) return;
        for (Map.Entry<String, NodeData.PortConfig> entry : ports.entrySet()) {
            NodeData.PortConfig config = entry.getValue();
            if (config == null || config.order == null) continue;
            if (entry.getKey().equals(ignoredPortId)) continue;
            if (config.order >= order) {
                config.order = config.order + 1;
            }
        }
    }

    private static void compactOrdersAfterRemoval(Map<String, NodeData.PortConfig> ports, int removedOrder) {
        if (ports == null) return;
        for (NodeData.PortConfig config : ports.values()) {
            if (config != null && config.order != null && config.order > removedOrder) {
                config.order = config.order - 1;
            }
        }
    }

    private static NodeData.PortConfig copyPortConfig(NodeData.PortConfig source) {
        NodeData.PortConfig copy = new NodeData.PortConfig();
        copy.customName = source.customName;
        copy.hidden = source.hidden;
        copy.type = source.type;
        copy.order = source.order;
        return copy;
    }

    private static String sanitizePortId(String preferredPrefix) {
        if (preferredPrefix == null) return "";
        String clean = preferredPrefix.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (!clean.isEmpty() && Character.isDigit(clean.charAt(0))) {
            clean = "port_" + clean;
        }
        return clean;
    }
}
