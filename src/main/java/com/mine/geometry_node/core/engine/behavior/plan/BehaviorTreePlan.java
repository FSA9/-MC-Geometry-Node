package com.mine.geometry_node.core.engine.behavior.plan;

import com.mine.geometry_node.core.engine.behavior.contract.BlackboardScope;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.compile.CompiledDataIndex;
import com.mine.geometry_node.core.engine.graph.compile.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.compile.CompiledGraphDependencies;
import com.mine.geometry_node.core.node.NodeCapabilities;
import com.mine.geometry_node.core.node.port.PortType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, compact behavior-tree artifact shared by all future runtime instances. */
public final class BehaviorTreePlan implements CompiledGraph, CompiledDataIndex, CompiledGraphDependencies {
    private final String assetId;
    private final String[] nodeIds;
    private final Map<String, Integer> nodeIndexes;
    private final String[] nodeTypes;
    private final NodeCapabilities[] capabilities;
    private final int rootNode;
    private final int[] parents;
    private final int[][] children;
    private final Map<String, Object>[] staticInputs;
    private final Map<String, DataConnectionSource>[] dataInputs;
    private final Set<String>[] ports;
    private final Map<String, Integer> portKeys;
    private final List<String> portNames;
    private final Map<String, List<Integer>> nodesByType;
    private final BlackboardSchema blackboardSchema;
    private final DependencyManifest dependencyManifest;

    private BehaviorTreePlan(String assetId, String[] nodeIds, Map<String, Integer> nodeIndexes,
                             String[] nodeTypes, NodeCapabilities[] capabilities, int rootNode,
                             int[] parents, int[][] children, Map<String, Object>[] staticInputs,
                             Map<String, DataConnectionSource>[] dataInputs, Set<String>[] ports,
                             Map<String, Integer> portKeys, List<String> portNames,
                             Map<String, List<Integer>> nodesByType,
                             BlackboardSchema blackboardSchema,
                             DependencyManifest dependencyManifest) {
        this.assetId = assetId != null ? assetId : "";
        this.nodeIds = nodeIds.clone();
        this.nodeIndexes = Map.copyOf(nodeIndexes);
        this.nodeTypes = nodeTypes.clone();
        this.capabilities = capabilities.clone();
        this.rootNode = rootNode;
        this.parents = parents.clone();
        this.children = copyChildren(children);
        this.staticInputs = copyMapArray(staticInputs);
        this.dataInputs = copyMapArray(dataInputs);
        this.ports = copySetArray(ports);
        this.portKeys = Map.copyOf(portKeys);
        this.portNames = List.copyOf(portNames);
        this.nodesByType = copyLookup(nodesByType);
        this.blackboardSchema = blackboardSchema;
        this.dependencyManifest = dependencyManifest;
    }

    public static BehaviorTreePlan createCompiled(
            String assetId, String[] nodeIds, Map<String, Integer> nodeIndexes,
            String[] nodeTypes, NodeCapabilities[] capabilities, int rootNode,
            int[] parents, int[][] children, Map<String, Object>[] staticInputs,
            Map<String, DataConnectionSource>[] dataInputs, Set<String>[] ports,
            Map<String, Integer> portKeys, List<String> portNames,
            Map<String, List<Integer>> nodesByType, BlackboardSchema blackboardSchema,
            DependencyManifest dependencyManifest) {
        return new BehaviorTreePlan(assetId, nodeIds, nodeIndexes, nodeTypes, capabilities,
                rootNode, parents, children, staticInputs, dataInputs, ports,
                portKeys, portNames, nodesByType, blackboardSchema, dependencyManifest);
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

    public int getNodeIndex(String nodeId) {
        return nodeIndexes.getOrDefault(nodeId, -1);
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

    public List<Integer> getNodesByType(String typeId) {
        return nodesByType.getOrDefault(typeId, List.of());
    }

    @Override
    public int getPortKey(String portName) {
        return portKeys.getOrDefault(portName, -1);
    }

    @Nullable
    public String getPortName(int portKey) {
        return portKey >= 0 && portKey < portNames.size() ? portNames.get(portKey) : null;
    }

    @Override
    @Nullable
    public DataConnectionSource findDataInput(int targetNodeId, String inputPortName) {
        return validNode(targetNodeId) ? dataInputs[targetNodeId].get(inputPortName) : null;
    }

    @Override
    @Nullable
    public Object getStaticInput(int nodeId, String portName) {
        return validNode(nodeId) ? staticInputs[nodeId].get(portName) : null;
    }

    @Override
    public boolean hasPort(int nodeId, String portName) {
        return validNode(nodeId) && ports[nodeId].contains(portName);
    }

    public BlackboardSchema blackboardSchema() {
        return blackboardSchema;
    }

    public DependencyManifest dependencyManifest() {
        return dependencyManifest;
    }

    @Override
    public Set<String> graphDependencies() {
        return dependencyManifest.assetIds();
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

    private static Map<String, List<Integer>> copyLookup(Map<String, List<Integer>> source) {
        Map<String, List<Integer>> result = new HashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }

    public record BlackboardKey(String name, BlackboardScope scope, PortType type,
                                boolean writable, @Nullable Object defaultValue) {
    }

    public static final class BlackboardSchema {
        public static final BlackboardSchema EMPTY = new BlackboardSchema(List.of());

        private final List<BlackboardKey> declarations;
        private final Map<Key, BlackboardKey> byKey;

        public BlackboardSchema(List<BlackboardKey> declarations) {
            this.declarations = List.copyOf(declarations);
            Map<Key, BlackboardKey> index = new LinkedHashMap<>();
            for (BlackboardKey declaration : declarations) {
                index.put(new Key(declaration.scope(), declaration.name()), declaration);
            }
            this.byKey = Map.copyOf(index);
        }

        public List<BlackboardKey> declarations() {
            return declarations;
        }

        @Nullable
        public BlackboardKey find(BlackboardScope scope, String name) {
            return byKey.get(new Key(scope, name));
        }

        public record Key(BlackboardScope scope, String name) {
        }
    }

    public record SubtreeDependency(String assetId, Map<String, String> inputMapping,
                                    Map<String, String> outputMapping) {
        public SubtreeDependency {
            inputMapping = Map.copyOf(inputMapping);
            outputMapping = Map.copyOf(outputMapping);
        }
    }

    public static final class DependencyManifest {
        public static final DependencyManifest EMPTY = new DependencyManifest(List.of());

        private final List<SubtreeDependency> dependencies;
        private final Set<String> assetIds;

        public DependencyManifest(List<SubtreeDependency> dependencies) {
            this.dependencies = List.copyOf(dependencies);
            Set<String> ids = new LinkedHashSet<>();
            dependencies.forEach(dependency -> ids.add(dependency.assetId()));
            this.assetIds = Set.copyOf(ids);
        }

        public List<SubtreeDependency> dependencies() {
            return dependencies;
        }

        public Set<String> assetIds() {
            return assetIds;
        }

    }
}
