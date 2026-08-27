package com.mine.geometry_node.core.engine.behavior.compile;

import com.mine.geometry_node.core.engine.behavior.document.BehaviorSubtreeParameter;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorTreeDiagnostic;
import com.mine.geometry_node.core.engine.behavior.plan.BehaviorTreePlan;
import com.mine.geometry_node.core.engine.graph.compile.CompiledDataIndex;
import com.mine.geometry_node.core.node.NodeCapabilities;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/** Links subtree calls into one immutable execution plan without creating nested schedulers. */
public final class BehaviorTreeLinker {
    public static final int MAX_LINKED_NODES = 32_768;
    public static final int MAX_CALL_FRAMES = 1_024;

    private BehaviorTreeLinker() {
    }

    public static BehaviorTreePlan link(
            BehaviorTreePlan root, Function<String, @Nullable BehaviorTreePlan> resolver) {
        if (root.isLinked() || root.dependencyManifest().dependencies().isEmpty()) return root;
        List<BehaviorTreeDiagnostic> diagnostics = BehaviorSubtreePlanValidator.validate(root, resolver);
        if (!diagnostics.isEmpty()) throw new BehaviorCompilationException(diagnostics);
        Builder builder = new Builder(root, resolver);
        return builder.build();
    }

    private static final class Builder {
        private final BehaviorTreePlan root;
        private final Function<String, @Nullable BehaviorTreePlan> resolver;
        private final List<NodeEntry> nodes = new ArrayList<>();
        private final List<BehaviorTreePlan.BlackboardSchema> frameSchemas = new ArrayList<>();
        private final List<BehaviorTreePlan.BlackboardFrameInfo> frameInfos = new ArrayList<>();
        private final Map<Integer, BehaviorTreePlan.SubtreeCallBoundary> calls = new LinkedHashMap<>();
        private final List<BehaviorTreePlan.SubtreeDependency> dependencies = new ArrayList<>();

        private Builder(BehaviorTreePlan root,
                        Function<String, @Nullable BehaviorTreePlan> resolver) {
            this.root = root;
            this.resolver = resolver;
            frameSchemas.add(root.blackboardSchema());
            frameInfos.add(new BehaviorTreePlan.BlackboardFrameInfo(0, root.assetId(), ""));
        }

        private BehaviorTreePlan build() {
            int rootNode = appendPlan(root, "", 0, -1);
            int count = nodes.size();
            String[] ids = new String[count];
            String[] types = new String[count];
            String[] assetIds = new String[count];
            NodeCapabilities[] capabilities = new NodeCapabilities[count];
            int[] parents = new int[count];
            int[] frames = new int[count];
            int[][] children = new int[count][];
            @SuppressWarnings("unchecked") Map<String, Object>[] staticInputs = new Map[count];
            @SuppressWarnings("unchecked") Map<String, CompiledDataIndex.DataConnectionSource>[] dataInputs =
                    new Map[count];
            @SuppressWarnings("unchecked") Set<String>[] ports = new Set[count];
            Map<String, Integer> indexes = new LinkedHashMap<>();
            Map<String, List<Integer>> byType = new LinkedHashMap<>();
            Set<String> allPorts = new TreeSet<>();
            for (int index = 0; index < count; index++) {
                NodeEntry node = nodes.get(index);
                ids[index] = node.id;
                types[index] = node.type;
                assetIds[index] = node.assetId;
                capabilities[index] = node.capabilities;
                parents[index] = node.parent;
                frames[index] = node.frame;
                children[index] = node.children;
                staticInputs[index] = node.staticInputs;
                dataInputs[index] = node.dataInputs;
                ports[index] = node.ports;
                indexes.put(node.id, index);
                byType.computeIfAbsent(node.type, ignored -> new ArrayList<>()).add(index);
                allPorts.addAll(node.ports);
            }
            List<String> portNames = List.copyOf(allPorts);
            Map<String, Integer> portKeys = new LinkedHashMap<>();
            for (int index = 0; index < portNames.size(); index++) portKeys.put(portNames.get(index), index);
            return BehaviorTreePlan.createLinked(root.assetId(), ids, indexes, types, capabilities,
                    rootNode, parents, children, staticInputs, dataInputs, ports, portKeys,
                    portNames, byType, root.blackboardSchema(),
                    new BehaviorTreePlan.DependencyManifest(dependencies), root.subtreeSignature(),
                    root.rootSchedule(), new BehaviorTreePlan.LinkedMetadata(assetIds, frames,
                            frameSchemas, frameInfos, calls));
        }

