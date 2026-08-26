package com.mine.geometry_node.core.engine.behavior.document;

import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.node.NodeCapabilities;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.document.BehaviorTreeStructure;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.Objects;

/** Validates editable hierarchy without mutating or normalizing the document. */
public final class BehaviorTreeStructureValidator {
    private final Predicate<String> knownNodeType;
    private final Function<String, NodeCapabilities> capabilities;

    public BehaviorTreeStructureValidator(Predicate<String> knownNodeType,
                                          Function<String, NodeCapabilities> capabilities) {
        this.knownNodeType = Objects.requireNonNull(knownNodeType, "knownNodeType");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
    }

    public static BehaviorTreeStructureValidator registeredNodes() {
        NodeRegistry registry = NodeRegistry.INSTANCE;
        return new BehaviorTreeStructureValidator(registry::has, registry::getCapabilities);
    }

    public BehaviorTreeValidationResult validate(NodeGraph graph) {
        List<BehaviorTreeDiagnostic> diagnostics = new ArrayList<>();
        if (graph == null) {
            diagnostics.add(problem("DOCUMENT_MISSING", "Behavior tree document is missing", "", ""));
            return new BehaviorTreeValidationResult(diagnostics);
        }

        String graphTypeId = graph.getGraphTypeId();
        if (GraphTypeRegistry.INSTANCE.get(graphTypeId) == null) {
            diagnostics.add(problem("GRAPH_TYPE_UNKNOWN", "Unknown graph type: " + graphTypeId, "", ""));
        } else if (!GraphTypeRegistry.BEHAVIOR_TREE.id().equals(graphTypeId)) {
            diagnostics.add(problem("GRAPH_TYPE_INVALID", "Expected a behavior_tree asset", "", ""));
        }

        Map<String, NodeData> nodes = graph.nodes != null ? graph.nodes : Map.of();
        List<String> roots = new ArrayList<>();
        Set<String> structuralNodeIds = new LinkedHashSet<>();
        List<String> nodeIds = nodes.keySet().stream().sorted().toList();
        for (String nodeId : nodeIds) {
            NodeData node = nodes.get(nodeId);
            if (node == null || node.type == null || !knownNodeType.test(node.type)) {
                diagnostics.add(problem("NODE_TYPE_MISSING", "Node type is missing or unavailable", nodeId, ""));
                continue;
            }
            if (!capabilities.apply(node.type).supports(GraphTypeRegistry.BEHAVIOR_TREE.id())) {
                diagnostics.add(problem("NODE_GRAPH_TYPE_UNSUPPORTED",
                        "Node type is not available in behavior trees: " + node.type, nodeId, ""));
            }
            if (capabilities.apply(node.type).context() == NodeCapabilities.Context.BEHAVIOR_EXECUTION) {
                structuralNodeIds.add(nodeId);
            }
            if (BehaviorNodeTypes.ROOT.equals(node.type)) roots.add(nodeId);
        }

        if (roots.size() != 1) {
            diagnostics.add(problem("ROOT_COUNT_INVALID",
                    "Behavior tree must contain exactly one Root node; found " + roots.size(), "", ""));
        }

        BehaviorTreeStructure structure = graph.behaviorTree;
        Map<String, List<String>> relationships = structure != null
                ? structure.relationships() : Map.of();
        Map<String, String> parentByChild = new LinkedHashMap<>();
        Map<String, List<String>> knownEdges = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : relationships.entrySet()) {
            String parentId = entry.getKey();
            if (!nodes.containsKey(parentId)) {
                diagnostics.add(problem("PARENT_NODE_MISSING", "Behavior parent node does not exist", parentId, ""));
                continue;
            }
            if (!structuralNodeIds.contains(parentId)) {
                diagnostics.add(problem("STRUCTURE_PARENT_INVALID",
                        "Only behavior nodes can own behavior children", parentId, ""));
                continue;
            }
            Set<String> siblings = new HashSet<>();
            for (String childId : entry.getValue()) {
                if (childId == null || !nodes.containsKey(childId)) {
                    diagnostics.add(problem("CHILD_NODE_MISSING", "Behavior child node does not exist", parentId, childId));
                    continue;
                }
                if (!structuralNodeIds.contains(childId)) {
                    diagnostics.add(problem("STRUCTURE_CHILD_INVALID",
                            "Data nodes cannot appear in the behavior hierarchy", parentId, childId));
                    continue;
                }
                if (!siblings.add(childId)) {
                    diagnostics.add(problem("CHILD_DUPLICATED", "Child occurs more than once under the same parent", parentId, childId));
                    continue;
                }
                String previousParent = parentByChild.putIfAbsent(childId, parentId);
                if (previousParent != null && !previousParent.equals(parentId)) {
                    diagnostics.add(problem("MULTIPLE_PARENTS", "Behavior node has more than one parent", childId, parentId));
                }
                knownEdges.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(childId);
            }
        }

