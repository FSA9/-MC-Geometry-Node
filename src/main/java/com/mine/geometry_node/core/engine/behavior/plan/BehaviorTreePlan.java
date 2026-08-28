package com.mine.geometry_node.core.engine.behavior.plan;

import com.mine.geometry_node.core.node.document.behavior.BehaviorSubtreeParameter;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledDataIndex;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.compile.dependency.CompiledGraphDependencies;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.node.NodeCapabilities;
import com.mine.geometry_node.core.node.port.PortType;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final DependencyManifest dependencyManifest;
    private final SubtreeSignature subtreeSignature;
    private final RootSchedule rootSchedule;
    private final String[] nodeAssetIds;
    private final int[] blackboardFrameIds;
    private final List<BlackboardFrameInfo> blackboardFrameInfos;
    private final Map<Integer, SubtreeCallBoundary> subtreeCalls;
    private final boolean linked;

    private BehaviorTreePlan(String assetId, String[] nodeIds, Map<String, Integer> nodeIndexes,
                             String[] nodeTypes, NodeCapabilities[] capabilities, int rootNode,
                             int[] parents, int[][] children, Map<String, Object>[] staticInputs,
                             Map<String, DataConnectionSource>[] dataInputs, Set<String>[] ports,
                             Map<String, Integer> portKeys, List<String> portNames,
                             Map<String, List<Integer>> nodesByType,
                             DependencyManifest dependencyManifest, SubtreeSignature subtreeSignature,
                             RootSchedule rootSchedule, @Nullable LinkedMetadata linkedMetadata) {
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
        this.dependencyManifest = dependencyManifest;
        this.subtreeSignature = subtreeSignature != null ? subtreeSignature : SubtreeSignature.EMPTY;
        this.rootSchedule = rootSchedule != null ? rootSchedule : RootSchedule.DEFAULT;
        if (linkedMetadata == null) {
            this.nodeAssetIds = new String[nodeIds.length];
            Arrays.fill(this.nodeAssetIds, this.assetId);
            this.blackboardFrameIds = new int[nodeIds.length];
            this.blackboardFrameInfos = List.of(new BlackboardFrameInfo(0, this.assetId, ""));
            this.subtreeCalls = Map.of();
            this.linked = false;
        } else {
            if (linkedMetadata.nodeAssetIds().length != nodeIds.length
                    || linkedMetadata.blackboardFrameIds().length != nodeIds.length) {
                throw new IllegalArgumentException("Linked behavior metadata must match node count");
            }
            this.nodeAssetIds = linkedMetadata.nodeAssetIds().clone();
            this.blackboardFrameIds = linkedMetadata.blackboardFrameIds().clone();
            this.blackboardFrameInfos = List.copyOf(linkedMetadata.blackboardFrameInfos());
            if (blackboardFrameInfos.isEmpty()) {
                throw new IllegalArgumentException("Linked behavior frame metadata is incomplete");
            }
            for (int frameId : blackboardFrameIds) {
                if (frameId < 0 || frameId >= blackboardFrameInfos.size()) {
                    throw new IllegalArgumentException("Linked behavior node references an invalid frame");
                }
            }
            this.subtreeCalls = Map.copyOf(linkedMetadata.subtreeCalls());
            this.linked = true;
        }
    }

    public static BehaviorTreePlan createCompiled(
            String assetId, String[] nodeIds, Map<String, Integer> nodeIndexes,
            String[] nodeTypes, NodeCapabilities[] capabilities, int rootNode,
            int[] parents, int[][] children, Map<String, Object>[] staticInputs,
            Map<String, DataConnectionSource>[] dataInputs, Set<String>[] ports,
            Map<String, Integer> portKeys, List<String> portNames,
            Map<String, List<Integer>> nodesByType,
            DependencyManifest dependencyManifest, SubtreeSignature subtreeSignature,
            RootSchedule rootSchedule) {
        return new BehaviorTreePlan(assetId, nodeIds, nodeIndexes, nodeTypes, capabilities,
                rootNode, parents, children, staticInputs, dataInputs, ports,
                portKeys, portNames, nodesByType, dependencyManifest,
                subtreeSignature, rootSchedule, null);
    }

    public static BehaviorTreePlan createLinked(
            String assetId, String[] nodeIds, Map<String, Integer> nodeIndexes,
            String[] nodeTypes, NodeCapabilities[] capabilities, int rootNode,
            int[] parents, int[][] children, Map<String, Object>[] staticInputs,
            Map<String, DataConnectionSource>[] dataInputs, Set<String>[] ports,
            Map<String, Integer> portKeys, List<String> portNames,
            Map<String, List<Integer>> nodesByType,
            DependencyManifest dependencyManifest, SubtreeSignature subtreeSignature,
            RootSchedule rootSchedule, LinkedMetadata linkedMetadata) {
        return new BehaviorTreePlan(assetId, nodeIds, nodeIndexes, nodeTypes, capabilities,
                rootNode, parents, children, staticInputs, dataInputs, ports, portKeys,
                portNames, nodesByType, dependencyManifest,
                subtreeSignature, rootSchedule, linkedMetadata);
    }

    public static BehaviorTreePlan createCompiled(
            String assetId, String[] nodeIds, Map<String, Integer> nodeIndexes,
            String[] nodeTypes, NodeCapabilities[] capabilities, int rootNode,
            int[] parents, int[][] children, Map<String, Object>[] staticInputs,
            Map<String, DataConnectionSource>[] dataInputs, Set<String>[] ports,
            Map<String, Integer> portKeys, List<String> portNames,
            Map<String, List<Integer>> nodesByType,
            DependencyManifest dependencyManifest) {
        return createCompiled(assetId, nodeIds, nodeIndexes, nodeTypes, capabilities,
                rootNode, parents, children, staticInputs, dataInputs, ports, portKeys,
                portNames, nodesByType, dependencyManifest,
                SubtreeSignature.EMPTY, RootSchedule.DEFAULT);
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

    public String getNodeAssetId(int nodeId) {
        return validNode(nodeId) ? nodeAssetIds[nodeId] : "";
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
        return validNode(nodeId)
                ? GraphValueSnapshot.snapshot(staticInputs[nodeId].get(portName))
                : null;
    }

    @Override
    public boolean hasPort(int nodeId, String portName) {
        return validNode(nodeId) && ports[nodeId].contains(portName);
    }

    public Set<String> getPorts(int nodeId) {
        return validNode(nodeId) ? ports[nodeId] : Set.of();
    }

    public DependencyManifest dependencyManifest() {
        return dependencyManifest;
    }

    public SubtreeSignature subtreeSignature() {
        return subtreeSignature;
    }

    public RootSchedule rootSchedule() {
        return rootSchedule;
    }

    public boolean isLinked() { return linked; }

    public int blackboardFrame(int nodeId) {
        return validNode(nodeId) ? blackboardFrameIds[nodeId] : 0;
    }

    public List<BlackboardFrameInfo> blackboardFrameInfos() {
        return blackboardFrameInfos;
    }

    @Nullable
    public SubtreeCallBoundary subtreeCall(int nodeId) {
        return subtreeCalls.get(nodeId);
    }

    @Override
    public Set<String> graphDependencies() {
        return dependencyManifest.assetIds();
    }

    @Override
    public boolean requiresAvailableDependencies() {
        return false;
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

    public record SubtreeDependency(String callNodeId, String assetId,
                                    Map<String, String> inputMapping,
                                    Map<String, String> outputMapping) {
        public SubtreeDependency {
            callNodeId = callNodeId != null ? callNodeId : "";
            inputMapping = Map.copyOf(inputMapping);
            outputMapping = Map.copyOf(outputMapping);
        }
    }

    public record SubtreeParameter(String name, BehaviorSubtreeParameter.Direction direction,
                                   PortType type, String blackboardKey) {
    }

    public record SubtreeParameterTransfer(String targetKey, String sourceKey, PortType type) {
        public SubtreeParameterTransfer {
            targetKey = targetKey != null ? targetKey : "";
            sourceKey = sourceKey != null ? sourceKey : "";
            type = Objects.requireNonNull(type, "type");
        }
    }

    public record SubtreeCallBoundary(int childRootNode, int parentFrame, int childFrame,
                                      List<SubtreeParameterTransfer> inputTransfers,
                                      List<SubtreeParameterTransfer> outputTransfers) {
        public SubtreeCallBoundary {
            inputTransfers = List.copyOf(inputTransfers);
            outputTransfers = List.copyOf(outputTransfers);
        }
    }

    public record BlackboardFrameInfo(int frameId, String assetId, String callNodePath) {
        public BlackboardFrameInfo {
            assetId = assetId != null ? assetId : "";
            callNodePath = callNodePath != null ? callNodePath : "";
        }
    }

    public record LinkedMetadata(String[] nodeAssetIds, int[] blackboardFrameIds,
                                 List<BlackboardFrameInfo> blackboardFrameInfos,
                                 Map<Integer, SubtreeCallBoundary> subtreeCalls) {
        public LinkedMetadata {
            nodeAssetIds = nodeAssetIds.clone();
            blackboardFrameIds = blackboardFrameIds.clone();
            blackboardFrameInfos = List.copyOf(blackboardFrameInfos);
            subtreeCalls = Map.copyOf(subtreeCalls);
        }

        @Override public String[] nodeAssetIds() { return nodeAssetIds.clone(); }
        @Override public int[] blackboardFrameIds() { return blackboardFrameIds.clone(); }
    }

    public static final class SubtreeSignature {
        public static final SubtreeSignature EMPTY = new SubtreeSignature(List.of());

        private final List<SubtreeParameter> parameters;
        private final Map<String, SubtreeParameter> byName;

        public SubtreeSignature(List<SubtreeParameter> parameters) {
            this.parameters = List.copyOf(parameters);
            Map<String, SubtreeParameter> index = new LinkedHashMap<>();
            parameters.forEach(parameter -> index.put(parameter.name(), parameter));
            byName = Map.copyOf(index);
        }

        public List<SubtreeParameter> parameters() { return parameters; }

        @Nullable
        public SubtreeParameter find(String name) { return byName.get(name); }
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