        private int appendPlan(BehaviorTreePlan source, String namespace, int frame, int parentOverride) {
            if (nodes.size() > MAX_LINKED_NODES - source.getNodeCount()) {
                throw linkLimit(source, "SUBTREE_LINKED_NODE_LIMIT",
                        "Linked behavior tree exceeds " + MAX_LINKED_NODES + " nodes");
            }
            int offset = nodes.size();
            int[] indexes = new int[source.getNodeCount()];
            for (int local = 0; local < source.getNodeCount(); local++) indexes[local] = offset + local;
            for (int local = 0; local < source.getNodeCount(); local++) {
                String localId = source.getNodeId(local);
                String id = namespace.isEmpty() ? localId : namespace + localId;
                Map<String, Object> staticInputs = new LinkedHashMap<>();
                Map<String, CompiledDataIndex.DataConnectionSource> dataInputs = new LinkedHashMap<>();
                for (String port : source.getPorts(local)) {
                    Object value = source.getStaticInput(local, port);
                    if (value != null) staticInputs.put(port, value);
                    CompiledDataIndex.DataConnectionSource data = source.findDataInput(local, port);
                    if (data != null) dataInputs.put(port,
                            new CompiledDataIndex.DataConnectionSource(
                                    indexes[data.sourceNodeId()], data.sourcePortName()));
                }
                int parent = source.getParent(local) >= 0
                        ? indexes[source.getParent(local)]
                        : local == source.getRootNode() ? parentOverride : -1;
                int[] children = new int[source.getChildCount(local)];
                for (int child = 0; child < children.length; child++) {
                    children[child] = indexes[source.getChild(local, child)];
                }
                nodes.add(new NodeEntry(id, source.assetId(), source.getNodeType(local),
                        source.getNodeCapabilities(local), parent, children, frame,
                        Map.copyOf(staticInputs), Map.copyOf(dataInputs), source.getPorts(local)));
            }

            for (BehaviorTreePlan.SubtreeDependency call : source.dependencyManifest().dependencies()) {
                int localCall = source.getNodeIndex(call.callNodeId());
                if (localCall < 0) {
                    throw new IllegalStateException("Subtree manifest references a missing call node: "
                            + source.assetId() + "/" + call.callNodeId());
                }
                int callNode = indexes[localCall];
                BehaviorTreePlan target = resolver.apply(call.assetId());
                if (target == null) {
                    throw new IllegalStateException("Validated subtree asset became unavailable: "
                            + call.assetId());
                }
                if (frameSchemas.size() >= MAX_CALL_FRAMES) {
                    throw linkLimit(source, "SUBTREE_CALL_FRAME_LIMIT",
                            "Linked behavior tree exceeds " + MAX_CALL_FRAMES + " call frames");
                }
                int childFrame = frameSchemas.size();
                frameSchemas.add(target.blackboardSchema());
                frameInfos.add(new BehaviorTreePlan.BlackboardFrameInfo(
                        childFrame, target.assetId(), nodes.get(callNode).id));
                String childNamespace = nodes.get(callNode).id + "=>" + target.assetId() + "::";
                int childRoot = appendPlan(target, childNamespace, childFrame, callNode);
                nodes.get(callNode).children = new int[]{childRoot};

                Map<String, String> inputs = resolveParameterKeys(
                        target, call.inputMapping(), BehaviorSubtreeParameter.Direction.INPUT, false);
                Map<String, String> outputs = resolveParameterKeys(
                        target, call.outputMapping(), BehaviorSubtreeParameter.Direction.OUTPUT, true);
                calls.put(callNode, new BehaviorTreePlan.SubtreeCallBoundary(
                        childRoot, frame, childFrame, inputs, outputs));
                dependencies.add(new BehaviorTreePlan.SubtreeDependency(nodes.get(callNode).id,
                        call.assetId(), call.inputMapping(), call.outputMapping()));
            }
            return indexes[source.getRootNode()];
        }

        private static Map<String, String> resolveParameterKeys(
                BehaviorTreePlan target, Map<String, String> mapping,
                BehaviorSubtreeParameter.Direction direction, boolean reverse) {
            Map<String, String> result = new LinkedHashMap<>();
            mapping.forEach((first, second) -> {
                String parameterName = reverse ? second : first;
                String callerKey = reverse ? first : second;
                BehaviorTreePlan.SubtreeParameter parameter = target.subtreeSignature().find(parameterName);
                if (parameter == null || parameter.direction() != direction) {
                    throw new IllegalStateException("Validated subtree parameter became unavailable: "
                            + target.assetId() + "/" + parameterName);
                }
                if (reverse) result.put(callerKey, parameter.blackboardKey());
                else result.put(parameter.blackboardKey(), callerKey);
            });
            return Map.copyOf(result);
        }

        private static BehaviorCompilationException linkLimit(
                BehaviorTreePlan plan, String code, String message) {
            return new BehaviorCompilationException(List.of(new BehaviorTreeDiagnostic(
                    plan.assetId(), code, message, "", "", "")));
        }
    }

    private static final class NodeEntry {
        private final String id;
        private final String assetId;
        private final String type;
        private final NodeCapabilities capabilities;
        private final int parent;
        private int[] children;
        private final int frame;
        private final Map<String, Object> staticInputs;
        private final Map<String, CompiledDataIndex.DataConnectionSource> dataInputs;
        private final Set<String> ports;

        private NodeEntry(String id, String assetId, String type, NodeCapabilities capabilities,
                          int parent, int[] children, int frame, Map<String, Object> staticInputs,
                          Map<String, CompiledDataIndex.DataConnectionSource> dataInputs,
                          Set<String> ports) {
            this.id = id;
            this.assetId = assetId;
            this.type = type;
            this.capabilities = capabilities;
            this.parent = parent;
            this.children = children;
            this.frame = frame;
            this.staticInputs = staticInputs;
            this.dataInputs = dataInputs;
            this.ports = Set.copyOf(new LinkedHashSet<>(ports));
        }
    }
}
