package com.mine.geometry_node.core.engine.behavior.plan;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledDataIndex;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.node.NodeCapabilities;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/** Immutable, compact behavior-tree artifact shared by all future runtime instances. */
public final class BehaviorTreePlan implements CompiledGraph, CompiledDataIndex {
    private final String assetId;
    private final String[] nodeIds;
    private final String[] nodeTypes;
    private final NodeCapabilities[] capabilities;
    private final int rootNode;
    private final int[] parents;
    private final int[][] children;
    private final Map<String, Object>[] staticInputs;
    private final Set<String>[] copiedStaticInputs;
    private final Map<String, DataConnectionSource>[] dataInputs;
    private final Set<String>[] ports;
    private final Map<String, Integer> portKeys;
    private final RootSchedule rootSchedule;

    private BehaviorTreePlan(String assetId, String[] nodeIds,
                             String[] nodeTypes, NodeCapabilities[] capabilities, int rootNode,
                             int[] parents, int[][] children, Map<String, Object>[] staticInputs,
                             Map<String, DataConnectionSource>[] dataInputs, Set<String>[] ports,
                             Map<String, Integer> portKeys,
                             RootSchedule rootSchedule) {
        this.assetId = assetId != null ? assetId : "";
        this.nodeIds = nodeIds.clone();
        this.nodeTypes = nodeTypes.clone();
        this.capabilities = capabilities.clone();
        this.rootNode = rootNode;
        this.parents = parents.clone();
        this.children = copyChildren(children);
        this.staticInputs = copyMapArray(staticInputs);
        this.copiedStaticInputs = mutableInputKeys(this.staticInputs);
        this.dataInputs = copyMapArray(dataInputs);
        this.ports = copySetArray(ports);
        this.portKeys = Map.copyOf(portKeys);
        this.rootSchedule = rootSchedule != null ? rootSchedule : RootSchedule.DEFAULT;
    }

    public static BehaviorTreePlan createCompiled(
            String assetId, String[] nodeIds,
            String[] nodeTypes, NodeCapabilities[] capabilities, int rootNode,
            int[] parents, int[][] children, Map<String, Object>[] staticInputs,
            Map<String, DataConnectionSource>[] dataInputs, Set<String>[] ports,
            Map<String, Integer> portKeys,
            RootSchedule rootSchedule) {
        return new BehaviorTreePlan(assetId, nodeIds, nodeTypes, capabilities,
                rootNode, parents, children, staticInputs, dataInputs, ports,
                portKeys, rootSchedule);
    }

    @Override
    public String graphTypeId() {
        return GraphTypeRegistry.BEHAVIOR_TREE.id();
    }

    @Override
    public GraphKind runtimeKind() {
        return GraphKind.BEHAVIOR_TREE;
    }

    public String assetId() {
        return assetId;
    }

    @Override
    public int getNodeCount() {
        return nodeIds.length;
    }

    @Override
    @Nullable
    public String getNodeId(int nodeId) {
        return validNode(nodeId) ? nodeIds[nodeId] : null;
    }

    @Override
    public String getNodeType(int nodeId) {
        return validNode(nodeId) ? nodeTypes[nodeId] : "";
    }

    public NodeCapabilities getNodeCapabilities(int nodeId) {
        return validNode(nodeId) ? capabilities[nodeId] : NodeCapabilities.LEGACY_BLUEPRINT;
    }

    public int getRootNode() {
        return rootNode;
    }

    public int getParent(int nodeId) {
        return validNode(nodeId) ? parents[nodeId] : -1;
    }

    public int getChildCount(int nodeId) {
        return validNode(nodeId) ? children[nodeId].length : 0;
    }

    public int getChild(int nodeId, int childIndex) {
        if (!validNode(nodeId) || childIndex < 0 || childIndex >= children[nodeId].length) return -1;
        return children[nodeId][childIndex];
    }

    @Override
    public int getPortKey(String portName) {
        return portKeys.getOrDefault(portName, -1);
    }

    @Override
    @Nullable
    public DataConnectionSource findDataInput(int targetNodeId, String inputPortName) {
        return validNode(targetNodeId) ? dataInputs[targetNodeId].get(inputPortName) : null;
    }

    @Override
    @Nullable
    public Object getStaticInput(int nodeId, String portName) {
        if (!validNode(nodeId)) return null;
        Object value = staticInputs[nodeId].get(portName);
        return copiedStaticInputs[nodeId].contains(portName)
                ? GraphValueSnapshot.snapshot(value) : value;
    }

    @Override
    public boolean hasPort(int nodeId, String portName) {
        return validNode(nodeId) && ports[nodeId].contains(portName);
    }

    public RootSchedule rootSchedule() {
        return rootSchedule;
    }

    private boolean validNode(int nodeId) {
        return nodeId >= 0 && nodeId < nodeIds.length;
    }

    private static int[][] copyChildren(int[][] source) {
        int[][] result = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] != null ? source[i].clone() : new int[0];
        }
        return result;
    }

    private static <T> Map<String, T>[] copyMapArray(Map<String, T>[] source) {
        @SuppressWarnings("unchecked") Map<String, T>[] result = new Map[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] != null ? Map.copyOf(source[i]) : Map.of();
        }
        return result;
    }

    private static Set<String>[] copySetArray(Set<String>[] source) {
        @SuppressWarnings("unchecked") Set<String>[] result = new Set[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] != null ? Set.copyOf(source[i]) : Set.of();
        }
        return result;
    }

    private static Set<String>[] mutableInputKeys(Map<String, Object>[] inputs) {
        @SuppressWarnings("unchecked") Set<String>[] result = new Set[inputs.length];
        for (int index = 0; index < inputs.length; index++) {
            Set<String> keys = new java.util.HashSet<>();
            inputs[index].forEach((key, value) -> {
                if (GraphValueSnapshot.requiresReadCopy(value)) keys.add(key);
            });
            result[index] = Set.copyOf(keys);
        }
        return result;
    }

    public record RootSchedule(int recheckInterval, int scheduleOffset) {
        public static final int AUTO_OFFSET = -1;
        public static final RootSchedule DEFAULT = new RootSchedule(1, AUTO_OFFSET);

        public RootSchedule {
            if (recheckInterval < 1) {
                throw new IllegalArgumentException("Root recheck interval must be positive");
            }
            if (scheduleOffset < AUTO_OFFSET) {
                throw new IllegalArgumentException("Root schedule offset must be -1 or greater");
            }
            if (scheduleOffset >= 0) scheduleOffset = Math.floorMod(scheduleOffset, recheckInterval);
        }

        public int resolveOffset(String hostIdentity, String assetId) {
            if (scheduleOffset >= 0) return scheduleOffset;
            int hash = 31 * (hostIdentity != null ? hostIdentity.hashCode() : 0)
                    + (assetId != null ? assetId.hashCode() : 0);
            return Math.floorMod(hash, recheckInterval);
        }
    }

}