        for (String nodeId : structuralNodeIds) {
            NodeData node = nodes.get(nodeId);
            if (node == null || node.type == null || !knownNodeType.test(node.type)) continue;
            NodeCapabilities nodeCapabilities = capabilities.apply(node.type);
            int childCount = knownEdges.getOrDefault(nodeId, List.of()).size();
            if (!nodeCapabilities.children().accepts(childCount)) {
                diagnostics.add(problem("CHILD_COUNT_INVALID",
                        childCountMessage(nodeCapabilities.children(), childCount), nodeId, ""));
            }
        }

        for (String rootId : roots) {
            if (parentByChild.containsKey(rootId)) {
                diagnostics.add(problem("ROOT_HAS_PARENT", "Root node cannot have a parent", rootId, parentByChild.get(rootId)));
            }
        }

        detectCycles(structuralNodeIds, knownEdges, diagnostics);
        if (roots.size() == 1) {
            Set<String> reachable = new LinkedHashSet<>();
            collectReachable(roots.getFirst(), knownEdges, reachable);
            for (String nodeId : structuralNodeIds) {
                if (!reachable.contains(nodeId)) {
                    diagnostics.add(problem("NODE_UNREACHABLE", "Node is not reachable from Root", nodeId, ""));
                }
            }
        }
        return new BehaviorTreeValidationResult(diagnostics);
    }

    private static void detectCycles(Set<String> nodeIds, Map<String, List<String>> edges,
                                     List<BehaviorTreeDiagnostic> diagnostics) {
        Set<String> visited = new HashSet<>();
        Set<String> active = new HashSet<>();
        Set<String> reported = new HashSet<>();
        for (String nodeId : nodeIds) {
            visit(nodeId, edges, visited, active, reported, diagnostics);
        }
    }

    private static void visit(String nodeId, Map<String, List<String>> edges, Set<String> visited,
                              Set<String> active, Set<String> reported,
                              List<BehaviorTreeDiagnostic> diagnostics) {
        if (visited.contains(nodeId)) return;
        if (!active.add(nodeId)) return;
        for (String childId : edges.getOrDefault(nodeId, List.of())) {
            if (active.contains(childId)) {
                String key = nodeId + '\0' + childId;
                if (reported.add(key)) {
                    diagnostics.add(problem("STRUCTURE_CYCLE", "Behavior hierarchy contains a cycle", nodeId, childId));
                }
            } else {
                visit(childId, edges, visited, active, reported, diagnostics);
            }
        }
        active.remove(nodeId);
        visited.add(nodeId);
    }

    private static void collectReachable(String nodeId, Map<String, List<String>> edges, Set<String> result) {
        if (!result.add(nodeId)) return;
        for (String childId : edges.getOrDefault(nodeId, List.of())) {
            collectReachable(childId, edges, result);
        }
    }

    private static BehaviorTreeDiagnostic problem(String code, String message,
                                                   String nodeId, String relatedNodeId) {
        return new BehaviorTreeDiagnostic(code, message, nodeId, relatedNodeId);
    }

    private static String childCountMessage(NodeCapabilities.ChildConstraint constraint, int actual) {
        String expected = constraint.maximum() == NodeCapabilities.ChildConstraint.UNBOUNDED
                ? "at least " + constraint.minimum()
                : constraint.minimum() == constraint.maximum()
                        ? Integer.toString(constraint.minimum())
                        : constraint.minimum() + " to " + constraint.maximum();
        return "Node requires " + expected + " behavior children; found " + actual;
    }
}
