package com.mine.geometry_node.client.ai.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Contract-level validation only; graph-aware validation belongs to the P5 planner. */
public final class GraphPatchContractValidator {
    private GraphPatchContractValidator() {}

    public record Diagnostic(int operationIndex, String code, String message) {}

    public static List<Diagnostic> validate(GraphPatch patch) {
        Set<String> nodeAliases = new HashSet<>();
        Set<String> frameAliases = new HashSet<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (int index = 0; index < patch.operations().size(); index++) {
            GraphPatch.Operation operation = patch.operations().get(index);
            validateReferences(operation, nodeAliases, frameAliases, index, diagnostics);
            if (operation instanceof GraphPatch.AddNode value && !nodeAliases.add(value.alias())) {
                diagnostics.add(new Diagnostic(index, "patch.duplicate_node_alias", "node alias is already declared: " + value.alias()));
            }
            if (operation instanceof GraphPatch.AddFrame value && !frameAliases.add(value.alias())) {
                diagnostics.add(new Diagnostic(index, "patch.duplicate_frame_alias", "frame alias is already declared: " + value.alias()));
            }
        }
        return List.copyOf(diagnostics);
    }

    private static void validateReferences(GraphPatch.Operation operation, Set<String> nodeAliases,
                                           Set<String> frameAliases, int index,
                                           List<Diagnostic> diagnostics) {
        switch (operation) {
            case GraphPatch.RemoveNode value -> validateNodeRef(value.node(), nodeAliases, index, diagnostics);
            case GraphPatch.MoveNode value -> validateNodeRef(value.node(), nodeAliases, index, diagnostics);
            case GraphPatch.Connect value -> {
                validateNodeRef(value.from().node(), nodeAliases, index, diagnostics);
                validateNodeRef(value.to().node(), nodeAliases, index, diagnostics);
            }
            case GraphPatch.Disconnect value -> {
                validateNodeRef(value.from().node(), nodeAliases, index, diagnostics);
                validateNodeRef(value.to().node(), nodeAliases, index, diagnostics);
            }
            case GraphPatch.SetPortValue value -> validateNodeRef(value.port().node(), nodeAliases, index, diagnostics);
            case GraphPatch.SetSelectValue value -> validateNodeRef(value.port().node(), nodeAliases, index, diagnostics);
            case GraphPatch.SetNodeProperty value -> validateNodeRef(value.node(), nodeAliases, index, diagnostics);
            case GraphPatch.SetFrameProperty value -> validateFrameRef(value.frame(), frameAliases, index, diagnostics);
            case GraphPatch.AddDynamicBranch value -> validateNodeRef(value.node(), nodeAliases, index, diagnostics);
            case GraphPatch.RemoveDynamicBranch value -> validateNodeRef(value.node(), nodeAliases, index, diagnostics);
            case GraphPatch.AddGroupVirtualPort value -> validateNodeRef(value.node(), nodeAliases, index, diagnostics);
            case GraphPatch.RemoveGroupVirtualPort value -> validateNodeRef(value.node(), nodeAliases, index, diagnostics);
            case GraphPatch.RenamePort value -> validateNodeRef(value.node(), nodeAliases, index, diagnostics);
            default -> { }
        }
    }

    private static void validateNodeRef(GraphPatch.NodeRef ref, Set<String> aliases, int index,
                                        List<Diagnostic> diagnostics) {
        if (ref.alias() != null && !aliases.contains(ref.alias())) {
            diagnostics.add(new Diagnostic(index, "patch.unknown_alias",
                    "alias must be declared by an earlier operation: " + ref.alias()));
        }
    }

    private static void validateFrameRef(GraphPatch.FrameRef ref, Set<String> aliases, int index,
                                         List<Diagnostic> diagnostics) {
        if (ref.alias() != null && !aliases.contains(ref.alias())) {
            diagnostics.add(new Diagnostic(index, "patch.unknown_frame_alias",
                    "frame alias must be declared by an earlier operation: " + ref.alias()));
        }
    }
}
