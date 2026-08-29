package com.mine.geometry_node.core.engine.behavior.plan;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledDataIndex;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledNodeIndex;
import com.mine.geometry_node.core.node.NodeCapabilities;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/** Immutable, compact behavior-tree artifact shared by all future runtime instances. */
public final class BehaviorTreePlan implements CompiledGraph, CompiledDataIndex {
    private final String assetId;
    private final CompiledNodeIndex nodes;
    private final NodeCapabilities[] capabilities;
    private final int rootNode;
    private final int[] parents;
    private final int[][] children;
    private final RootSchedule rootSchedule;

    private BehaviorTreePlan(String assetId, String[] nodeIds,
                             String[] nodeTypes, NodeCapabilities[] capabilities, int rootNode,
                             int[] parents, int[][] children, Map<String, Object>[] staticInputs,
                             Map<Integer, DataConnectionSource>[] dataInputs, Set<String>[] ports,
                             Map<String, Integer> portKeys,
                             RootSchedule rootSchedule) {
        this.assetId = assetId != null ? assetId : "";
        this.nodes = new CompiledNodeIndex(nodeIds, nodeTypes, staticInputs,
                dataInputs, ports, portKeys);
        this.capabilities = capabilities.clone();
        this.rootNode = rootNode;
        this.parents = parents.clone();
        this.children = copyChildren(children);
        this.rootSchedule = rootSchedule != null ? rootSchedule : RootSchedule.DEFAULT;
    }

    public static BehaviorTreePlan createCompiled(
            String assetId, String[] nodeIds,
            String[] nodeTypes, NodeCapabilities[] capabilities, int rootNode,
            int[] parents, int[][] children, Map<String, Object>[] staticInputs,
            Map<Integer, DataConnectionSource>[] dataInputs, Set<String>[] ports,
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
        return nodes.getNodeCount();
    }

    @Override
    @Nullable
    public String getNodeId(int nodeId) {
        return nodes.getNodeId(nodeId);
    }

    @Override
    public String getNodeType(int nodeId) {
        return nodes.getNodeType(nodeId);
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
        return nodes.getPortKey(portName);
    }

    @Override
    @Nullable
    public DataConnectionSource findDataInput(int targetNodeId, String inputPortName) {
        return nodes.findDataInput(targetNodeId, inputPortName);
    }

    @Override
    @Nullable
    public Object getStaticInput(int nodeId, String portName) {
        return nodes.getStaticInput(nodeId, portName);
    }

    @Override
    public boolean hasPort(int nodeId, String portName) {
        return nodes.hasPort(nodeId, portName);
    }

    public RootSchedule rootSchedule() {
        return rootSchedule;
    }

    private boolean validNode(int nodeId) {
        return nodeId >= 0 && nodeId < nodes.getNodeCount();
    }

    private static int[][] copyChildren(int[][] source) {
        int[][] result = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] != null ? source[i].clone() : new int[0];
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
